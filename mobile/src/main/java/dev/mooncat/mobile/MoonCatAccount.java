package dev.mooncat.mobile;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Shares the Fire TV account protocol while keeping session tokens outside the WebView. */
final class MoonCatAccount implements AutoCloseable {
    private static final String OFFICIAL = "https://api.nuvio.tv";
    private static final String KEY_ALIAS = "mooncat-cloud-session";
    private static final String RPC = "/rest/v1/rpc/";
    private static final int MAX_RESPONSE = 4 * 1024 * 1024;
    private final Activity activity;
    private final Consumer<String> deliver;
    private final SharedPreferences stored;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final String clientId;
    private Configuration configuration;
    private Session session;
    private DeviceLogin login;
    private int profile;

    private record Configuration(String base, String key, boolean deviceLogin) {
        String link() { return OFFICIAL.equals(base) ? "https://nuvio.tv/link" : base + "/link"; }
    }

    private record Session(String access, String refresh, long expiry, String email) {}

    private record DeviceLogin(String code, String nonce, long expiresAt, int interval, int failures) {
        DeviceLogin retry(int failures, int interval) {
            return new DeviceLogin(code, nonce, expiresAt, interval, failures);
        }
    }

    MoonCatAccount(Activity activity, Consumer<String> deliver) {
        this.activity = activity;
        this.deliver = deliver;
        stored = activity.getSharedPreferences("mooncat_account_v1", Activity.MODE_PRIVATE);
        String id = stored.getString("client", null);
        clientId = id == null ? "mooncat-mobile-" + UUID.randomUUID() : id;
        if (id == null) stored.edit().putString("client", clientId).apply();
    }

