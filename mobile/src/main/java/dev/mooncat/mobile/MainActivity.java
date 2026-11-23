package dev.mooncat.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.window.OnBackInvokedDispatcher;
import org.json.JSONObject;
import com.google.zxing.integration.android.IntentIntegrator;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String APP_HOST = "mooncat.invalid";
    private static final String APP_URL = "https://" + APP_HOST + "/index.html";
    private static final int PLAYBACK = 30;
    private static final int MAX_JSON_BYTES = 5 * 1024 * 1024;
    private final ExecutorService requests = Executors.newFixedThreadPool(5);
    private WebView web;
    private TvConnection tv;
    private MoonCatAccount account;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        tv = new TvConnection(this);
        account = new MoonCatAccount(this, this::deliverAccountEvent);
        getWindow().setStatusBarColor(Color.rgb(16, 14, 25));
        getWindow().setNavigationBarColor(Color.rgb(16, 14, 25));
        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.rgb(16, 14, 25));
        if (Build.VERSION.SDK_INT >= 35) {
            layout.setOnApplyWindowInsetsListener((view, insets) -> {
                var safe = insets.getInsets(WindowInsets.Type.systemBars() |
                    WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        }

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(8, 8, 14));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMediaPlaybackRequiresUserGesture(true);
        web.addJavascriptInterface(new AppBridge(), "MoonCatAndroid");
        web.addJavascriptInterface(account, "MoonCatAccountNative");
        web.setWebViewClient(new Client());
        layout.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(layout);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackPressed);
        }
        if (savedState == null || web.restoreState(savedState) == null) web.loadUrl(APP_URL);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
            "typeof mooncatBack === 'function' && mooncatBack()",
            handled -> { if (!"true".equals(handled)) super.onBackPressed(); });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        var scan = IntentIntegrator.parseActivityResult(request, result, data);
        if (scan != null) {
            if (scan.getContents() == null) {
                tvFeedback("Pairing cancelled.");
            } else if (account.scanCode(scan.getContents())) {
                web.evaluateJavascript("window.mooncatOpenAccount?.()", null);
            } else {
                requests.execute(() -> {
                    try {
                        tv.pair(scan.getContents());
                        tvFeedback("MoonCat TV paired.");
                    } catch (Exception error) {
                        tvFeedback(error.getMessage() == null ? "Could not pair with the TV." : error.getMessage());
                    }
                });
            }
            return;
        }
        if (request != PLAYBACK || result != RESULT_OK || data == null) return;
        String token = JSONObject.quote(data.getStringExtra("token"));
        double position = data.getDoubleExtra("position", 0);
        double duration = data.getDoubleExtra("duration", 0);
        boolean complete = data.getBooleanExtra("complete", false);
        web.evaluateJavascript("window.__mooncatProgress(" + token + "," + position + "," +
            duration + "," + complete + ")", null);
    }

    @Override
    protected void onDestroy() {
        account.close();
        web.removeJavascriptInterface("MoonCatAccountNative");
        requests.shutdownNow();
        web.removeJavascriptInterface("MoonCatAndroid");
        ((FrameLayout) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return !request.isForMainFrame() || !isAppHost(request.getUrl()) ||
                !"/index.html".equals(request.getUrl().getPath());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (!isAppHost(url)) return request.isForMainFrame() ? unavailable() : null;
            String path = url.getPath();
            if (path == null || !path.matches("/[a-z0-9_./-]+") || path.contains("..")) return unavailable();
            try {
                String type = path.endsWith(".html") ? "text/html" :
                    path.endsWith(".css") ? "text/css" :
                    path.endsWith(".js") ? "text/javascript" :
                    path.endsWith(".json") ? "application/json" :
                    path.endsWith(".png") ? "image/png" : "image/webp";
                String encoding = type.startsWith("image/") ? null : "UTF-8";
                return new WebResourceResponse(type, encoding, 200, "OK",
                    Collections.singletonMap("X-Content-Type-Options", "nosniff"), getAssets().open(path.substring(1)));
            } catch (IOException ignored) {
                return unavailable();
            }
        }
    }

    private static boolean isAppHost(Uri uri) {
        return "https".equals(uri.getScheme()) && APP_HOST.equals(uri.getHost()) && uri.getPort() == -1;
    }

    private static WebResourceResponse unavailable() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found",
            Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private void deliverAccountEvent(String json) {
        runOnUiThread(() -> {
            if (!isDestroyed() && web != null) web.evaluateJavascript("window.__mooncatAccountEvent(" + json + ")", null);
        });
    }

    private void tvFeedback(String message) {
        runOnUiThread(() -> {
            if (!isDestroyed()) web.evaluateJavascript("window.__mooncatTvFeedback(" + JSONObject.quote(message) + ")", null);
        });
    }

    private class AppBridge {
        @JavascriptInterface
        public boolean hasTv() {
            return tv.paired();
        }

        @JavascriptInterface
        public void scanTv() {
            runOnUiThread(() -> new IntentIntegrator(MainActivity.this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("Scan the QR shown under Phone pairing on your TV")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .initiateScan());
        }

        @JavascriptInterface
        public void forgetTv() {
            tv.forget();
            tvFeedback("MoonCat TV removed.");
        }

        @JavascriptInterface
        public void sendTv(String payload) {
            if (payload == null || payload.length() > 2048 || isDestroyed()) return;
            requests.execute(() -> {
                try {
                    tv.send(new JSONObject(payload));
                    tvFeedback("Sent to MoonCat TV. Your TV is finding a source.");
                } catch (Exception error) {
                    tvFeedback(error.getMessage() == null ? "Could not reach your TV." : error.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void request(long id, String url) {
            if (isDestroyed()) return;
            requests.execute(() -> {
                try {
                    String payload = fetchJson(url);
                    deliver(id, payload, "null");
                } catch (IOException | IllegalArgumentException error) {
                    deliver(id, "null", JSONObject.quote("The provider is unavailable or returned an invalid response."));
                }
            });
        }

        @JavascriptInterface
        public void play(String configuration) {
            if (configuration == null || configuration.length() > 100_000) return;
            runOnUiThread(() -> {
                Intent playback = new Intent(MainActivity.this, PlaybackActivity.class);
                playback.putExtra("configuration", configuration);
                startActivityForResult(playback, PLAYBACK);
            });
        }

        @JavascriptInterface
        public void openExternal(String url) {
            if (!validHttps(url)) return;
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (ActivityNotFoundException ignored) {
                    android.widget.Toast.makeText(MainActivity.this, "No browser is available.", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void deliver(long id, String value, String error) {
        runOnUiThread(() -> {
            if (!isDestroyed()) web.evaluateJavascript(
                "window.__mooncatResult(" + id + "," + value + "," + error + ")", null);
        });
    }

    static boolean validHttps(String value) {
        if (value == null || value.length() > 8192) return false;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null) return false;
            host = host.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
            return host.contains(".") && !host.contains(":") && !host.matches("[0-9.]+") &&
                !host.endsWith(".localhost") && !host.endsWith(".local");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String fetchJson(String target) throws IOException {
        for (int redirect = 0; redirect <= 3; redirect++) {
            if (!validHttps(target)) throw new IOException("Invalid HTTPS provider URL");
            URI uri = URI.create(target);
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() ||
                    address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IOException("Private network address");
                }
            }
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            try {
                connection.setConnectTimeout(6_000);
                connection.setReadTimeout(9_000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "MoonCat/3 Android");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Invalid redirect");
                    target = uri.resolve(location).toString();
                    continue;
                }
                if (status != 200) throw new IOException("Provider request failed");
                try (InputStream stream = connection.getInputStream()) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = stream.read(buffer)) != -1) {
                        if (bytes.size() + read > MAX_JSON_BYTES) throw new IOException("Provider response too large");
                        bytes.write(buffer, 0, read);
                    }
                    String raw = bytes.toString(StandardCharsets.UTF_8.name());
                    Object value = new JSONTokener(raw).nextValue();
                    if (!(value instanceof JSONObject)) throw new IOException("Invalid provider response");
                    return value.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
                } catch (org.json.JSONException error) {
                    throw new IOException("Invalid provider response", error);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects");
    }
}
