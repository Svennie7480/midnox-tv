package de.midnox.tv;

import android.app.Activity;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/** Programm eines Senders (heute/morgen). OK auf einer spaeteren Sendung = Erinnerung an/aus. */
public class ChannelActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        final String ch = getIntent().getStringExtra("channel");
        final String chName = TvRows.name(ch);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Style.Background());
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Style.px(64), Style.px(32), Style.px(64), 0);
        col.addView(new Style.GradientText(this, chName, 32));
        TextView hint = Style.text(this, "OK auf einer späteren Sendung: Erinnerung an/aus  ·  OK auf „Jetzt“ schaltet um", 12, Style.MUTED);
        hint.setPadding(Style.px(2), Style.px(6), 0, Style.px(12));
        col.addView(hint);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false); sv.setPadding(0, 0, 0, Style.px(40));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(Style.px(760), -1));
        root.addView(col);
        setContentView(root);

        Map<String, List<TvRows.Prog>> d = TvRows.readCache(new File(getFilesDir(), "tvnow.json"));
        List<TvRows.Prog> l = d == null ? null : d.get(ch);
        if (l == null) { list.addView(Style.text(this, "Noch keine Programmdaten – kurz warten und erneut öffnen.", 14, Style.MUTED)); return; }
        final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.GERMANY), day = new SimpleDateFormat("EEEE", Locale.GERMANY);
        long now = System.currentTimeMillis();
        String lastDay = "";
        View first = null;
        for (final TvRows.Prog p : l) {
            if (p.stop <= now) continue;
            String dname = day.format(new Date(p.start));
            if (!dname.equals(lastDay)) {
                lastDay = dname;
                TextView sec = Style.text(this, dname.toUpperCase(Locale.GERMANY), 11, Style.MUTED);
                sec.setLetterSpacing(0.3f); sec.setPadding(Style.px(4), Style.px(14), 0, Style.px(6));
                list.addView(sec);
            }
            final boolean running = p.start <= now;
            final LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Style.px(18), Style.px(10), Style.px(18), Style.px(10));
            row.setFocusable(true); row.setClickable(true);
            final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(Style.px(12)); bg.setColor(0x8015141F);
            row.setBackground(bg);
            TextView time = Style.text(this, running ? "Jetzt" : hm.format(new Date(p.start)), 14, running ? 0xFFFFFFFF : 0xFFB4B8CC);
            time.setMinWidth(Style.px(64));
            row.addView(time);
            TextView title = Style.text(this, p.title + (p.sub.isEmpty() ? "" : "  ·  " + p.sub), 15, 0xFFE6E8F2);
            title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            final TextView mark = Style.text(this, "", 13, 0xFFE6E8F2);
            row.addView(mark);
            final Runnable show = new Runnable() { public void run() {
                mark.setText(running ? "umschalten ›" : Daily.hasReminder(ChannelActivity.this, ch, p.start) ? "\u25CF Erinnerung" : "");
            }};
            show.run();
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() { public void onFocusChange(View v, boolean has) {
                bg.setColor(has ? 0xFF1C1B29 : 0x8015141F);
                bg.setStroke(has ? Style.px(2) : 0, has ? Style.C2 : 0);
            }});
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (running) { TvRows.openTv(ChannelActivity.this, ch); return; }
                boolean on = Daily.toggleReminder(ChannelActivity.this, ch, chName, p.start, p.title);
                Style.toast(ChannelActivity.this, on ? "Erinnerung um " + hm.format(new Date(p.start - 5 * 60000L)) + " Uhr gesetzt" : "Erinnerung entfernt");
                show.run();
            }});
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Style.px(6);
            list.addView(row, lp);
            if (first == null) first = row;
        }
        if (first != null) first.requestFocus();
    }
}
