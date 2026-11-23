package dev.mooncat.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

@androidx.annotation.OptIn(markerClass = UnstableApi.class)
public class PlaybackActivity extends Activity implements Player.Listener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable startTimeout = this::tryNext;
    private JSONObject configuration;
    private MobileTorrentPlayback torrentPlayback;
    private JSONArray streams;
    private int current;
    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout heading;
    private TextView status;
    private Button retry;
    private boolean started;
    private boolean resumed;
    private boolean startOnResume;
    private boolean complete;
    private boolean live;
    private long position;
    private long duration;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            configuration = new JSONObject(getIntent().getStringExtra("configuration"));
            streams = configuration.getJSONArray("streams");
            if (streams.length() == 0) {
                finish();
                return;
            }
        } catch (JSONException | NullPointerException error) {
            finish();
            return;
        }
        live = configuration.optBoolean("live");
        torrentPlayback = new MobileTorrentPlayback(this);
        position = (long) (configuration.optDouble("resume", 0) * 1000);
        if (savedInstanceState != null) {
            current = savedInstanceState.getInt("current");
            position = savedInstanceState.getLong("position");
            duration = savedInstanceState.getLong("duration");
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
        createControls();
        openCurrent();
    }

    private void createControls() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(this);
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setShowSubtitleButton(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setControllerShowTimeoutMs(4000);
        frame.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(12), dp(5), dp(12), dp(5));
        heading.setBackgroundColor(0xC6100E19);
        Button close = new Button(this);
        close.setText("‹ Back");
        close.setContentDescription("Back to MoonCat");
        close.setOnClickListener(view -> finish());
        TextView title = new TextView(this);
        title.setText(configuration.optString("title"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(2);
        heading.addView(close);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (!live && new TvConnection(this).paired()) {
            Button handoff = new Button(this);
            handoff.setText("Play on TV");
            handoff.setOnClickListener(view -> sendToTv(handoff));
            heading.addView(handoff);
        }
        frame.addView(heading, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout feedback = new LinearLayout(this);
        feedback.setOrientation(LinearLayout.VERTICAL);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(18), dp(8), dp(18), dp(8));
        status = new TextView(this);
        status.setTextColor(0xFFEEE4FF);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(0xE5100E19);
        status.setPadding(dp(16), dp(8), dp(16), dp(8));
        retry = new Button(this);
        retry.setText("Retry");
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(view -> {
            current = 0;
            openCurrent();
        });
        feedback.addView(status);
        feedback.addView(retry);
        frame.addView(feedback, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility ->
            heading.setVisibility(visibility == View.VISIBLE || !started ? View.VISIBLE : View.GONE));
        setContentView(frame);
    }

    private void openCurrent() {
        releasePlayer();
        started = false;
        if (complete) {
            complete = false;
            position = 0;
        }
        heading.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        JSONObject stream = streams.optJSONObject(current);
        if (stream == null) {
            tryNext();
            return;
        }
        status.setVisibility(View.VISIBLE);
        String provider = stream.optString("provider", "source");
        String url = stream.optString("url");
        JSONObject torrent = stream.optJSONObject("torrent");
        if (MainActivity.validHttps(url)) {
            status.setText("Connecting to " + provider + "…");
            playSource(url, playerHeaders(stream.optJSONObject("headers")), stream.optBoolean("hls"));
        } else if (torrent != null) {
            status.setText("Finding peers for " + provider + "…");
            torrentPlayback.open(torrent, localUrl -> {
                status.setText("Buffering " + provider + "…");
                playSource(localUrl, new HashMap<>(), false);
            }, this::tryNext);
        } else {
            tryNext();
        }
    }

    private void playSource(String url, Map<String, String> headers, boolean hls) {
        DefaultHttpDataSource.Factory data = new DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 MoonCat/3")
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(14_000)
            .setDefaultRequestProperties(headers);
        ExoPlayer fresh = new ExoPlayer.Builder(this)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(data))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build();
        player = fresh;
        playerView.setPlayer(fresh);
        fresh.addListener(this);
        boolean english = "en".equals(configuration.optString("language"));
        fresh.setTrackSelectionParameters(fresh.getTrackSelectionParameters().buildUpon()
            .setPreferredAudioLanguages(english ? "en" : "hi", english ? "hi" : "en")
            .setMaxVideoSize(1920, 1080).build());

        MediaItem.Builder media = new MediaItem.Builder().setUri(url);
        if (hls) media.setMimeType(MimeTypes.APPLICATION_M3U8);
        fresh.setMediaItem(media.build(), live ? C.TIME_UNSET : Math.max(0, position));
        fresh.prepare();
        if (resumed) startPlayback(fresh);
        else startOnResume = true;
    }

    private void startPlayback(ExoPlayer ready) {
        ready.play();
        if (!started) handler.postDelayed(startTimeout, 19_000);
    }

    private void sendToTv(Button action) {
        snapshot();
        JSONObject request = new JSONObject();
        try {
            for (String key : new String[]{"contentId", "videoId", "type", "title", "season", "episode", "episodeTitle"}) {
                request.put(key, configuration.opt(key));
            }
            request.put("positionMs", Math.max(0L, position));
        } catch (JSONException error) {
            Toast.makeText(this, "Could not send this title.", Toast.LENGTH_SHORT).show();
            return;
        }
        action.setEnabled(false);
        new Thread(() -> {
            String message;
            try {
                new TvConnection(this).send(request);
                message = null;
            } catch (Exception error) {
                message = error.getMessage() == null ? "Could not reach the TV." : error.getMessage();
            }
            String failure = message;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                action.setEnabled(true);
                if (failure == null && player != null) player.pause();
                Toast.makeText(this, failure == null ? "TV is finding a source." : failure, Toast.LENGTH_LONG).show();
            });
        }, "mooncat-tv-handoff").start();
    }

    private Map<String, String> playerHeaders(JSONObject object) {
        Map<String, String> headers = new HashMap<>();
        if (object == null) return headers;
        object.keys().forEachRemaining(key -> {
            String value = object.optString(key);
            if (key.matches("(?i)accept|authorization|origin|referer|user-agent|x-[a-z0-9-]+") &&
                value.length() < 2048 && !value.contains("\r") && !value.contains("\n")) {
                headers.put(key, value);
            }
        });
        return headers;
    }

    private void tryNext() {
        handler.removeCallbacks(startTimeout);
        if (!started && current + 1 < streams.length()) {
            current++;
            openCurrent();
            return;
        }
        releasePlayer();
        status.setText("This source could not start. Retry or choose another source.");
        status.setVisibility(View.VISIBLE);
        retry.setVisibility(View.VISIBLE);
        heading.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        ExoPlayer failedPlayer = player;
        handler.post(() -> { if (player == failedPlayer && !isFinishing()) tryNext(); });
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_READY) {
            started = true;
            handler.removeCallbacks(startTimeout);
            status.setVisibility(View.GONE);
            retry.setVisibility(View.GONE);
        } else if (state == Player.STATE_ENDED && !live) {
            complete = true;
            playerView.showController();
        }
    }

    private void snapshot() {
        if (player == null || !started) return;
        long total = player.getDuration();
        if (total != C.TIME_UNSET && total > 0) duration = total;
        position = player.getCurrentPosition();
    }

    private void releasePlayer() {
        handler.removeCallbacks(startTimeout);
        startOnResume = false;
        if (player != null) {
            snapshot();
            playerView.setPlayer(null);
            player.removeListener(this);
            player.release();
            player = null;
        }
        if (torrentPlayback != null) torrentPlayback.close();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (startOnResume && player != null) {
            startOnResume = false;
            startPlayback(player);
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(startTimeout);
        if (player != null) {
            startOnResume = !started && player.getPlayWhenReady();
            player.pause();
        }
        snapshot();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        snapshot();
        bundle.putInt("current", current);
        bundle.putLong("position", position);
        bundle.putLong("duration", duration);
    }

    @Override
    public void finish() {
        snapshot();
        if (configuration != null && !live) {
            setResult(RESULT_OK, new Intent().putExtra("token", configuration.optString("token"))
                .putExtra("position", position / 1000.0).putExtra("duration", duration / 1000.0)
                .putExtra("complete", complete));
        }
        super.finish();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) hideSystemBars();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }
}
