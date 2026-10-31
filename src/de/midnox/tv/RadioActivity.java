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

/**
 * Radio: midnox Radio (mit Verlauf, Songtext, Hoererzahl aus AzuraCast) und weitere Sender
 * (Live-Titel aus dem Stream, Cover ueber die iTunes-Suche, Verlauf seit dem Einschalten).
 */
public class RadioActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    Stations.Station station = Stations.ALL.get(0);   // angezeigter Sender (aus dem Dienst)
    Map<String, Bitmap> covers = new HashMap<>();
    String shownId = "";
    String shownHist = "";

    ImageView bg;
    Cover cover;
    TextView kind, title, artist, album, listeners, lyrics, recentHead;
    Progress progress;
    Pill play, textBtn, stationBtn, timerBtn;
    LinearLayout recent;
    ScrollView lyricsBox;
    FrameLayout root, picker;

    RadioService svc() { return RadioService.inst; }
    LinearLayout wishBox;
    String wishShown;

    void showWishQr(RadioService s) {
        boolean on = station.midnox() && s.wishUrl != null;
        wishBox.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on || s.wishUrl.equals(wishShown)) return;
        wishShown = s.wishUrl;
        wishBox.removeAllViews();
        wishBox.addView(new SetupActivity.QrView(this, s.wishUrl), new LinearLayout.LayoutParams(Style.px(96), Style.px(96)));
        TextView t = Style.text(this, "Musikwunsch?\nQR scannen", 11, 0xCCFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Style.px(6), 0, 0);
        wishBox.addView(t);
    }

    // ------------------------------------------------------------ Ansichten
    /** Senderlogo als Grafik: Name auf Verlauf in Senderfarbe. */
    static void stationArt(Canvas c, RectF r, Stations.Station st, Paint p, TextPaint t, float rad) {
        Bitmap lg = Stations.logo(st);
        if (lg != null) {   // echtes Logo: quadratisch mittig, Rest in der Hintergrundfarbe des Logos
            int bgc = lg.getPixel(2, 2);
            p.setShader(null); p.setColor(bgc | 0xFF000000);
            c.drawRoundRect(r, rad, rad, p);
            // midnox: Schriftzug auf schlichtem Grund -> fuellend und etwas herangezoomt (kein Rand links/rechts)
            float s = st.midnox() ? Math.max(r.width(), r.height()) * 1.35f : Math.min(r.width(), r.height());
            RectF q = new RectF(r.centerX() - s / 2, r.centerY() - s / 2, r.centerX() + s / 2, r.centerY() + s / 2);
            int save = c.save();
            Path clip = new Path(); clip.addRoundRect(r, rad, rad, Path.Direction.CW); c.clipPath(clip);
            p.setFilterBitmap(true);
            c.drawBitmap(lg, null, q, p);
            c.restoreToCount(save);
            return;
        }
        if (st.midnox()) p.setShader(Style.brand(r.left, r.right));
        else p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, st.color, darker(st.color), Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(new RadialGradient(r.left + r.width() * 0.2f, r.top, r.width() * 0.9f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, rad, rad, p);
        p.setShader(null);
        t.setTypeface(Style.POP); t.setColor(Color.WHITE); t.setTextAlign(Paint.Align.CENTER);
        float size = r.height() * 0.2f;
        t.setTextSize(size);
        while (t.measureText(st.name) > r.width() * 0.84f && size > 6) { size -= 1; t.setTextSize(size); }
        c.drawText(st.name, r.centerX(), r.centerY() - (t.ascent() + t.descent()) / 2, t);
        t.setTextAlign(Paint.Align.LEFT);
    }
    static int darker(int c) { return Color.rgb((int) (Color.red(c) * 0.45f), (int) (Color.green(c) * 0.45f), (int) (Color.blue(c) * 0.45f)); }

    /** Grosses Cover mit Leuchten im Verlauf. */
    class Cover extends View {
        Bitmap bmp; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Cover(Context c) { super(c); setLayerType(LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp, in = 16 * dp, w = getWidth() - 2 * in, rad = 18 * dp;
            RectF r = new RectF(in, in, in + w, in + w);
            p.setShader(station.midnox() ? Style.brand(r.left, r.right) : new LinearGradient(r.left, 0, r.right, 0, station.color, darker(station.color), Shader.TileMode.CLAMP));
            p.setMaskFilter(new BlurMaskFilter(16 * dp, BlurMaskFilter.Blur.NORMAL));
            p.setAlpha(150);
            c.drawRoundRect(r, rad, rad, p);
            p.setMaskFilter(null); p.setShader(null); p.setAlpha(255);
            int save = c.save();
            Path clip = new Path(); clip.addRoundRect(r, rad, rad, Path.Direction.CW); c.clipPath(clip);
            if (bmp != null) c.drawBitmap(bmp, null, r, p);
            else stationArt(c, r, station, p, t, 0);
            c.restoreToCount(save);
        }
        @Override protected void onMeasure(int ws, int hs) { int s = MeasureSpec.getSize(ws); setMeasuredDimension(s, s); }
    }

    /** Fortschrittsbalken mit Zeiten (nur wenn Laenge bekannt). */
    static class Progress extends View {
        long start; int dur;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Progress(Context c) { super(c); t.setTypeface(Style.POP); }
        static String fmt(long s) { s = Math.max(0, s); return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60); }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp, w = getWidth();
            if (start <= 0 || dur <= 0) { postInvalidateDelayed(500); return; }
            long el = Math.min(dur, Math.max(0, System.currentTimeMillis() / 1000 - start));
            float f = el / (float) dur, y = 6 * dp;
            p.setColor(0x33FFFFFF); c.drawRoundRect(new RectF(0, y - 2 * dp, w, y + 2 * dp), 2 * dp, 2 * dp, p);
            p.setShader(Style.brand(0, w)); c.drawRoundRect(new RectF(0, y - 2 * dp, Math.max(4 * dp, w * f), y + 2 * dp), 2 * dp, 2 * dp, p);
            c.drawCircle(Math.max(4 * dp, w * f), y, 5 * dp, p); p.setShader(null);
            t.setTextSize(11 * dp); t.setColor(0xFFB4B8CC);
            c.drawText(fmt(el), 0, y + 20 * dp, t);
            String r = fmt(dur); c.drawText(r, w - t.measureText(r), y + 20 * dp, t);
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
            Bitmap b = s.art == null ? null : covers.get(s.art);
            if (b != null) c.drawBitmap(b, null, r, p);
            else stationArt(c, r, station, p, t, 0);
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

    /** Senderkachel in der Auswahl. */
    class StationCard extends View {
        final Stations.Station st; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        StationCard(Context c, final Stations.Station st) {
            super(c); this.st = st;
            setFocusable(true); setClickable(true); setLayerType(LAYER_TYPE_SOFTWARE, null);
            setOnClickListener(new OnClickListener() { public void onClick(View v) { closePicker(); if (svc() != null) svc().switchTo(st); play.requestFocus(); } });
            setOnFocusChangeListener(new OnFocusChangeListener() { public void onFocusChange(View v, boolean has) {
                animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(130).start(); invalidate();
            }});
        }
        @Override protected void onDraw(Canvas c) {
            float dp = Style.dp, pad = 8 * dp, rad = 12 * dp;
            RectF r = new RectF(pad, pad, getWidth() - pad, getHeight() - pad);
            if (isFocused()) {
                glow.setShader(Style.brand(r.left, r.right)); glow.setMaskFilter(new BlurMaskFilter(10 * dp, BlurMaskFilter.Blur.NORMAL)); glow.setAlpha(170);
                c.drawRoundRect(r, rad, rad, glow);
            }
            stationArt(c, r, st, p, t, rad);
            if (st == station) {   // aktueller Sender: kleiner Punkt
                p.setColor(Color.WHITE); c.drawCircle(r.right - 10 * dp, r.top + 10 * dp, 4 * dp, p);
            }
            if (isFocused()) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f * dp); p.setShader(Style.brand(r.left, r.right));
                c.drawRoundRect(r, rad, rad, p); p.setStyle(Paint.Style.FILL); p.setShader(null);
            }
        }
    }

    // ------------------------------------------------------------ Aufbau
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        Stations.init(this);
        station = Stations.byId(Style.prefs(this).getString("radio_station", "midnox"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        root = new FrameLayout(this);
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
        kind = Style.text(this, "", 11, Style.MUTED); kind.setLetterSpacing(0.3f); kind.setPadding(0, Style.px(16), 0, 0);
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
        play = new Pill(this, "Abspielen", new Runnable() { public void run() { RadioService.start(RadioActivity.this, "toggle"); } });
        btns.addView(play);
        stationBtn = new Pill(this, "Sender", new Runnable() { public void run() { openPicker(); } });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2); bl.leftMargin = Style.px(12);
        btns.addView(stationBtn, bl);
        timerBtn = new Pill(this, "Timer", new Runnable() { public void run() { Sleep.cycle(RadioActivity.this, 1); showTimer(); } });
        LinearLayout.LayoutParams bl3 = new LinearLayout.LayoutParams(-2, -2); bl3.leftMargin = Style.px(12);
        btns.addView(timerBtn, bl3);
        textBtn = new Pill(this, "Songtext", new Runnable() { public void run() { toggleLyrics(); } });
        LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(-2, -2); bl2.leftMargin = Style.px(12);
        btns.addView(textBtn, bl2);
        listeners = Style.text(this, "", 12, Style.MUTED); listeners.setPadding(Style.px(18), 0, 0, 0);
        btns.addView(listeners);
        info.addView(btns);
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        col.addView(top);
        recentHead = Style.text(this, "ZULETZT GESPIELT", 11, Style.MUTED);
        recentHead.setLetterSpacing(0.3f); recentHead.setPadding(Style.px(16), Style.px(4), 0, Style.px(10));
        col.addView(recentHead);
        recent = new LinearLayout(this);
        recent.setPadding(Style.px(16), 0, 0, 0);
        col.addView(recent);
        root.addView(col);

        lyricsBox = new ScrollView(this);
        lyricsBox.setVerticalScrollBarEnabled(false);
        lyricsBox.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x0007060D, 0xF007060D}));
        lyrics = Style.text(this, "", 15, 0xFFE6E8F2);
        lyrics.setLineSpacing(Style.px(6), 1f);
        lyrics.setPadding(Style.px(90), Style.px(60), Style.px(56), Style.px(60));
        lyricsBox.addView(lyrics);
        lyricsBox.setVisibility(View.GONE);
        root.addView(lyricsBox, new FrameLayout.LayoutParams(Style.px(460), -1, Gravity.END));

        // Musikwunsch per Handy (nur bei midnox Radio)
        wishBox = new LinearLayout(this);
        wishBox.setOrientation(LinearLayout.VERTICAL);
        wishBox.setGravity(Gravity.CENTER_HORIZONTAL);
        wishBox.setVisibility(View.GONE);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        wl.setMargins(0, 0, Style.px(44), Style.px(30));
        root.addView(wishBox, wl);
        setContentView(root);
        play.requestFocus();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyStation();
        RadioService.listen(render);
        RadioService.start(this, "play");   // beim Oeffnen direkt spielen (laeuft schon -> weiter)
        render.run();
        h.post(timerTick);
    }

    final Runnable timerTick = new Runnable() { public void run() { showTimer(); h.postDelayed(this, 15000); } };
    void showTimer() {
        int m = Sleep.remainingMin(this);
        timerBtn.setText(m <= 0 ? "Timer" : "Timer " + m + " Min.");
        timerBtn.active = m > 0; timerBtn.invalidate();
    }

    /** Anzeige aus dem Zustand des Radio-Dienstes. */
    final Runnable render = new Runnable() { public void run() {
        RadioService s = svc();
        if (s == null) return;
        covers = s.covers;
        showWishQr(s);
        if (s.station != station || shownId.isEmpty() && title.getText().length() <= 1) { station = s.station; applyStation(); }
        play.setText(s.preparing ? "Verbinde \u2026" : s.playing ? "Pause" : "Abspielen");
        play.active = s.playing; play.invalidate();
        boolean m = station.midnox();
        if (m && s.state != null) {
            Radio.State st = s.state;
            listeners.setText(s.error != null ? s.error : st.listeners == 1 ? "1 h\u00f6rt zu" : st.listeners + " h\u00f6ren zu");
            kind.setText(st.live ? "LIVE" + (st.streamer.isEmpty() ? "" : "  \u00b7  " + st.streamer) : "L\u00c4UFT GERADE" + (st.now.playlist.isEmpty() ? "" : "  \u00b7  " + st.now.playlist.toUpperCase(Locale.GERMANY)));
            showSong(st.now);
            progress.start = st.now.playedAt; progress.dur = st.now.duration; progress.invalidate();
            lyrics.setText(st.now.lyrics.isEmpty() ? "F\u00fcr diesen Titel ist kein Songtext hinterlegt." : st.now.lyrics);
            textBtn.setVisibility(st.now.lyrics.isEmpty() ? View.GONE : View.VISIBLE);
            if (st.now.lyrics.isEmpty()) lyricsBox.setVisibility(View.GONE);
        } else {
            listeners.setText(s.error != null ? s.error : "");
            if (s.current != null) showSong(s.current);
        }
        showRecent(s.history());
    }};

    /** Anzeige fuer den gewaehlten Sender zuruecksetzen. */
    void applyStation() {
        shownId = ""; shownHist = "";
        boolean m = station.midnox();
        title.setText(station.name); artist.setText(m ? "" : "Live"); album.setText("");
        kind.setText(m ? "LÄUFT GERADE" : station.name.toUpperCase(Locale.GERMANY) + "  ·  LIVE");
        listeners.setText("");
        progress.start = 0; progress.invalidate();
        textBtn.setVisibility(View.GONE);
        lyricsBox.setVisibility(View.GONE);
        cover.bmp = null; cover.invalidate();
        bg.setImageDrawable(null);
        recent.removeAllViews();
        recentHead.setText(m ? "ZULETZT GESPIELT" : "ZULETZT GESPIELT  ·  SEIT DEM EINSCHALTEN");
    }

    // ------------------------------------------------------------ Senderauswahl
    void openPicker() {
        if (picker != null) return;
        picker = new FrameLayout(this);
        picker.setBackgroundColor(0xF207060D);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Style.px(56), Style.px(30), Style.px(56), 0);
        col.addView(new Style.GradientText(this, "Sender wählen", 28));
        TextView hint = Style.text(this, "OK spielt den Sender  ·  Zurück schließt", 12, Style.MUTED);
        hint.setPadding(0, Style.px(4), 0, Style.px(12));
        col.addView(hint);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, Style.px(6), 0, Style.px(40));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int cols = 5;
        LinearLayout row = null;
        View focus = null;
        for (int i = 0; i < Stations.ALL.size(); i++) {
            if (i % cols == 0) { row = new LinearLayout(this); grid.addView(row); }
            StationCard sc = new StationCard(this, Stations.ALL.get(i));
            row.addView(sc, new LinearLayout.LayoutParams(Style.px(168), Style.px(100)));
            if (Stations.ALL.get(i) == station) focus = sc;
        }
        sv.addView(grid);
        col.addView(sv);
        picker.addView(col);
        root.addView(picker, new FrameLayout.LayoutParams(-1, -1));
        picker.setAlpha(0f); picker.animate().alpha(1f).setDuration(200).start();
        if (focus != null) focus.requestFocus();
    }
    void closePicker() {
        if (picker == null) return;
        root.removeView(picker); picker = null;
        stationBtn.requestFocus();
    }

    /** Titel/Interpret/Cover anzeigen (weich ueberblenden bei neuem Song). */
    void showSong(Radio.Song s) {
        String id = s.artist + "|" + s.title + "|" + s.playedAt;
        Bitmap bm = s.art == null ? null : covers.get(s.art);
        if (id.equals(shownId) && (bm == null || cover.bmp == bm)) return;
        boolean isNew = !id.equals(shownId);
        shownId = id;
        title.setText(s.title.isEmpty() ? station.name : s.title);
        artist.setText(s.artist);
        List<String> m = new ArrayList<>();
        if (!s.album.isEmpty() && !s.album.equals(s.title)) m.add(s.album);
        if (!s.genre.isEmpty()) m.add(s.genre);
        album.setText(TextUtils.join("   ·   ", m));
        cover.bmp = bm; cover.invalidate();
        if (bm != null) bg.setImageBitmap(Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(bm, 12, 12, true), 240, 240, true));
        else bg.setImageDrawable(null);
        if (isNew) for (View v : new View[]{cover, title, artist, album}) { v.setAlpha(0f); v.animate().alpha(1f).setDuration(500).start(); }
    }

    void showRecent(List<Radio.Song> list) {
        StringBuilder sig = new StringBuilder();
        for (Radio.Song x : list) sig.append(x.title).append(x.playedAt).append(x.art != null && covers.containsKey(x.art) ? '+' : '-');
        if (sig.toString().equals(shownHist)) return;
        shownHist = sig.toString();
        recent.removeAllViews();
        for (int i = 0; i < Math.min(station.midnox() ? 5 : 6, list.size()); i++) {
            RecentCard rc = new RecentCard(this, list.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Style.px(118), Style.px(118 + 54));
            lp.rightMargin = Style.px(22);
            recent.addView(rc, lp);
        }
    }

    // ------------------------------------------------------------ Bedienung
    void toggleLyrics() {
        boolean show = lyricsBox.getVisibility() != View.VISIBLE;
        lyricsBox.setVisibility(show ? View.VISIBLE : View.GONE);
        textBtn.active = show; textBtn.invalidate();
        if (show) { lyricsBox.scrollTo(0, 0); lyricsBox.setAlpha(0f); lyricsBox.animate().alpha(1f).setDuration(250).start(); }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            int k = e.getKeyCode();
            if (picker != null && k == KeyEvent.KEYCODE_BACK) { closePicker(); return true; }
            if (picker == null && lyricsBox.getVisibility() == View.VISIBLE) {
                if (k == KeyEvent.KEYCODE_DPAD_DOWN) { lyricsBox.smoothScrollBy(0, Style.px(80)); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_UP) { lyricsBox.smoothScrollBy(0, -Style.px(80)); return true; }
                if (k == KeyEvent.KEYCODE_BACK) { toggleLyrics(); return true; }
            }
            if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { RadioService.start(this, "toggle"); return true; }
            if (picker == null && (k == KeyEvent.KEYCODE_MEDIA_NEXT || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)) { step(1); return true; }
            if (picker == null && (k == KeyEvent.KEYCODE_MEDIA_PREVIOUS || k == KeyEvent.KEYCODE_MEDIA_REWIND)) { step(-1); return true; }
        }
        return super.dispatchKeyEvent(e);
    }

    /** Vor-/Zurueckspulen-Taste wechselt den Sender. */
    void step(int d) {
        int i = Stations.ALL.indexOf(station);
        if (svc() != null) svc().switchTo(Stations.ALL.get((i + d + Stations.ALL.size()) % Stations.ALL.size()));
    }

    // Verlassen: Radio laeuft im Hintergrund weiter
    @Override protected void onStop() { super.onStop(); RadioService.unlisten(render); h.removeCallbacks(timerTick); finish(); }
}
