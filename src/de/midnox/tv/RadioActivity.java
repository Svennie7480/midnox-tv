package de.midnox.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** midnox Radio: laeuft gerade (grosses Cover), Fortschritt, Songtext und zuletzt gespielte Titel. */
public class RadioActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    final Map<String, Bitmap> covers = Collections.synchronizedMap(new HashMap<String, Bitmap>());
    Radio.State state;
    MediaPlayer player;
    boolean playing, preparing;

    ImageView bg;
    Cover cover;
    TextView kind, title, artist, album, listeners, lyrics;
    Progress progress;
    Pill play, textBtn;
    LinearLayout recent;
    ScrollView lyricsBox;
    String shownId = "";
    String streamErr;

    /** Grosses Cover mit Leuchten im Verlauf. */
    static class Cover extends View {
        Bitmap bmp; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Cover(Context c) { super(c); setLayerType(LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp, in = 16 * dp, w = getWidth() - 2 * in, rad = 18 * dp;
            RectF r = new RectF(in, in, in + w, in + w);
            p.setShader(Style.brand(r.left, r.right));
            p.setMaskFilter(new BlurMaskFilter(16 * dp, BlurMaskFilter.Blur.NORMAL));
            p.setAlpha(150);
            c.drawRoundRect(r, rad, rad, p);
            p.setMaskFilter(null); p.setShader(null); p.setAlpha(255);
            int save = c.save();
            Path clip = new Path(); clip.addRoundRect(r, rad, rad, Path.Direction.CW); c.clipPath(clip);
            p.setColor(Style.CARD); c.drawRect(r, p);
            if (bmp != null) c.drawBitmap(bmp, null, r, p);
            else Radio.waves(c, r.centerX(), r.centerY(), w * 0.22f, p);
            c.restoreToCount(save);
        }
        @Override protected void onMeasure(int ws, int hs) { int s = MeasureSpec.getSize(ws); setMeasuredDimension(s, s); }
    }

    /** Fortschrittsbalken mit Zeiten. */
    static class Progress extends View {
        long start; int dur;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Progress(Context c) { super(c); t.setTypeface(Style.POP); }
        static String fmt(long s) { s = Math.max(0, s); return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60); }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp, w = getWidth();
            if (start <= 0) { postInvalidateDelayed(500); return; }
            long el = Math.max(0, System.currentTimeMillis() / 1000 - start);
            if (dur > 0) el = Math.min(el, dur);
            float f = dur > 0 ? el / (float) dur : 0;
            float y = 6 * dp;
            p.setColor(0x33FFFFFF); c.drawRoundRect(new RectF(0, y - 2 * dp, w, y + 2 * dp), 2 * dp, 2 * dp, p);
            p.setShader(Style.brand(0, w)); c.drawRoundRect(new RectF(0, y - 2 * dp, Math.max(4 * dp, w * f), y + 2 * dp), 2 * dp, 2 * dp, p);
            c.drawCircle(Math.max(4 * dp, w * f), y, 5 * dp, p); p.setShader(null);
            t.setTextSize(11 * dp); t.setColor(0xFFB4B8CC);
            c.drawText(fmt(el), 0, y + 20 * dp, t);
            if (dur > 0) { String r = fmt(dur); c.drawText(r, w - t.measureText(r), y + 20 * dp, t); }
            postInvalidateDelayed(500);
        }
    }

    /** Kleine Karte "zuletzt gespielt". */
    class RecentCard extends View {
        final Radio.Song s; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        RecentCard(Context c, Radio.Song s) { super(c); this.s = s; t.setTypeface(Style.POP); }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp, w = getWidth(), rad = 12 * dp;
            RectF r = new RectF(0, 0, w, w);
            int save = c.save();
            Path clip = new Path(); clip.addRoundRect(r, rad, rad, Path.Direction.CW); c.clipPath(clip);
            p.setColor(Style.CARD); c.drawRect(r, p);
            Bitmap b = s.art == null ? null : covers.get(s.art);
            if (b != null) c.drawBitmap(b, null, r, p); else Radio.waves(c, r.centerX(), r.centerY(), w * 0.2f, p);
            c.restoreToCount(save);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1 * dp); p.setColor(0x22FFFFFF);
            c.drawRoundRect(r, rad, rad, p); p.setStyle(Paint.Style.FILL);
            t.setTextSize(12 * dp); t.setColor(Color.WHITE);
            c.drawText(TextUtils.ellipsize(s.title, t, w, TextUtils.TruncateAt.END).toString(), 0, w + 18 * dp, t);
            t.setTextSize(10 * dp); t.setColor(0xFFB4B8CC);
            c.drawText(TextUtils.ellipsize(s.artist, t, w, TextUtils.TruncateAt.END).toString(), 0, w + 33 * dp, t);
            t.setColor(0xFF6B6F86);
            c.drawText(new SimpleDateFormat("HH:mm", Locale.GERMANY).format(new Date(s.playedAt * 1000)), 0, w + 47 * dp, t);
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF07060D);
        bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setAlpha(0.55f);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x6607060D, 0xCC07060D, 0xF207060D}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Style.px(48), Style.px(26), Style.px(48), 0);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        cover = new Cover(this);
        top.addView(cover, new LinearLayout.LayoutParams(Style.px(262), -2));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Style.px(24), Style.px(22), 0, 0);
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(new Style.GradientText(this, "midnox", 22));
        TextView rl = Style.text(this, "  RADIO", 11, 0xB3FFFFFF); rl.setLetterSpacing(0.4f);
        brand.addView(rl);
        info.addView(brand);
        kind = Style.text(this, "LÄUFT GERADE", 11, Style.MUTED); kind.setLetterSpacing(0.3f); kind.setPadding(0, Style.px(16), 0, 0);
        info.addView(kind);
        title = Style.text(this, "…", 30, Color.WHITE); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);
        artist = Style.text(this, "", 17, 0xFFE6E8F2); artist.setSingleLine(true);
        info.addView(artist);
        album = Style.text(this, "", 12, 0xFFB4B8CC); album.setPadding(0, Style.px(4), 0, Style.px(14)); album.setSingleLine(true);
        info.addView(album);
        progress = new Progress(this);
        info.addView(progress, new LinearLayout.LayoutParams(-1, Style.px(32)));
        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setPadding(0, Style.px(8), 0, 0);
        play = new Pill(this, "Abspielen", new Runnable() { public void run() { toggle(); } });
        btns.addView(play);
        textBtn = new Pill(this, "Songtext", new Runnable() { public void run() { toggleLyrics(); } });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2); bl.leftMargin = Style.px(12);
        btns.addView(textBtn, bl);
        listeners = Style.text(this, "", 12, Style.MUTED); listeners.setPadding(Style.px(18), 0, 0, 0);
        btns.addView(listeners);
        info.addView(btns);
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(top);

        TextView rh = Style.text(this, "ZULETZT GESPIELT", 11, Style.MUTED);
        rh.setLetterSpacing(0.3f); rh.setPadding(Style.px(16), Style.px(4), 0, Style.px(10));
        col.addView(rh);
        recent = new LinearLayout(this);
        recent.setPadding(Style.px(16), 0, 0, 0);
        col.addView(recent);
        root.addView(col);

        // Songtext-Feld rechts (ein-/ausblendbar)
        lyricsBox = new ScrollView(this);
        lyricsBox.setVerticalScrollBarEnabled(false);
        lyricsBox.setFocusable(true);
        lyricsBox.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x0007060D, 0xF007060D}));
        lyrics = Style.text(this, "", 15, 0xFFE6E8F2);
        lyrics.setLineSpacing(Style.px(6), 1f);
        lyrics.setPadding(Style.px(90), Style.px(60), Style.px(56), Style.px(60));
        lyricsBox.addView(lyrics);
        lyricsBox.setVisibility(View.GONE);
        root.addView(lyricsBox, new FrameLayout.LayoutParams(Style.px(460), -1, Gravity.END));

        setContentView(root);
        play.requestFocus();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        h.post(poll);
        toggle();   // direkt losspielen
    }

    final Runnable poll = new Runnable() { public void run() {
        new Thread(new Runnable() { public void run() {
            try {
                final Radio.State st = Radio.fetch(RadioActivity.this);
                List<String> arts = new ArrayList<>();
                if (st.now.art != null) arts.add(st.now.art);
                for (Radio.Song s : st.history) if (s.art != null) arts.add(s.art);
                for (String a : arts) if (!covers.containsKey(a)) { Bitmap bm = RadioNet.bitmap(a); if (bm != null) synchronized (covers) { covers.put(a, bm); } }
                h.post(new Runnable() { public void run() { show(st); } });
            } catch (final Exception e) {
                h.post(new Runnable() { public void run() { log("API: " + e); if (state == null) { title.setText("Radio nicht erreichbar"); artist.setText(String.valueOf(e.getMessage())); } } });
            }
        }}).start();
        h.postDelayed(this, 10000);
    }};

    void show(Radio.State st) {
        state = st;
        Radio.Song s = st.now;
        listeners.setText(st.listeners == 1 ? "1 hört zu" : st.listeners + " hören zu");
        kind.setText(st.live ? "LIVE" + (st.streamer.isEmpty() ? "" : "  ·  " + st.streamer) : "LÄUFT GERADE" + (s.playlist.isEmpty() ? "" : "  ·  " + s.playlist.toUpperCase(Locale.GERMANY)));
        String id = s.title + "|" + s.playedAt;
        if (!id.equals(shownId)) {
            shownId = id;
            title.setText(s.title); artist.setText(s.artist);
            List<String> m = new ArrayList<>();
            if (!s.album.isEmpty() && !s.album.equals(s.title)) m.add(s.album);
            if (!s.genre.isEmpty()) m.add(s.genre);
            album.setText(TextUtils.join("   ·   ", m));
            progress.start = s.playedAt; progress.dur = s.duration; progress.invalidate();
            Bitmap bm = s.art == null ? null : covers.get(s.art);
            cover.bmp = bm; cover.invalidate();
            if (bm != null) bg.setImageBitmap(Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(bm, 12, 12, true), 240, 240, true)); // weicher Hintergrund
            for (View v : new View[]{cover, title, artist, album}) { v.setAlpha(0f); v.animate().alpha(1f).setDuration(500).start(); }
            lyrics.setText(s.lyrics.isEmpty() ? "Für diesen Titel ist kein Songtext hinterlegt." : s.lyrics);
            textBtn.setVisibility(s.lyrics.isEmpty() ? View.GONE : View.VISIBLE);
            if (s.lyrics.isEmpty()) lyricsBox.setVisibility(View.GONE);
        }
        recent.removeAllViews();
        for (int i = 0; i < Math.min(6, st.history.size()); i++) {
            RecentCard rc = new RecentCard(this, st.history.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Style.px(118), Style.px(118 + 54));
            lp.rightMargin = Style.px(22);
            recent.addView(rc, lp);
        }
    }

    void toggleLyrics() {
        boolean show = lyricsBox.getVisibility() != View.VISIBLE;
        lyricsBox.setVisibility(show ? View.VISIBLE : View.GONE);
        textBtn.active = show; textBtn.invalidate();
        if (show) { lyricsBox.scrollTo(0, 0); lyricsBox.setAlpha(0f); lyricsBox.animate().alpha(1f).setDuration(250).start(); }
    }

    /** OK im Songtext scrollt weiter; hoch/runter scrollt. */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (lyricsBox.getVisibility() == View.VISIBLE && e.getAction() == KeyEvent.ACTION_DOWN) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) { lyricsBox.smoothScrollBy(0, Style.px(80)); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP) { lyricsBox.smoothScrollBy(0, -Style.px(80)); return true; }
            if (k == KeyEvent.KEYCODE_BACK) { toggleLyrics(); return true; }
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && e.getAction() == KeyEvent.ACTION_DOWN) { toggle(); return true; }
        return super.dispatchKeyEvent(e);
    }

    static final String UA = "midnox TV (Fire TV; Android " + Build.VERSION.RELEASE + ")";

    /** Erst pruefen, welche Stream-Adresse antwortet (mit Klartext-Fehler), dann abspielen. */
    RadioNet.Proxy proxy;

    void toggle() {
        if (preparing) return;
        if (playing) { stopPlayer(); return; }
        preparing = true; play.setText("Verbinde \u2026");
        final String main = state != null && state.stream != null ? state.stream : "https://radio.midnox.de/listen/midnox/radio.mp3";
        new Thread(new Runnable() { public void run() {
            String err = null, local = null;
            try {
                RadioNet.Resp r = RadioNet.open(main);   // Probe: erreichbar?
                int code = r.code; r.close();
                if (code != 200) err = "HTTP " + code + (code == 401 || code == 403 ? " \u2013 Server lehnt Zuh\u00f6rer ab" : "");
                else { proxy = new RadioNet.Proxy(main); local = proxy.start(); }
            } catch (Exception e) { err = e.getClass().getSimpleName() + ": " + e.getMessage(); }
            log("Stream " + main + " -> " + (err == null ? "OK" : err) + (RadioNet.lastInfo.isEmpty() ? "" : "  (" + RadioNet.lastInfo + ")"));
            final String url = local, msg = err;
            h.post(new Runnable() { public void run() {
                if (url == null) { preparing = false; play.setText("Abspielen"); streamErr = "Stream: " + msg; listeners.setText(streamErr); return; }
                streamErr = null;
                start(url);
            }});
        }}).start();
    }

    void start(String url) {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            Map<String, String> hd = new HashMap<>();
            hd.put("User-Agent", UA);
            player.setDataSource(this, android.net.Uri.parse(url), hd);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { public void onPrepared(MediaPlayer mp) {
                preparing = false; playing = true; mp.start(); play.setText("Pause"); play.active = true; play.invalidate();
            }});
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() { public boolean onError(MediaPlayer mp, int what, int extra) {
                log("MediaPlayer-Fehler " + what + "/" + extra + (proxy != null && proxy.error != null ? " (" + proxy.error + ")" : ""));
                stopPlayer(); play.setText("Nochmal versuchen"); streamErr = "Wiedergabefehler " + what + "/" + extra; listeners.setText(streamErr); return true;
            }});
            player.prepareAsync();
        } catch (Exception ex) { log("start: " + ex); stopPlayer(); play.setText("Nochmal versuchen"); }
    }

    void log(String s) {
        try {
            java.io.File f = new java.io.File(getExternalFilesDir(null), "radio_status.txt");
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(new SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY).format(new Date()) + "  " + s + "\n");
            w.close();
        } catch (Exception ignored) {}
    }

    void stopPlayer() {
        preparing = false; playing = false;
        if (player != null) { try { player.stop(); } catch (Exception ignored) {} player.release(); player = null; }
        if (proxy != null) { proxy.stop(); proxy = null; }
        play.setText("Abspielen"); play.active = false; play.invalidate();
    }

    @Override protected void onStop() { super.onStop(); stopPlayer(); h.removeCallbacks(poll); finish(); }
}
