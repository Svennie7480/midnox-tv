package de.midnox.tv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import org.json.*;
import java.util.*;

/** midnox Radio (AzuraCast): Daten + Live-Kachel fuer die Startseite. */
public class Radio {
    public static final String DEFAULT_API = "https://radio.midnox.de/api/nowplaying/midnox";

    public static class Song {
        public String title = "", artist = "", album = "", genre = "", art, lyrics = "", playlist = "";
        public long playedAt; public int duration;
    }
    public static class State {
        public Song now; public final List<Song> history = new ArrayList<>(); public Song next;
        public int listeners; public boolean live; public String streamer = "", stream, name = "midnox Radio";
    }

    static String api(Context c) {
        JSONObject cfg = Style.loadConfig(c);
        String u = cfg == null ? "" : cfg.optString("radio_api");
        return u.isEmpty() ? DEFAULT_API : u;
    }

    static Song song(JSONObject o) {
        Song s = new Song();
        if (o == null) return s;
        JSONObject so = o.optJSONObject("song");
        if (so != null) {
            s.title = so.optString("title"); s.artist = so.optString("artist"); s.album = so.optString("album");
            s.genre = so.optString("genre"); s.art = so.optString("art", null); s.lyrics = so.optString("lyrics", "");
            if (s.title.isEmpty()) s.title = so.optString("text");
        }
        s.playedAt = o.optLong("played_at"); s.duration = o.optInt("duration"); s.playlist = o.optString("playlist");
        return s;
    }

    public static State fetch(Context c) throws Exception {
        JSONObject o = new JSONObject(RadioNet.get(api(c)));
        State st = new State();
        JSONObject sta = o.optJSONObject("station");
        if (sta != null) { st.name = sta.optString("name", st.name); st.stream = sta.optString("listen_url", null); }
        st.listeners = o.optJSONObject("listeners") == null ? 0 : o.getJSONObject("listeners").optInt("current");
        JSONObject live = o.optJSONObject("live");
        if (live != null) { st.live = live.optBoolean("is_live"); st.streamer = live.optString("streamer_name"); }
        st.now = song(o.optJSONObject("now_playing"));
        if (o.optJSONObject("playing_next") != null) st.next = song(o.optJSONObject("playing_next"));
        JSONArray h = o.optJSONArray("song_history");
        for (int i = 0; h != null && i < h.length(); i++) st.history.add(song(h.getJSONObject(i)));
        return st;
    }

    /** Sendewellen-Symbol im Verlauf. */
    static void waves(Canvas c, float cx, float cy, float s, Paint p) {
        p.setShader(Style.brand(cx - s, cx + s));
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, s * 0.14f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(s * 0.1f);
        for (int i = 1; i <= 2; i++) {
            float r = s * (0.32f + 0.3f * i);
            RectF o = new RectF(cx - r, cy - r, cx + r, cy + r);
            c.drawArc(o, -40, 80, false, p);
            c.drawArc(o, 140, 80, false, p);
        }
        p.setStyle(Paint.Style.FILL); p.setShader(null);
    }

    /** Live-Kachel: aktuelles Cover rechts, Logo und Titel links. */
    public static class Banner extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        public Bitmap cover; public String title = "", artist = "";
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float w = b.width(), h = b.height(), dp = Style.dp, x0 = b.left, y0 = b.top;
            p.setShader(new LinearGradient(x0, y0, x0 + w, y0 + h, 0xFF15122A, 0xFF0B0A14, Shader.TileMode.CLAMP));
            c.drawRect(b, p);
            p.setShader(null);
            if (cover != null) {   // Cover rechts, nach links ausblendend
                RectF r = new RectF(x0 + w - h, y0, x0 + w, y0 + h);
                int save = c.saveLayer(r, null);
                c.drawBitmap(cover, null, r, p);
                Paint m = new Paint();
                m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                m.setShader(new LinearGradient(r.left, 0, r.left + h * 0.7f, 0, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
                c.drawRect(r, m);
                c.restoreToCount(save);
            }
            float pad = 12 * dp;
            waves(c, x0 + pad + 10 * dp, y0 + pad + 10 * dp, 12 * dp, p);
            t.setTypeface(Style.POP);
            t.setTextSize(Math.min(22 * dp, h * 0.2f));
            t.setShader(Style.brand(x0 + pad, x0 + pad + t.measureText("midnox")));
            c.drawText("midnox", x0 + pad, y0 + h * 0.58f, t);
            t.setShader(null);
            t.setTextSize(Math.min(10 * dp, h * 0.09f)); t.setLetterSpacing(0.35f); t.setColor(0xB3FFFFFF);
            c.drawText("RADIO", x0 + pad + 2 * dp, y0 + h * 0.58f + 14 * dp, t);
            t.setLetterSpacing(0f);
            if (!title.isEmpty()) {
                float maxW = w * 0.62f;
                t.setTextSize(Math.min(11 * dp, h * 0.1f)); t.setColor(Color.WHITE);
                c.drawText(TextUtils.ellipsize(title, t, maxW, TextUtils.TruncateAt.END).toString(), x0 + pad, y0 + h - pad - 12 * dp, t);
                t.setTextSize(Math.min(9 * dp, h * 0.085f)); t.setColor(0xFFB4B8CC);
                c.drawText(TextUtils.ellipsize(artist, t, maxW, TextUtils.TruncateAt.END).toString(), x0 + pad, y0 + h - pad, t);
            }
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