    @JavascriptInterface
    public void action(String operation, String payload) {
        if (executor.isShutdown() || operation == null || payload == null || payload.length() > 65_536) return;
        try {
            executor.execute(() -> {
                try {
                    handle(operation, new JSONObject(payload));
                } catch (Exception error) {
                    if (error instanceof HttpFailure && ((HttpFailure) error).status == 429) {
                        notifyFailure("Account server is busy. Wait briefly and try again.");
                    } else {
                        notifyFailure(error instanceof AccountFailure ? error.getMessage() :
                            "MoonCat could not reach the account service. Try again.");
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { /* Closing the activity. */ }
    }

    boolean scanCode(String raw) {
        if (raw == null || raw.length() > 2_048) return false;
        try {
            URI url = URI.create(raw);
            if (!"https".equals(url.getScheme()) || url.getHost() == null ||
                url.getUserInfo() != null || url.getFragment() != null || !"/link".equals(url.getPath())) {
                return false;
            }
            normalizeCode(Uri.parse(raw).getQueryParameter("code"));
            action("previewTv", obj("value", raw).toString());
            return true;
        } catch (Exception invalidCode) {
            return false;
        }
    }

    private void handle(String operation, JSONObject payload) throws Exception {
        switch (operation) {
            case "restore":
                restore();
                break;
            case "signIn":
                startLogin(payload.optString("backend", OFFICIAL));
                break;
            case "cancel":
                login = null;
                send(obj("event", "guest"));
                break;
            case "signOut":
                signOut();
                break;
            case "refresh":
                if (session != null) snapshot();
                break;
            case "switchProfile":
                selectProfile(payload.optInt("profileId", 0));
                break;
            case "previewTv":
                previewTv(payload.optString("value"));
                break;
            case "approveTv":
                approveTv(payload.optString("value"));
                break;
            case "saveTitle":
                changeLibrary(payload);
                break;
            case "saveProgress":
                changeProgress(payload);
                break;
            default:
                break;
        }
    }

    private void startLogin(String backend) throws Exception {
        if (session != null) throw new AccountFailure("Sign out before connecting another account.");
        login = null;
        configuration = discover(backend);
        if (!configuration.deviceLogin) throw new AccountFailure("This server has device sign-in disabled.");
        String nonce = UUID.randomUUID().toString();
        JSONObject started = first(request(configuration.base + RPC + "start_device_login_session", configuration.key, null,
            obj("p_device_nonce", nonce, "p_redirect_base_url", configuration.link(),
                "p_device_name", android.os.Build.MODEL, "p_device_type", "mobile")));
        String code = started.getString("device_code");
        String userCode = normalizeCode(started.getString("user_code"));
        String url = started.getString("verification_uri_complete");
        if (!sameLink(configuration.link(), url) || code.trim().isEmpty()) {
            throw new AccountFailure("The account server returned an invalid sign-in link.");
        }
        int interval = Math.max(2, Math.min(15, started.optInt("poll_interval_seconds", 3)));
        login = new DeviceLogin(code, nonce, System.currentTimeMillis() + 10 * 60_000L, interval, 0);
        send(obj("event", "pending", "code", userCode));
        activity.runOnUiThread(() -> {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (android.content.ActivityNotFoundException error) {
                action("cancel", "{}");
            }
        });
        schedulePoll(login);
    }

    private void schedulePoll(DeviceLogin pending) {
        if (!executor.isShutdown()) executor.schedule(() -> poll(pending), pending.interval, TimeUnit.SECONDS);
    }

    private void poll(DeviceLogin pending) {
        if (pending != login) return;
        if (System.currentTimeMillis() >= pending.expiresAt) {
            login = null;
            notifyFailure("The sign-in code expired. Start again.");
            return;
        }
        try {
            JSONObject result = first(request(configuration.base + RPC + "poll_tv_login_session", configuration.key, null,
                obj("p_code", pending.code, "p_device_nonce", pending.nonce)));
            int interval = Math.max(pending.interval, Math.min(20, result.optInt("poll_interval_seconds", pending.interval)));
            switch (result.optString("status")) {
                case "pending":
                    login = pending.retry(0, interval);
                    schedulePoll(login);
                    return;
                case "approved":
                    finishLogin(pending);
                    return;
                default:
                    login = null;
                    notifyFailure("The sign-in code expired or was declined.");
            }
        } catch (Exception error) {
            if (login == null) {
                notifyFailure("The account service could not complete sign-in. Start again.");
                return;
            }
            if (pending.failures >= 2) {
                login = null;
                notifyFailure("The account service could not finish sign-in. Start again.");
                return;
            }
            login = pending.retry(pending.failures + 1, pending.interval + 2);
            schedulePoll(login);
        }
    }

    private void finishLogin(DeviceLogin pending) throws Exception {
        login = null;
        JSONObject result = (JSONObject) request(configuration.base + "/functions/v1/tv-logins-exchange", configuration.key,
            null, obj("code", pending.code, "device_nonce", pending.nonce));
        session = sessionFrom(result, "");
        profile = 0;
        saveSession();
        snapshot();
    }

    private void signOut() throws Exception {
        if (session != null && configuration != null) {
            try {
                request(configuration.base + "/auth/v1/logout?scope=local", configuration.key, session.access, new JSONObject());
            } catch (Exception ignored) { /* An offline device can still sign out locally. */ }
        }
        clearLocal();
        send(obj("event", "guest"));
    }

    private void clearLocal() {
        session = null;
        login = null;
        configuration = null;
        profile = 0;
        tvCode = null;
        stored.edit().remove("session").apply();
    }

    private void restore() throws Exception {
        if (session != null) {
            snapshot();
            return;
        }
        String encrypted = stored.getString("session", null);
        if (encrypted == null) {
            send(obj("event", "guest"));
            return;
        }
        JSONObject saved;
        try {
            saved = new JSONObject(open(encrypted));
        } catch (Exception invalid) {
            clearLocal();
            send(obj("event", "guest"));
            return;
        }
        Configuration next = discover(saved.getString("backend"));
        configuration = next;
        session = new Session(saved.getString("access"), saved.getString("refresh"),
            saved.getLong("expiry"), saved.optString("email"));
        profile = saved.optInt("profile", 0);
        snapshot();
    }

    private void saveSession() throws Exception {
        JSONObject values = obj("backend", configuration.base, "access", session.access, "refresh", session.refresh,
            "expiry", session.expiry, "email", session.email, "profile", profile);
        stored.edit().putString("session", seal(values.toString())).apply();
    }

    private static Session sessionFrom(JSONObject result, String fallbackEmail) throws Exception {
        JSONObject user = result.optJSONObject("user");
        String access = result.getString("access_token");
        String refresh = result.getString("refresh_token");
        if (access.trim().isEmpty() || refresh.trim().isEmpty()) throw new AccountFailure("The account service returned an invalid session.");
        return new Session(access, refresh, System.currentTimeMillis() + result.optLong("expires_in", 3600L) * 1000L,
            user == null ? fallbackEmail : user.optString("email", fallbackEmail));
    }

    private void refreshToken() throws Exception {
        try {
            JSONObject value = (JSONObject) request(configuration.base + "/auth/v1/token?grant_type=refresh_token",
                configuration.key, null, obj("refresh_token", session.refresh));
            session = sessionFrom(value, session.email);
            saveSession();
        } catch (HttpFailure failure) {
            if (failure.status == 400 || failure.status == 401 || failure.status == 403) {
                clearLocal();
                send(obj("event", "guest"));
                throw new AccountFailure("Your account session expired. Sign in again.");
            }
            throw failure;
        }
    }

    private Object rpc(String name, JSONObject data) throws Exception {
        if (session == null) throw new AccountFailure("Sign in before using your account.");
        if (session.expiry < System.currentTimeMillis() + 30_000L) refreshToken();
        try {
            return request(configuration.base + RPC + name, configuration.key, session.access, data);
        } catch (HttpFailure failure) {
            if (failure.status != 401) throw failure;
            refreshToken();
            return request(configuration.base + RPC + name, configuration.key, session.access, data);
        }
    }

    private JSONArray getProfiles() throws Exception {
        JSONArray rows = array(rpc("sync_pull_profiles", new JSONObject()));
        JSONArray available = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            int id = row.optInt("profile_index", 0);
            if (id > 0 && !row.optBoolean("pin_enabled")) {
                available.put(obj("id", id, "name", row.optString("name", "Profile")));
            }
        }
        if (rows.length() == 0) available.put(obj("id", 1, "name", "Main profile"));
        return available;
    }

    private boolean containsProfile(JSONArray profiles, int id) throws Exception {
        for (int i = 0; i < profiles.length(); i++) {
            if (profiles.getJSONObject(i).getInt("id") == id) return true;
        }
        return false;
    }

    private void selectProfile(int selected) throws Exception {
        JSONArray profiles = getProfiles();
        if (!containsProfile(profiles, selected)) throw new AccountFailure("This profile is unavailable on the phone.");
        profile = selected;
        saveSession();
        snapshot(profiles);
    }

    private void snapshot() throws Exception {
        snapshot(getProfiles());
    }

    private void snapshot(JSONArray profiles) throws Exception {
        if (!containsProfile(profiles, profile)) {
            profile = profiles.length() == 0 ? 0 : profiles.getJSONObject(0).getInt("id");
        }
        send(obj("event", "account", "email", session.email, "profiles", profiles, "profileId", profile));
        if (profile == 0) return;

        JSONArray titles = new JSONArray();
        int limit = 200;
        for (int offset = 0; offset < 4_000; offset += limit) {
            JSONArray page = array(rpc("sync_pull_library", obj("p_profile_id", profile, "p_limit", limit, "p_offset", offset)));
            for (int i = 0; i < page.length(); i++) titles.put(page.getJSONObject(i));
            if (page.length() < limit) break;
        }
        JSONArray progress = array(rpc("sync_pull_watch_progress", obj("p_profile_id", profile, "p_limit", 150)));
        send(obj("event", "snapshot", "profileId", profile, "library", titles, "progress", progress));
    }

    private int requireProfile(JSONObject payload) throws AccountFailure {
        if (profile <= 0 || profile != payload.optInt("profileId")) {
            throw new AccountFailure("Select an accessible account profile first.");
        }
        return profile;
    }

    private void changeLibrary(JSONObject payload) throws Exception {
        int id = requireProfile(payload);
        JSONObject item = payload.getJSONObject("item");
        String key = requireId(item.optString("id"));
        String type = requireType(item.optString("type"));
        JSONObject data = obj("p_profile_id", id, "p_origin_client_id", clientId);
        if (payload.optBoolean("saved")) {
            JSONObject row = obj("content_id", key, "content_type", type, "name", item.optString("name"),
                "poster_shape", "POSTER", "poster", item.optString("poster"), "background", item.optString("background"),
                "description", item.optString("description"), "genres", item.optJSONArray("genres") != null ?
                item.optJSONArray("genres") : new JSONArray(), "release_info", item.optString("year"),
                "added_at", System.currentTimeMillis());
            data.put("p_items", new JSONArray().put(row));
            rpc("sync_push_library_items", data);
        } else {
            data.put("p_keys", new JSONArray().put(obj("content_id", key, "content_type", type)));
            rpc("sync_delete_library_items", data);
        }
    }

    private void changeProgress(JSONObject payload) throws Exception {
        int id = requireProfile(payload);
        String titleId = requireId(payload.optString("contentId"));
        String type = requireType(payload.optString("type"));
        String videoId = payload.optString("videoId");
        int season = payload.optInt("season", -1);
        int episode = payload.optInt("episode", -1);
        boolean series = "series".equals(type);
        if (series && (season < 0 || episode < 1 || !videoId.equals(titleId + ":" + season + ":" + episode))) {
            throw new AccountFailure("This episode has no compatible sync identifier.");
        }
        if (!series && !videoId.equals(titleId)) throw new AccountFailure("This movie cannot be synced.");
        JSONObject data = obj("p_profile_id", id, "p_origin_client_id", clientId);
        long duration = payload.optLong("duration");
        long position = payload.optLong("position");
        if (duration <= 0 || position < 0) return;
        String key = series ? titleId + "_s" + season + "e" + episode : titleId;
        JSONObject row = obj("content_id", titleId, "content_type", type, "video_id", videoId, "progress_key", key,
            "last_watched", System.currentTimeMillis(), "duration", duration,
            "position", payload.optBoolean("complete") ? duration : Math.min(position, duration));
        if (series) {
            row.put("season", season);
            row.put("episode", episode);
        }
        data.put("p_entries", new JSONArray().put(row));
        rpc("sync_push_watch_progress", data);
    }

    private String tvCode;
    private long tvConfirmationDeadline;

    private void previewTv(String input) throws Exception {
        if (session == null) throw new AccountFailure("Sign in on the phone before approving a TV login.");
        String code = input;
        if (input.startsWith("https://")) {
            if (!sameLink(configuration.link(), input)) throw new AccountFailure("This code is for a different TV account server.");
            code = Uri.parse(input).getQueryParameter("code");
        }
        code = normalizeCode(code);
        JSONObject target = first(rpc("get_device_login_session", obj("p_user_code", code)));
        if (!target.optBoolean("success") || !"tv".equalsIgnoreCase(target.optString("device_type"))) {
            throw new AccountFailure("This TV code is invalid or has expired.");
        }
        tvCode = code;
        tvConfirmationDeadline = System.currentTimeMillis() + 90_000L;
        send(obj("event", "confirmTv", "code", code, "device", target.optString("device_name", "MoonCat TV")));
    }

    private void approveTv(String input) throws Exception {
        String code = normalizeCode(input);
        if (!code.equals(tvCode) || System.currentTimeMillis() >= tvConfirmationDeadline) {
            throw new AccountFailure("Check the TV code again before approving.");
        }
        tvCode = null;
        JSONObject result = first(rpc("approve_device_login_session", obj("p_user_code", code)));
        if (!result.optBoolean("success")) throw new AccountFailure("The TV code expired. Generate a fresh code on the TV.");
        send(obj("event", "tvApproved"));
    }

    private static String normalizeCode(String code) throws AccountFailure {
        if (code == null || code.length() > 32) throw new AccountFailure("Enter the six-character code on your TV.");
        String value = code.trim().replace("-", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
        if (!value.matches("[A-Z0-9]{6}")) throw new AccountFailure("Enter the six-character code on your TV.");
        return value;
    }

    private static boolean sameLink(String reference, String value) {
        try {
            URI a = URI.create(reference);
            URI b = URI.create(value);
            return "https".equals(b.getScheme()) && a.getHost().equalsIgnoreCase(b.getHost()) &&
                a.getPort() == b.getPort() && a.getPath().equals(b.getPath()) && b.getUserInfo() == null &&
                b.getFragment() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Configuration discover(String input) throws Exception {
        String base = origin(input.trim().isEmpty() ? OFFICIAL : input.trim());
        JSONObject info = (JSONObject) request(base + "/.well-known/nuvio", null, null, null);
        String advertised = origin(info.getString("backend_url"));
        String key = info.getString("publishable_key");
        if (!advertised.equals(base) || info.optInt("version") != 1 || !"nuvio".equals(info.optString("service")) ||
            !publicClientKey(key)) {
            throw new AccountFailure("This address is not a compatible Nuvio account server.");
        }
        return new Configuration(base, key, info.getJSONObject("capabilities").optBoolean("tv_login"));
    }

    private static boolean publicClientKey(String key) {
        if (key.matches("sb_publishable_[A-Za-z0-9_-]{16,}")) return true;
        try {
            String[] segments = key.split("\\.");
            if (segments.length != 3) return false;
            byte[] payload = Base64.decode(segments[1], Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            return "anon".equals(new JSONObject(new String(payload, StandardCharsets.UTF_8)).optString("role"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String origin(String value) throws AccountFailure {
        if (value == null || value.length() > 2048) throw new AccountFailure("Enter an HTTPS account server address.");
        try {
            URI url = URI.create(value);
            String host = url.getHost();
            if (!"https".equals(url.getScheme()) || host == null || url.getRawUserInfo() != null ||
                url.getRawQuery() != null || url.getRawFragment() != null ||
                !(url.getPath().isEmpty() || "/".equals(url.getPath())) ||
                host.matches("[0-9.]+") || host.contains(":") || !host.contains(".") ||
                host.toLowerCase(java.util.Locale.ROOT).endsWith(".local")) throw new IllegalArgumentException();
            return "https://" + host.toLowerCase(java.util.Locale.ROOT) +
                (url.getPort() <= 0 || url.getPort() == 443 ? "" : ":" + url.getPort());
        } catch (IllegalArgumentException error) {
            throw new AccountFailure("Enter an HTTPS account server address.");
        }
    }

    private static Object request(String target, String apiKey, String bearer, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(target).toURL().openConnection();
        try {
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(9000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MoonCat/3 Android");
            if (apiKey != null) connection.setRequestProperty("apikey", apiKey);
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (var output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new HttpFailure(status);
            if (status == 204) return JSONObject.NULL;
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_RESPONSE) throw new IOException("Response limit exceeded");
                    output.write(buffer, 0, count);
                }
                if (output.size() == 0) return JSONObject.NULL;
                return new JSONTokener(output.toString(StandardCharsets.UTF_8.name())).nextValue();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String requireId(String id) throws AccountFailure {
        if (!id.matches("tt[0-9]{1,20}")) throw new AccountFailure("This title has no compatible sync identifier.");
        return id;
    }

    private static String requireType(String type) throws AccountFailure {
        if (!"movie".equals(type) && !"series".equals(type)) throw new AccountFailure("This title type cannot be synced.");
        return type;
    }

    private static JSONArray array(Object value) throws IOException {
        if (value instanceof JSONArray) return (JSONArray) value;
        throw new IOException("Unexpected account response");
    }

    private static JSONObject first(Object value) throws Exception {
        return array(value).getJSONObject(0);
    }

    private static JSONObject obj(Object... fields) throws org.json.JSONException {
        JSONObject json = new JSONObject();
        for (int i = 0; i < fields.length; i += 2) json.put((String) fields[i], fields[i + 1]);
        return json;
    }

    private void send(JSONObject event) {
        deliver.accept(event.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029"));
    }

    private void notifyFailure(String message) {
        try {
            send(obj("event", "error", "message", message));
        } catch (org.json.JSONException ignored) { /* Fixed field names and a plain string. */ }
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    private String seal(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] bytes = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private String open(String value) throws Exception {
        int split = value.indexOf(':');
        byte[] iv = Base64.decode(value.substring(0, split), Base64.NO_WRAP);
        byte[] bytes = Base64.decode(value.substring(split + 1), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(bytes), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static final class AccountFailure extends IOException {
        AccountFailure(String message) { super(message); }
    }

    private static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status) { this.status = status; }
    }
}
