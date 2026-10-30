package de.midnox.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import io.nayuki.qrcodegen.QrCode;
import org.json.JSONObject;
import java.util.*;

/** Einrichtung per Handy: QR-Code + Adresse + PIN, Formular laeuft im kleinen Webserver auf dem Fernseher. */
public class SetupActivity extends Activity {
    static volatile boolean changed;
    SetupServer server;
    TextView status;
    final Handler h = new Handler(Looper.getMainLooper());

    /** QR-Code auf weisser, abgerundeter Karte. */
    static class QrView extends View {
        final QrCode qr; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        QrView(Context c, String text) { super(c); qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), pad = w * 0.07f, cell = (w - 2 * pad) / qr.size;
            p.setColor(Color.WHITE);
            c.drawRoundRect(new RectF(0, 0, w, w), w * 0.06f, w * 0.06f, p);
            p.setColor(0xFF0B0A14);
            for (int y = 0; y < qr.size; y++) for (int x = 0; x < qr.size; x++)
                if (qr.getModule(x, y)) c.drawRect(pad + x * cell, pad + y * cell, pad + (x + 1) * cell + 0.5f, pad + (y + 1) * cell + 0.5f, p);
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        String pin = String.format(Locale.ROOT, "%04d", new Random().nextInt(10000));
        server = new SetupServer(this, pin, new SetupServer.Listener() { public void onSaved(final String s) {
            changed = true;
            h.post(new Runnable() { public void run() { status.setText("Gespeichert:  " + s + "\nZurück drücken, um zur Startseite zu gehen."); status.setTextColor(0xFF34D399); } });
        }});
        int port;
        try { port = server.start(); } catch (Exception e) { port = -1; }
        String url = "http://" + SettingsActivity.ownIp() + ":" + port + "/";

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Style.Background());
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Style.px(80), Style.px(40), Style.px(80), Style.px(40));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(new Style.GradientText(this, "Mit Handy einrichten", 34));
        TextView sub = Style.text(this, "Home Assistant und Emby verbinden – ganz ohne Tippen auf der Fernbedienung.", 14, Style.MUTED);
        sub.setPadding(0, Style.px(6), 0, Style.px(22));
        col.addView(sub);
        String[] steps = {
            "Handy ins gleiche WLAN wie den Fernseher.",
            "QR-Code scannen oder die Adresse im Browser öffnen.",
            "Home Assistant und Emby eintragen, PIN eingeben, Verbinden.",
        };
        for (int i = 0; i < steps.length; i++) {
            TextView t = Style.text(this, (i + 1) + "   " + steps[i], 15, 0xFFE6E8F2);
            t.setPadding(0, Style.px(5), 0, Style.px(5));
            col.addView(t);
        }
        TextView adr = Style.text(this, port < 0 ? "Einrichtung konnte nicht gestartet werden" : url, 20, Color.WHITE);
        adr.setPadding(0, Style.px(22), 0, 0);
        col.addView(adr);
        LinearLayout pinRow = new LinearLayout(this);
        pinRow.setGravity(Gravity.CENTER_VERTICAL);
        pinRow.setPadding(0, Style.px(10), 0, 0);
        pinRow.addView(Style.text(this, "PIN   ", 14, Style.MUTED));
        Style.GradientText pt = new Style.GradientText(this, pin.charAt(0) + " " + pin.charAt(1) + " " + pin.charAt(2) + " " + pin.charAt(3), 34);
        pinRow.addView(pt);
        col.addView(pinRow);
        status = Style.text(this, "Suche Server im Netzwerk …", 13, Style.MUTED);
        status.setPadding(0, Style.px(18), 0, 0);
        col.addView(status);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1));

        if (port > 0) {
            QrView q = new QrView(this, url);
            LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(Style.px(240), Style.px(240));
            ql.leftMargin = Style.px(48);
            row.addView(q, ql);
        }
        root.addView(row);
        setContentView(root);

        new Thread(new Runnable() { public void run() {
            final List<JSONObject> em = Discovery.emby();
            final List<String> ha = Discovery.homeAssistant(SetupActivity.this);
            server.embyFound = em; server.haFound = ha;
            h.post(new Runnable() { public void run() {
                if (status.getCurrentTextColor() == 0xFF34D399) return;
                status.setText("Gefunden:  " + ha.size() + "× Home Assistant  ·  " + em.size() + "× Emby  – wird im Formular vorgeschlagen.");
            }});
        }}).start();
    }

    @Override protected void onDestroy() { super.onDestroy(); if (server != null) server.stop(); }
}
