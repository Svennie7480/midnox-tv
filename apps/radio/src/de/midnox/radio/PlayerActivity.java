package de.midnox.radio;

import android.app.Activity;
import android.graphics.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Grosse Ansicht des laufenden Senders: Cover, Titel, Verlauf; links/rechts wechselt den Sender. */
public class PlayerActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());
    Ui.Bg bg;
    Art art;
    TextView station, titleV, artistV, group, sleepV;
    LinearLayout hist;
    Ui.Pill playBtn, favBtn, sleepBtn;
    Bitmap bgSrc;
    static final int[] SLEEP = {0, 15, 30, 45, 60, 90};
    static int sleepIdx;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        boolean portrait = !U.tv && dm.heightPixels > dm.widthPixels;
        if (U.tv) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= 21) { getWindow().setStatusBarColor(U.BG); getWindow().setNavigationBarColor(U.BG); }
        }
        FrameLayout root = new FrameLayout(this);
        bg = new Ui.Bg(); root.setBackground(bg);
        LinearLayout main = new LinearLayout(this);
        main.setGravity(portrait ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        if (portrait) { main.setOrientation(LinearLayout.VERTICAL); main.setPadding(U.px(24), U.px(24), U.px(24), U.px(24)); }
        else if (U.tv) main.setPadding(U.px(70), U.px(40), U.px(60), U.px(40));
        else main.setPadding(U.px(30), U.px(16), U.px(24), U.px(16));
        if (U.tv) root.addView(main, new FrameLayout.LayoutParams(-1, -1));
        else { ScrollView sv = new ScrollView(this); sv.setFillViewport(true); sv.addView(main, new FrameLayout.LayoutParams(-1, -2)); root.addView(sv, new FrameLayout.LayoutParams(-1, -1)); if (!portrait) main.setMinimumHeight(dm.heightPixels - U.px(30)); }

        art = new Art(this);
        int as = U.tv ? U.px(330) : portrait ? Math.min(dm.widthPixels - U.px(70), U.px(340)) : Math.min(dm.heightPixels - U.px(60), U.px(300));
        main.addView(art, new LinearLayout.LayoutParams(as, as));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(portrait ? 0 : U.px(U.tv ? 46 : 26), portrait ? U.px(10) : 0, 0, 0);
        if (portrait) info.setGravity(Gravity.CENTER_HORIZONTAL);
        main.addView(info, portrait ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1f));
        station = U.text(this, "", 13, U.MUTED); station.setLetterSpacing(0.2f);
        info.addView(station);
        titleV = U.text(this, "", U.tv ? 34 : 26, 0xFFFFFFFF);
        if (portrait) titleV.setGravity(Gravity.CENTER);
        titleV.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titleV.setMaxLines(2); titleV.setEllipsize(TextUtils.TruncateAt.END);
        titleV.setPadding(0, U.px(10), 0, U.px(4));
        info.addView(titleV);
        artistV = U.text(this, "", U.tv ? 20 : 17, 0xFFC9CCE0);
        artistV.setMaxLines(1); artistV.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(artistV);
        group = U.text(this, "", 12, U.MUTED);
        group.setPadding(0, U.px(8), 0, U.px(22));
        info.addView(group);

        LinearLayout btns = new LinearLayout(this);
        info.addView(btns);
        LinearLayout btns2 = btns;
        if (!U.tv) {   // Handy: Zurueck/Weiter als eigene Knoepfe, zweite Reihe fuer Favorit und Timer
            pill(btns, "⏮", new Runnable() { public void run() { step(-1); } });
        }
        playBtn = pill(btns, "Pause", new Runnable() { public void run() { PlayerService.toggle(PlayerActivity.this); } });
        if (!U.tv) {
            pill(btns, "⏭", new Runnable() { public void run() { step(1); } });
            btns2 = new LinearLayout(this);
            LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(-2, -2); b2.topMargin = U.px(10);
            info.addView(btns2, b2);
        }
        favBtn = pill(btns2, "♡  Favorit", new Runnable() { public void run() {
            PlayerService ps = PlayerService.inst; if (ps == null || ps.station == null) return;
            Catalog.toggleFav(PlayerActivity.this, ps.station); update(); } });
        sleepBtn = pill(btns2, "Einschlaf-Timer", new Runnable() { public void run() {
            PlayerService ps = PlayerService.inst; if (ps == null) return;
            sleepIdx = ps.sleepLeft() > 0 ? (sleepIdx + 1) % SLEEP.length : 1;
            int next = SLEEP[sleepIdx];
            ps.sleep(next); update(); } });
        if (U.tv) pill(btns, "Senderliste", new Runnable() { public void run() { finish(); } });
        if (U.tv) playBtn.requestFocus();

        TextView hh = U.text(this, "ZULETZT GESPIELT", 11, U.MUTED); hh.setLetterSpacing(0.22f);
        hh.setPadding(0, U.px(30), 0, U.px(8));
        info.addView(hh);
        hist = new LinearLayout(this); hist.setOrientation(LinearLayout.VERTICAL);
        info.addView(hist);
        TextView tip = U.text(this, U.tv ? "◀  ▶  Sender wechseln" : "", 11, 0x668A8FA8);
        tip.setPadding(0, U.px(18), 0, 0);
        info.addView(tip);

        setContentView(root);
    }

    Ui.Pill pill(LinearLayout parent, String s, Runnable r) {
        Ui.Pill p = new Ui.Pill(this, s, r);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = U.px(12);
        parent.addView(p, lp);
        return p;
    }

    final Runnable changed = new Runnable() { public void run() { update(); } };

    void update() {
        PlayerService ps = PlayerService.inst;
        if (ps == null || ps.station == null) { finish(); return; }
        Station s = ps.station;
        station.setText((ps.preparing ? "VERBINDE MIT " : ps.playing ? "" : "PAUSIERT  ·  ") + s.name.toUpperCase(Locale.GERMANY));
        PlayerService.Song cur = ps.current;
        if (ps.error != null && !ps.playing) { titleV.setText(ps.error); artistV.setText(""); }
        else if (cur != null && !cur.title.isEmpty()) { titleV.setText(cur.title); artistV.setText(cur.artist); }
        else { titleV.setText(s.name); artistV.setText(ps.preparing ? "" : s.caption()); }
        group.setText(s.group == null || s.group.isEmpty() || s.group.equals("Suche") ? "" : s.group);
        playBtn.setText(ps.playing || ps.preparing ? "❚❚  Pause" : "▶  Abspielen");
        favBtn.setText(Catalog.isFav(this, s) ? "♥  Favorit" : "♡  Favorit");
        int sl = ps.sleepLeft();
        sleepBtn.setText(sl > 0 ? "Aus in " + sl + " Min." : "Einschlaf-Timer");
        Bitmap cov = ps.cover;
        Bitmap logo = Img.now(s.logo);
        if (logo == null && s.logo != null) Img.load(s.logo, 512, new Img.Cb() { public void done(Bitmap x) { if (x != null) update(); } });
        art.set(s, cov, logo, ps.playing);
        Bitmap bsrc = cov != null ? cov : logo;
        if (bsrc != bgSrc) { bgSrc = bsrc; bg.art = Ui.soft(bsrc); bg.tint = s.color; bg.invalidateSelf(); }
        hist.removeAllViews();
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMANY);
        int n = 0;
        for (PlayerService.Song x : ps.history) {
            if (x.title.isEmpty() || n++ >= 5) continue;
            TextView t = U.text(this, (x.at > 0 ? hm.format(new Date(x.at * 1000)) + "   " : "") + (x.artist.isEmpty() ? x.title : x.artist + " – " + x.title), 13, 0xFFB8BCD0);
            t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(0, U.px(3), 0, U.px(3));
            hist.addView(t);
        }
        if (n == 0) hist.addView(U.text(this, "Titel erscheinen hier, sobald der Sender sie mitsendet.", 12, U.MUTED));
    }

    void step(int d) {
        PlayerService ps = PlayerService.inst;
        List<Station> l = MainActivity.playList;
        if (ps == null || ps.station == null || l.isEmpty()) return;
        int i = 0;
        for (int k = 0; k < l.size(); k++) if (l.get(k).id.equals(ps.station.id)) i = k;
        i = (i + d + l.size()) % l.size();
        PlayerService.play(this, l.get(i));
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE) { PlayerService.toggle(this); return true; }
            if (k == KeyEvent.KEYCODE_MEDIA_NEXT || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) { step(1); return true; }
            if (k == KeyEvent.KEYCODE_MEDIA_PREVIOUS || k == KeyEvent.KEYCODE_MEDIA_REWIND) { step(-1); return true; }
            View f = getCurrentFocus();
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && f == playBtn) { step(-1); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT && f instanceof Ui.Pill && ((ViewGroup) f.getParent()).indexOfChild(f) == ((ViewGroup) f.getParent()).getChildCount() - 1) { step(1); return true; }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onResume() { super.onResume(); PlayerService.listen(changed); update(); h.post(anim); }
    @Override protected void onPause() { super.onPause(); PlayerService.unlisten(changed); h.removeCallbacks(anim); }
    final Runnable anim = new Runnable() { public void run() { art.invalidate(); h.postDelayed(this, 50); } };

    /** Cover bzw. Logo mit pulsierendem Ring. */
    static final class Art extends View {
        Station s; Bitmap cover, logo; boolean playing;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Art(android.content.Context c) { super(c); }
        void set(Station s, Bitmap cover, Bitmap logo, boolean playing) { this.s = s; this.cover = cover; this.logo = logo; this.playing = playing; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            if (s == null) return;
            float w = getWidth(), dp = U.dp, pad = 18 * dp;
            RectF r = new RectF(pad, pad, w - pad, w - pad);
            if (playing) {
                float ph = (System.currentTimeMillis() % 2400) / 2400f;
                for (int i = 0; i < 2; i++) {
                    float q = (ph + i * 0.5f) % 1f;
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * dp); p.setShader(U.brand(0, w));
                    p.setAlpha((int) (140 * (1 - q)));
                    float g = q * pad;
                    c.drawRoundRect(new RectF(r.left - g, r.top - g, r.right + g, r.bottom + g), 26 * dp + g, 26 * dp + g, p);
                }
                p.setAlpha(255); p.setShader(null); p.setStyle(Paint.Style.FILL);
            }
            p.setColor(0x66000000);
            c.drawRoundRect(new RectF(r.left + 4 * dp, r.top + 10 * dp, r.right + 4 * dp, r.bottom + 12 * dp), 26 * dp, 26 * dp, p);
            Ui.logo(c, r, s, cover != null ? cover : logo, 26 * dp);
            if (cover != null && logo != null) {   // kleines Senderlogo in der Ecke
                float ls = 64 * dp;
                RectF lr = new RectF(r.right - ls - 10 * dp, r.bottom - ls - 10 * dp, r.right - 10 * dp, r.bottom - 10 * dp);
                p.setColor(0x99000000); c.drawRoundRect(new RectF(lr.left - 2 * dp, lr.top - 2 * dp, lr.right + 2 * dp, lr.bottom + 2 * dp), 12 * dp, 12 * dp, p);
                Ui.logo(c, lr, s, logo, 10 * dp);
            }
        }
    }
}
