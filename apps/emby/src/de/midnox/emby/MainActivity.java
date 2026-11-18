package de.midnox.emby;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.FrameLayout;
import androidx.media3.common.*;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * midnox Emby: Die gesamte Oberfläche ist HTML (assets/ui) in einer transparenten WebView,
 * darunter liegt die native Videofläche (ExoPlayer). Die WebView spricht über "Android" mit der App.
 */
public class MainActivity extends Activity {
    WebView web; SurfaceView surface; ExoPlayer player;
    final Handler h = new Handler(Looper.getMainLooper());
    boolean pageReady, wasPlaying;
    String pendingJs;
    JSONObject pendingUpdate;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF07060D);
        surface = new SurfaceView(this);
        surface.setVisibility(View.GONE);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true); s.setAllowFileAccessFromFileURLs(true); s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true); s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setFocusable(true); web.setFocusableInTouchMode(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) { android.util.Log.i("midnox-emby", m.message() + " @" + m.lineNumber()); return true; }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) { pageReady = true; if (pendingJs != null) { js(pendingJs); pendingJs = null; } }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return !url.startsWith("file:"); }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        web.loadUrl("file:///android_asset/ui/index.html");
        web.requestFocus();
        initPlayer();
        if (Update.due(this)) Update.check(this, new Update.Cb() { public void result(JSONObject u) { if (u != null) { pendingUpdate = u; js("APP.onUpdate(" + u + ")"); } } });
    }

    void js(String code) {
        if (!pageReady) { pendingJs = pendingJs == null ? code : pendingJs + ";" + code; return; }
        web.evaluateJavascript(code, null);
    }
    static String q(String s) { return JSONObject.quote(s == null ? "" : s); }

    // ===================== Player =====================
    void initPlayer() {
        DefaultLoadControl lc = new DefaultLoadControl.Builder().setBufferDurationsMs(4000, 50000, 900, 2500).setPrioritizeTimeOverSizeThresholds(true).build();
        DefaultHttpDataSource.Factory ds = new DefaultHttpDataSource.Factory().setUserAgent("midnoxEmby/" + U.versionName(this))
            .setConnectTimeoutMs(10000).setReadTimeoutMs(30000).setAllowCrossProtocolRedirects(true);
        player = new ExoPlayer.Builder(this).setLoadControl(lc).setMediaSourceFactory(new DefaultMediaSourceFactory(ds)).build();
        player.setVideoSurfaceView(surface);
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int st) {
                if (st == Player.STATE_READY) send("{\"e\":\"ready\"}");
                if (st == Player.STATE_ENDED) send("{\"e\":\"ended\"}");
                tick();
            }
            @Override public void onIsPlayingChanged(boolean p) {
                if (p && surface.getVisibility() == View.VISIBLE) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                tick();
            }
            @Override public void onPlayerError(PlaybackException e) {
                send("{\"e\":\"error\",\"code\":" + e.errorCode + ",\"msg\":" + q(e.getErrorCodeName() + (e.getCause() != null ? ": " + e.getCause().getMessage() : "")) + "}");
            }
            @Override public void onTracksChanged(Tracks t) { send(tracksJson(t)); }
            @Override public void onCues(CueGroup g) {
                StringBuilder sb = new StringBuilder();
                for (Cue c : g.cues) if (c.text != null) { if (sb.length() > 0) sb.append('\n'); sb.append(c.text); }
                send("{\"e\":\"cues\",\"text\":" + q(sb.toString()) + "}");
            }
            @Override public void onVideoSizeChanged(VideoSize v) { fitVideo(v.width, v.height, v.pixelWidthHeightRatio); }
        });
    }
    void send(String json) { js("window.PLAYER&&PLAYER.ev(" + json + ")"); }
    final Runnable ticker = new Runnable() { public void run() { tick(); if (player != null && player.getPlaybackState() != Player.STATE_IDLE) h.postDelayed(this, 500); } };
    void tick() {
        if (player == null) return;
        long pos = player.getCurrentPosition(), dur = player.getDuration(), buf = player.getBufferedPosition();
        int st = player.getPlaybackState();
        send("{\"e\":\"tick\",\"pos\":" + pos + ",\"dur\":" + (dur == C.TIME_UNSET ? 0 : dur) + ",\"buf\":" + buf + ",\"playing\":" + player.isPlaying()
            + ",\"state\":" + st + ",\"wantPlay\":" + player.getPlayWhenReady() + "}");
    }
    void fitVideo(int w, int hgt, float par) {
        if (w <= 0 || hgt <= 0) return;
        View root = (View) surface.getParent();
        float vr = w * par / hgt, sr = root.getWidth() / (float) Math.max(1, root.getHeight());
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (vr > sr) { lp.width = root.getWidth(); lp.height = Math.round(root.getWidth() / vr); }
        else { lp.height = root.getHeight(); lp.width = Math.round(root.getHeight() * vr); }
        lp.gravity = Gravity.CENTER; surface.setLayoutParams(lp);
    }
    String tracksJson(Tracks t) {
        JSONArray a = new JSONArray();
        try {
            int gi = 0;
            for (Tracks.Group g : t.getGroups()) {
                int type = g.getType();
                if (type == C.TRACK_TYPE_AUDIO || type == C.TRACK_TYPE_TEXT) {
                    for (int i = 0; i < g.length; i++) {
                        Format f = g.getTrackFormat(i);
                        a.put(new JSONObject().put("type", type == C.TRACK_TYPE_AUDIO ? "audio" : "text").put("g", gi).put("i", i)
                            .put("lang", f.language == null ? "" : f.language).put("label", f.label == null ? "" : f.label).put("id", f.id == null ? "" : f.id)
                            .put("ch", f.channelCount).put("codec", f.sampleMimeType == null ? "" : f.sampleMimeType)
                            .put("sel", g.isTrackSelected(i)).put("ok", g.isTrackSupported(i)));
                    }
                }
                gi++;
            }
        } catch (JSONException ignored) {}
        return "{\"e\":\"tracks\",\"list\":" + a + "}";
    }

    void play(JSONObject o) throws JSONException {
        String url = o.getString("url");
        MediaItem.Builder mb = new MediaItem.Builder().setUri(Uri.parse(url));
        if (o.optBoolean("hls") || url.contains(".m3u8")) mb.setMimeType(MimeTypes.APPLICATION_M3U8);
        JSONArray subs = o.optJSONArray("subs");
        if (subs != null) {
            List<MediaItem.SubtitleConfiguration> sc = new ArrayList<>();
            for (int i = 0; i < subs.length(); i++) {
                JSONObject s = subs.getJSONObject(i);
                String mime = s.optString("mime", "vtt").equals("srt") ? MimeTypes.APPLICATION_SUBRIP : s.optString("mime").equals("ass") ? MimeTypes.TEXT_SSA : MimeTypes.TEXT_VTT;
                sc.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.getString("url"))).setMimeType(mime)
                    .setLanguage(s.optString("lang", null)).setLabel(s.optString("label", null)).setId("ext" + s.optInt("index")).build());
            }
            mb.setSubtitleConfigurations(sc);
        }
        boolean video = o.optBoolean("video", true);
        surface.setVisibility(video ? View.VISIBLE : View.GONE);
        TrackSelectionParameters.Builder tp = player.getTrackSelectionParameters().buildUpon().clearOverrides()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !o.optBoolean("textOn", false))
            .setPreferredTextLanguages(o.optString("textLang", "de"), "ger", "deu")
            .setPreferredAudioLanguages(o.optString("audioLang", "de"), "ger", "deu");
        player.setTrackSelectionParameters(tp.build());
        player.setPlaybackParameters(PlaybackParameters.DEFAULT);
        player.setMediaItem(mb.build(), Math.max(0, o.optLong("startMs", 0)));
        player.setPlayWhenReady(!o.optBoolean("paused", false));
        player.prepare();
        h.removeCallbacks(ticker); h.post(ticker);
    }
    void selectTrack(String type, int group, int index) {
        int t = type.equals("audio") ? C.TRACK_TYPE_AUDIO : C.TRACK_TYPE_TEXT;
        TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon();
        if (index < 0) { b.setTrackTypeDisabled(t, true); player.setTrackSelectionParameters(b.build()); return; }
        List<Tracks.Group> gs = player.getCurrentTracks().getGroups();
        if (group < 0 || group >= gs.size()) return;
        b.setTrackTypeDisabled(t, false).setOverrideForType(new TrackSelectionOverride(gs.get(group).getMediaTrackGroup(), index));
        player.setTrackSelectionParameters(b.build());
    }
    void stopPlayer() {
        if (player == null) return;
        player.stop(); player.clearMediaItems();
        surface.setVisibility(View.GONE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        h.removeCallbacks(ticker);
    }

    // ===================== Tasten =====================
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        String name = null;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP: name = "up"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: name = "down"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: name = "left"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: name = "right"; break;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BUTTON_A: name = "ok"; break;
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE: case KeyEvent.KEYCODE_BUTTON_B: name = "back"; break;
            case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_BUTTON_Y: name = "menu"; break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_SPACE: name = "playpause"; break;
            case KeyEvent.KEYCODE_MEDIA_PLAY: name = "play"; break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE: name = "pause"; break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: case KeyEvent.KEYCODE_BUTTON_R1: name = "ff"; break;
            case KeyEvent.KEYCODE_MEDIA_REWIND: case KeyEvent.KEYCODE_BUTTON_L1: name = "rew"; break;
            case KeyEvent.KEYCODE_MEDIA_NEXT: name = "next"; break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: name = "prev"; break;
            case KeyEvent.KEYCODE_MEDIA_STOP: name = "stop"; break;
            case KeyEvent.KEYCODE_SEARCH: name = "search"; break;
        }
        if (name == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) js("window.KEY&&KEY(" + q(name) + "," + e.getRepeatCount() + ")");
        return true;
    }

    @Override protected void onStop() { super.onStop(); if (player != null && player.isPlaying()) { wasPlaying = true; player.pause(); } js("window.APP&&APP.onPause&&APP.onPause()"); }
    @Override protected void onStart() { super.onStart(); js("window.APP&&APP.onResume&&APP.onResume()"); }
    @Override protected void onDestroy() { Phone.stop(); if (player != null) { player.release(); player = null; } super.onDestroy(); }

    // ===================== Brücke zur Oberfläche =====================
    final class Bridge {
        @JavascriptInterface public String info() {
            try {
                return new JSONObject().put("deviceId", U.deviceId(MainActivity.this)).put("deviceName", U.deviceName())
                    .put("version", U.versionName(MainActivity.this)).put("versionCode", Update.ownCode(MainActivity.this)).put("ip", U.ownIp())
                    .put("model", Build.MODEL).put("sdk", Build.VERSION.SDK_INT).toString();
            } catch (JSONException e) { return "{}"; }
        }
        @JavascriptInterface public String codecs() {
            JSONArray a = new JSONArray(); Set<String> seen = new HashSet<>();
            try {
                for (android.media.MediaCodecInfo ci : new android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).getCodecInfos())
                    if (!ci.isEncoder()) for (String t : ci.getSupportedTypes()) if (seen.add(t.toLowerCase())) a.put(t.toLowerCase());
            } catch (Exception ignored) {}
            return a.toString();
        }
        @JavascriptInterface public String pget(String k) { return U.prefs(MainActivity.this).getString(k, null); }
        @JavascriptInterface public void pset(String k, String v) { if (v == null) U.prefs(MainActivity.this).edit().remove(k).apply(); else U.prefs(MainActivity.this).edit().putString(k, v).apply(); }
        @JavascriptInterface public void http(final String id, final String method, final String url, final String headers, final String body) {
            new Thread(new Runnable() { public void run() {
                int code = 0; String res = "";
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod(method); c.setConnectTimeout(10000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("Accept", "application/json");
                    if (headers != null && !headers.isEmpty()) { JSONObject hh = new JSONObject(headers); Iterator<String> it = hh.keys(); while (it.hasNext()) { String n = it.next(); c.setRequestProperty(n, hh.getString(n)); } }
                    if (body != null && !method.equals("GET")) {
                        c.setDoOutput(true);
                        if (c.getRequestProperty("Content-Type") == null) c.setRequestProperty("Content-Type", "application/json");
                        byte[] bb = body.getBytes("UTF-8"); c.setFixedLengthStreamingMode(bb.length);
                        OutputStream os = c.getOutputStream(); os.write(bb); os.close();
                    }
                    code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    res = in == null ? "" : U.read(in);
                    c.disconnect();
                } catch (Exception ex) { code = 0; res = ex.getClass().getSimpleName() + ": " + ex.getMessage(); }
                final String js = "NET.done(" + q(id) + "," + code + "," + q(res) + ")";
                h.post(new Runnable() { public void run() { MainActivity.this.js(js); } });
            } }, "http").start();
        }
        @JavascriptInterface public void play(final String json) { h.post(new Runnable() { public void run() { try { MainActivity.this.play(new JSONObject(json)); } catch (Exception e) { send("{\"e\":\"error\",\"code\":0,\"msg\":" + q(e.toString()) + "}"); } } }); }
        @JavascriptInterface public void pause() { h.post(new Runnable() { public void run() { if (player != null) player.pause(); } }); }
        @JavascriptInterface public void resume() { h.post(new Runnable() { public void run() { if (player != null) { if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0); player.play(); } } }); }
        @JavascriptInterface public void seek(final double ms) { h.post(new Runnable() { public void run() { if (player != null) player.seekTo(Math.max(0, (long) ms)); } }); }
        @JavascriptInterface public void speed(final double r) { h.post(new Runnable() { public void run() { if (player != null) player.setPlaybackParameters(new PlaybackParameters((float) r)); } }); }
        @JavascriptInterface public void stop() { h.post(new Runnable() { public void run() { stopPlayer(); } }); }
        @JavascriptInterface public void track(final String type, final int group, final int index) { h.post(new Runnable() { public void run() { if (player != null) selectTrack(type, group, index); } }); }
        @JavascriptInterface public void video(final boolean show) { h.post(new Runnable() { public void run() { surface.setVisibility(show ? View.VISIBLE : View.GONE); } }); }
        @JavascriptInterface public void volume(final double v) { h.post(new Runnable() { public void run() { if (player != null) player.setVolume((float) v); } }); }
        @JavascriptInterface public void keyboard(final boolean show) {
            h.post(new Runnable() { public void run() {
                InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (show) { web.requestFocus(); im.showSoftInput(web, InputMethodManager.SHOW_FORCED); } else im.hideSoftInputFromWindow(web.getWindowToken(), 0);
            } });
        }
        @JavascriptInterface public void discover() {
            new Thread(new Runnable() { public void run() { final String r = Discovery.find(2500); h.post(new Runnable() { public void run() { js("APP.onDiscover(" + r + ")"); } }); } }, "discover").start();
        }
        @JavascriptInterface public String phone(String stateJson) {
            String url = Phone.start(MainActivity.this, new Phone.Cb() { public void got(final String json) { h.post(new Runnable() { public void run() { js("APP.onPhone(" + json + ")"); } }); } });
            Phone.setState(stateJson);
            return url;
        }
        @JavascriptInterface public void phoneOff() { Phone.setState(null); }
        @JavascriptInterface public void installUpdate() { h.post(new Runnable() { public void run() { if (pendingUpdate != null) Update.install(MainActivity.this, pendingUpdate, new Runnable() { public void run() { js("APP.toast('Update konnte nicht geladen werden')"); } }); } }); }
        @JavascriptInterface public void checkUpdate() { h.post(new Runnable() { public void run() { Update.check(MainActivity.this, new Update.Cb() { public void result(JSONObject u) { pendingUpdate = u; js("APP.onUpdate(" + (u == null ? "null" : u.toString()) + ",true)"); } }); } }); }
        @JavascriptInterface public void exit() { h.post(new Runnable() { public void run() { stopPlayer(); moveTaskToBack(true); } }); }
    }
}
