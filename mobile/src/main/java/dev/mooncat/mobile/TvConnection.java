package dev.mooncat.mobile;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

final class TvConnection {
    private static final String PAIRING = "mooncat_tv_pairing";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Context context;

    TvConnection(Context context) {
        this.context = context.getApplicationContext();
    }

    void pair(String code) throws Exception {
        Pairing pairing = parse(code);
        if (pairing == null) throw new IOException("Scan the code shown in MoonCat on your TV.");
        send(pairing, "/pair", new JSONObject());
        preferences().edit().putString("code", code).apply();
    }

    boolean paired() {
        return saved() != null;
    }

    void forget() {
        preferences().edit().remove("code").apply();
    }

    void send(JSONObject request) throws Exception {
        Pairing pairing = saved();
        if (pairing == null) throw new IOException("Pair a MoonCat TV first.");
        send(pairing, "/play", request);
    }

    private void send(Pairing pairing, String route, JSONObject request) throws Exception {
        byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
        if (body.length > 2048) throw new IOException("This title could not be sent to the TV.");
        String time = Long.toString(System.currentTimeMillis() / 1000);
        byte[] randomBytes = new byte[16];
        RANDOM.nextBytes(randomBytes);
        String nonce = hex(randomBytes);
        String signature = signature(pairing.key, route, time, nonce, body);
        try (Socket connection = new Socket(java.net.Proxy.NO_PROXY)) {
            connection.connect(new InetSocketAddress(pairing.host, pairing.port), 3_000);
            connection.setSoTimeout(4_000);
            String headers = "POST " + route + " HTTP/1.1\r\n"
                + "Host: " + pairing.host + ":" + pairing.port + "\r\n"
                + "Connection: close\r\nContent-Type: application/json\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "X-MoonCat-Time: " + time + "\r\nX-MoonCat-Nonce: " + nonce + "\r\n"
                + "X-MoonCat-Signature: " + signature + "\r\n\r\n";
            OutputStream requestStream = connection.getOutputStream();
            requestStream.write(headers.getBytes(StandardCharsets.US_ASCII));
            requestStream.write(body);
            requestStream.flush();
            String responseLine = readStatus(connection.getInputStream());
            if (!responseLine.matches("HTTP/1\\.[01] [0-9]{3} .{0,160}")) throw new IOException();
            int response = Integer.parseInt(responseLine.substring(9, 12));
            if (response == 401 || response == 403) throw new IOException("The TV pairing expired. Pair the TV again.");
            if (response < 200 || response >= 300) throw new IOException("Open MoonCat on your TV and try again.");
        } catch (IOException error) {
            Log.d("MoonCat", "TV handoff failed: " + error.getClass().getSimpleName());
            if (error.getMessage() != null && (error.getMessage().startsWith("The TV") || error.getMessage().startsWith("Open MoonCat"))) throw error;
            throw new IOException("Keep MoonCat open on the TV and connect both devices to the same Wi-Fi.");
        }
    }

    private String readStatus(InputStream stream) throws IOException {
        ByteArrayOutputStream status = new ByteArrayOutputStream(64);
        for (int count = 0; count < 180; count++) {
            int value = stream.read();
            if (value == '\r') continue;
            if (value == '\n') return status.toString(StandardCharsets.US_ASCII.name());
            if (value < 0) break;
            status.write(value);
        }
        throw new IOException();
    }

    private android.content.SharedPreferences preferences() {
        return context.getSharedPreferences(PAIRING, Context.MODE_PRIVATE);
    }

    private Pairing saved() {
        return parse(preferences().getString("code", null));
    }

    private static Pairing parse(String code) {
        if (code == null || code.length() > 240) return null;
        try {
            Uri uri = Uri.parse(code);
            if (!"mooncat".equals(uri.getScheme()) || !"pair".equals(uri.getAuthority()) ||
                uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty())) return null;
            String host = uri.getQueryParameter("host");
            String portValue = uri.getQueryParameter("port");
            String key = uri.getQueryParameter("key");
            if (!privateIp(host) || portValue == null || !portValue.matches("[0-9]{1,5}") ||
                key == null || !key.matches("[0-9a-fA-F]{64}")) return null;
            int port = Integer.parseInt(portValue);
            if (port < 1024 || port > 65535) return null;
            return new Pairing(host, port, key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean privateIp(String host) {
        if (host == null) return false;
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("0|[1-9][0-9]{0,2}")) return false;
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) return false;
        }
        return octets[0] == 10 || (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31);
    }

    static String signature(String key, String route, String time, String nonce, byte[] body) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        hmac.update(("POST\n" + route + "\n" + time + "\n" + nonce + "\n").getBytes(StandardCharsets.UTF_8));
        return hex(hmac.doFinal(body));
    }

    private static String hex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) output.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return output.toString();
    }

    private static final class Pairing {
        final String host;
        final int port;
        final String key;

        Pairing(String host, int port, String key) {
            this.host = host;
            this.port = port;
            this.key = key;
        }
    }
}
