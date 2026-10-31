package de.midnox.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/**
 * Hinweis-Fenster im midnox-Stil ueber jeder App:
 * "shift" = Schlafenszeit vor dem Dienst, "reminder" = Sendung beginnt gleich.
 */
public class PromptActivity extends Activity {
    final Handler h = new Handler(Looper.getMainLooper());

    static void show(Context c, String kind, String title, String text, Bundle extra) {
        Intent i = new Intent(c, PromptActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("kind", kind).putExtra("title", title).putExtra("text", text);
        if (extra != null) i.putExtras(extra);
        c.startActivity(i);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        final Intent in = getIntent();
        final String kind = in.getStringExtra("kind");
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);
        LinearLayout card = new LinearLayout(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            { setWillNotDraw(false); }
            @Override protected void onDraw(Canvas c) {
                float dp = Style.dp; RectF r = new RectF(0, 0, getWidth(), getHeight());
                p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(0xF2121120);
                c.drawRoundRect(r, 20 * dp, 20 * dp, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * dp); p.setShader(Style.brand(0, getWidth()));
                c.drawRoundRect(new RectF(dp, dp, getWidth() - dp, getHeight() - dp), 20 * dp, 20 * dp, p);
            }
        };
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Style.px(36), Style.px(28), Style.px(36), Style.px(28));
        card.addView(new Style.GradientText(this, in.getStringExtra("title"), 26));
        TextView t = Style.text(this, in.getStringExtra("text"), 15, 0xFFE6E8F2);
        t.setLineSpacing(Style.px(4), 1f);
        t.setPadding(0, Style.px(10), 0, Style.px(22));
        t.setMaxWidth(Style.px(520));
        card.addView(t);
        LinearLayout btns = new LinearLayout(this);
        card.addView(btns);
        Pill first = null;
        if ("shift".equals(kind)) {
            first = pill(btns, "Einschlaf-Timer 30 Min.", new Runnable() { public void run() { Sleep.set(PromptActivity.this, 30); finish(); } });
            pill(btns, "In 30 Min. nochmal", new Runnable() { public void run() {
                Style.prefs(PromptActivity.this).edit().putLong("shift_snooze", System.currentTimeMillis() + 30 * 60000L).apply(); finish(); } });
            pill(btns, "Heute nicht", new Runnable() { public void run() { finish(); } });
        } else if ("qr".equals(kind)) {
            String url = in.getStringExtra("url");
            if (url != null) {
                LinearLayout q = new LinearLayout(this);
                q.setGravity(Gravity.CENTER_VERTICAL);
                q.addView(new SetupActivity.QrView(this, url), new LinearLayout.LayoutParams(Style.px(200), Style.px(200)));
                TextView u = Style.text(this, url.replaceAll("\\?k=.*", ""), 13, Style.MUTED);
                u.setPadding(Style.px(22), 0, 0, 0);
                q.addView(u);
                card.addView(q, card.getChildCount() - 1);
                LinearLayout.LayoutParams ql = (LinearLayout.LayoutParams) q.getLayoutParams(); ql.bottomMargin = Style.px(20);
            }
            first = pill(btns, "Schlie\u00dfen", new Runnable() { public void run() { finish(); } });
        } else if ("installed".equals(kind)) {
            final String pkg = in.getStringExtra("pkg");
            first = pill(btns, "Jetzt öffnen", new Runnable() { public void run() {
                Intent li = Style.launchIntent(getPackageManager(), pkg);
                if (li != null) Style.start(PromptActivity.this, li); else Style.toast(PromptActivity.this, "Die App hat kein Startsymbol");
                finish(); } });
            pill(btns, "Schließen", new Runnable() { public void run() { SetupActivity.changed = true; finish(); } });
        } else if ("reminder".equals(kind)) {
            final String ch = in.getStringExtra("channel");
            first = pill(btns, "Jetzt umschalten", new Runnable() { public void run() { TvRows.openTv(PromptActivity.this, ch); finish(); } });
            pill(btns, "Schließen", new Runnable() { public void run() { finish(); } });
            h.postDelayed(new Runnable() { public void run() { finish(); } }, 90000);
        } else {
            first = pill(btns, "OK", new Runnable() { public void run() { finish(); } });
        }
        root.addView(card, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        setContentView(root);
        if (first != null) first.requestFocus();
        card.setAlpha(0f); card.setScaleX(0.96f); card.setScaleY(0.96f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).start();
    }

    Pill pill(LinearLayout row, String s, Runnable r) {
        Pill p = new Pill(this, s, r);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Style.px(12);
        row.addView(p, lp);
        return p;
    }

    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
