package de.midnox.maus;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import io.nayuki.qrcodegen.QrCode;

/** Einrichtung (einmal per ADB den Dienst einschalten) und QR-Code fuers Handy. */
public class MainActivity extends Activity {
    static final String SVC = "de.midnox.maus/de.midnox.maus.MouseService";
    final Handler h = new Handler(Looper.getMainLooper());
    LinearLayout body;
    TextView status;
    float dp;
    boolean busy;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Bg());
        body = new LinearLayout(this);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(px(80), px(40), px(80), px(40));
        root.addView(body, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); build(); h.post(watch); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(watch); }

    boolean active() {
        if (MouseService.inst != null) return true;
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(SVC) && MouseService.inst != null;
    }
    boolean lastActive;
    final Runnable watch = new Runnable() { public void run() {
        boolean a = active();
        if (a != lastActive) build();
        h.postDelayed(this, 1000);
    }};

    void build() {
        lastActive = active();
        body.removeAllViews();
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        body.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout t = new LinearLayout(this);
        TextView a = tv("midnox ", 40, Color.WHITE); a.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); t.addView(a);
        t.addView(new Grad(this, "Maus", 40));
        text.addView(t);
        if (lastActive) {
            TextView sub = tv("Dein Handy ist das Touchpad – für jede App.", 18, 0xFFC9CCE0); sub.setPadding(0, px(8), 0, px(26)); text.addView(sub);
            String[][] tips = {{"1", "QR-Code mit dem Handy scannen (gleiches WLAN)"}, {"2", "Finger bewegen = Mauszeiger, kurz tippen = Klick"}, {"3", "Halten = langer Klick · zwei Finger = scrollen und wischen"}, {"4", "Unten: Zurück, Home, Menü, Text eintippen, Pfeiltasten, Lautstärke"}};
            for (String[] tp : tips) text.addView(step(tp[0], tp[1]));
            TextView u = tv(Server.url(this).replaceAll("\\?k=.*", ""), 13, 0xFF8A8FA8); u.setPadding(0, px(22), 0, 0); text.addView(u);
            TextView hint = tv("Tipp: Auf dem Handy „Zum Startbildschirm hinzufügen“ – dann ist das Touchpad mit einem Tipp da.", 12, 0xFF6B6F86);
            hint.setPadding(0, px(8), 0, 0); text.addView(hint);
            QrView q = new QrView(this, Server.url(this));
            LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(px(280), px(280)); ql.leftMargin = px(50);
            body.addView(q, ql);
        } else {
            TextView sub = tv("Einmal einrichten – danach ist die Maus immer bereit.", 18, 0xFFC9CCE0); sub.setPadding(0, px(8), 0, px(22)); text.addView(sub);
            text.addView(step("1", "Netzwerk-Debugging muss an sein: Einstellungen › Mein Fire TV › Entwickleroptionen › ADB-Debugging (falls die Entwickleroptionen fehlen: unter „Info“ 7× auf den Gerätenamen drücken)."));
            text.addView(step("2", "Unten auf „Jetzt einrichten“ drücken und am Fernseher „Zulassen“ bestätigen (Haken bei „Immer zulassen“)."));
            Pill go = new Pill(this, "Jetzt einrichten", new Runnable() { public void run() { setup(); } });
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2); gl.topMargin = px(24);
            text.addView(go, gl);
            status = tv("", 14, 0xFF8A8FA8); status.setPadding(0, px(16), 0, 0); text.addView(status);
            go.requestFocus();
        }
    }

    void setup() {
        if (busy) return;
        busy = true;
        status.setText("Verbinde … falls am Fernseher eine Frage erscheint: „Zulassen“ wählen.");
        status.setTextColor(0xFF8A8FA8);
        new Thread(new Runnable() { public void run() {
            String err = null;
            try {
                String cur = Adb.shell(MainActivity.this, "settings get secure enabled_accessibility_services").trim();
                if (cur.equals("null") || cur.isEmpty()) cur = "";
                if (!cur.contains(SVC)) cur = cur.isEmpty() ? SVC : cur + ":" + SVC;
                Adb.shell(MainActivity.this, "settings put secure enabled_accessibility_services " + cur);
                Adb.shell(MainActivity.this, "settings put secure accessibility_enabled 1");
            } catch (Exception e) {
                err = Adb.status != null && !Adb.status.isEmpty() && !Adb.status.equals("verbunden") ? Adb.status
                    : "Keine Verbindung – ist ADB-Debugging eingeschaltet? (Einstellungen › Mein Fire TV › Entwickleroptionen)";
            }
            final String fe = err;
            h.post(new Runnable() { public void run() {
                busy = false;
                if (fe != null) { status.setText(fe); status.setTextColor(0xFFFCA5A5); }
                else { status.setText("Fertig – einen Moment …"); status.setTextColor(0xFF34D399); }
            }});
        }}).start();
        h.postDelayed(new Runnable() { public void run() { if (busy && status != null) status.setText(Adb.status == null || Adb.status.isEmpty() ? status.getText() : Adb.status); if (busy) h.postDelayed(this, 800); } }, 800);
    }

    LinearLayout step(String n, String s) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, px(6), 0, px(6));
        TextView num = new TextView(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                p.setShader(new LinearGradient(0, 0, getWidth(), 0, new int[]{0xFF7C3AED, 0xFF3B5BF0, 0xFF06B6D4}, null, Shader.TileMode.CLAMP));
                c.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, p);
                super.onDraw(c);
            }
        };
        num.setText(n); num.setTextColor(Color.WHITE); num.setGravity(Gravity.CENTER); num.setTextSize(13);
        r.addView(num, new LinearLayout.LayoutParams(px(26), px(26)));
        TextView t = tv(s, 15, 0xFFE6E8F2); t.setPadding(px(14), px(2), 0, 0); t.setMaxWidth(px(560));
        r.addView(t);
        return r;
    }

    TextView tv(String s, float sp, int col) { TextView t = new TextView(this); t.setText(s); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(col); t.setLineSpacing(px(3), 1f); return t; }
    int px(float v) { return Math.round(v * dp); }

    static final class Grad extends TextView {
        Grad(Context c, String s, float sp) { super(c); setText(s); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); setTextColor(Color.WHITE); }
        @Override protected void onLayout(boolean ch, int l, int t, int r, int b) {
            super.onLayout(ch, l, t, r, b);
            getPaint().setShader(new LinearGradient(0, 0, getWidth(), 0, new int[]{0xFF7C3AED, 0xFF3B5BF0, 0xFF06B6D4}, null, Shader.TileMode.CLAMP));
        }
    }
    static final class Pill extends TextView {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Pill(Context c, String s, final Runnable r) {
            super(c); setText(s); setTextSize(16); setTextColor(Color.WHITE); setGravity(Gravity.CENTER);
            float d = c.getResources().getDisplayMetrics().density;
            setPadding((int) (30 * d), (int) (12 * d), (int) (30 * d), (int) (12 * d));
            setFocusable(true); setClickable(true);
            setOnClickListener(new OnClickListener() { public void onClick(View v) { r.run(); } });
            setOnFocusChangeListener(new OnFocusChangeListener() { public void onFocusChange(View v, boolean f) { invalidate(); } });
        }
        @Override protected void onDraw(Canvas c) {
            RectF r = new RectF(2, 2, getWidth() - 2, getHeight() - 2);
            if (isFocused()) p.setShader(new LinearGradient(0, 0, getWidth(), 0, new int[]{0xFF7C3AED, 0xFF3B5BF0, 0xFF06B6D4}, null, Shader.TileMode.CLAMP));
            else { p.setShader(null); p.setColor(0xFF2A2840); }
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, p);
            super.onDraw(c);
        }
    }
    static final class QrView extends View {
        final QrCode qr; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        QrView(Context c, String text) { super(c); qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), pad = w * 0.07f, cell = (w - 2 * pad) / qr.size;
            p.setColor(Color.WHITE); c.drawRoundRect(new RectF(0, 0, w, w), w * 0.06f, w * 0.06f, p);
            p.setColor(0xFF0B0A14);
            for (int y = 0; y < qr.size; y++) for (int x = 0; x < qr.size; x++)
                if (qr.getModule(x, y)) c.drawRect(pad + x * cell, pad + y * cell, pad + (x + 1) * cell + 0.5f, pad + (y + 1) * cell + 0.5f, p);
        }
    }
    static final class Bg extends android.graphics.drawable.Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            Rect r = getBounds(); float w = r.width(), h = r.height();
            c.drawColor(0xFF0B0A14);
            p.setShader(new RadialGradient(w * 0.1f, h * 0.05f, w * 0.6f, 0x407C3AED, 0, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, p);
            p.setShader(new RadialGradient(w * 0.95f, h, w * 0.5f, 0x3006B6D4, 0, Shader.TileMode.CLAMP)); c.drawRect(0, 0, w, h, p);
        }
        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
