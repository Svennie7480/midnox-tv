package de.midnox.infoscreen;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

/** midnox Infoscreen: Kiosk-Player. 5x Zurueck (oder 3x Menue) + PIN oeffnet das Wartungsmenue. */
public class MainActivity extends Activity {
    static final String DEF = "https://midnox.de/midnoxtv/infoscreen/index.php";
    WebView wv; SharedPreferences sp; final Handler h = new Handler();
    String pin = "1234"; int backCount, menuCount; long lastBack, lastMenu; boolean dialogOpen;

    final Runnable retry = new Runnable() { public void run() { load(); } };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sp = getSharedPreferences("mi", 0); pin = sp.getString("pin", "1234");
        makeWebView();
        hideUi();
    }

    void makeWebView() {
        if (wv != null) { try { wv.destroy(); } catch (Exception ignored) {} }
        wv = new WebView(this);
        wv.setBackgroundColor(0xFF000000);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setSupportZoom(false); s.setBuiltInZoomControls(false); s.setLoadWithOverviewMode(false);
        s.setUserAgentString(s.getUserAgentString() + " MidnoxInfoscreen/1.0");
        wv.setOverScrollMode(View.OVER_SCROLL_NEVER); wv.setVerticalScrollBarEnabled(false); wv.setHorizontalScrollBarEnabled(false);
        wv.setLongClickable(false); wv.setHapticFeedbackEnabled(false);
        wv.addJavascriptInterface(new Bridge(), "Android");
        wv.setWebChromeClient(new WebChromeClient());
        wv.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) { waiting(); h.removeCallbacks(retry); h.postDelayed(retry, 15000); }
            }
            @Override public void onPageFinished(WebView v, String u) { if (!u.startsWith("data:")) h.removeCallbacks(retry); }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) { runOnUiThread(new Runnable() { public void run() { makeWebView(); load(); } }); return true; }
        });
        setContentView(wv);
        load();
    }

    boolean online() {
        try { NetworkInfo n = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo(); return n != null && n.isConnected(); } catch (Exception e) { return true; }
    }

    void load() {
        String u = sp.getString("url", DEF);
        int q = u.indexOf('?'); if (q > 0) u = u.substring(0, q);
        wv.getSettings().setCacheMode(online() ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        wv.loadUrl(u + "?a=player");
    }

    void waiting() {
        wv.loadData("<body style='margin:0;background:#0B0A14;color:#b8b4d8;font:5vh sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center'>"
                + "<div><div style='font-size:9vh;color:#fff;font-weight:800'>midnox <span style='color:#F59E0B'>Infoscreen</span></div><p>Warte auf Internetverbindung …</p></div></body>", "text/html; charset=utf-8", "UTF-8");
    }

    void hideUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) hideUi(); }
    @Override protected void onResume() { super.onResume(); hideUi(); if (wv != null) wv.onResume(); }
    @Override protected void onPause() { super.onPause(); if (wv != null) wv.onPause(); }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (dialogOpen) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                long now = SystemClock.uptimeMillis(); if (now - lastBack > 3000) backCount = 0; lastBack = now;
                if (++backCount >= 5) { backCount = 0; askPin(); }
            }
            return true;   // Zurueck beendet den Kiosk nicht
        }
        if (k == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                long now = SystemClock.uptimeMillis(); if (now - lastMenu > 3000) menuCount = 0; lastMenu = now;
                if (++menuCount >= 3) { menuCount = 0; askPin(); }
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    void askPin() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setHint("PIN");
        dialogOpen = true;
        new AlertDialog.Builder(this).setTitle("Kiosk-PIN eingeben").setView(et)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                dialogOpen = false;
                if (et.getText().toString().equals(pin)) menu(); else Toast.makeText(MainActivity.this, "Falsche PIN", Toast.LENGTH_SHORT).show();
            } })
            .setNegativeButton("Abbrechen", null)
            .setOnDismissListener(new DialogInterface.OnDismissListener() { public void onDismiss(DialogInterface d) { dialogOpen = false; hideUi(); } })
            .show();
    }

    void menu() {
        final boolean auto = sp.getBoolean("autostart", true);
        final String[] it = { "Neu laden", "Server-Adresse ändern", "Kopplung zurücksetzen", "Autostart nach dem Einschalten: " + (auto ? "AN" : "AUS"), "App beenden" };
        dialogOpen = true;
        new AlertDialog.Builder(this).setTitle("Infoscreen – Wartung").setItems(it, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int i) {
            dialogOpen = false;
            if (i == 0) load();
            else if (i == 1) askUrl();
            else if (i == 2) { wv.evaluateJavascript("localStorage.removeItem('mxi_dev');localStorage.removeItem('mxi_c');location.reload()", null); }
            else if (i == 3) { sp.edit().putBoolean("autostart", !auto).apply(); Toast.makeText(MainActivity.this, "Autostart " + (!auto ? "an" : "aus"), Toast.LENGTH_SHORT).show(); }
            else finishAndRemoveTask();
        } }).setOnDismissListener(new DialogInterface.OnDismissListener() { public void onDismiss(DialogInterface d) { dialogOpen = false; hideUi(); } }).show();
    }

    void askUrl() {
        final EditText et = new EditText(this); et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); et.setText(sp.getString("url", DEF));
        dialogOpen = true;
        new AlertDialog.Builder(this).setTitle("Adresse des Servers (index.php)").setView(et)
            .setPositiveButton("Speichern", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String u = et.getText().toString().trim(); if (!u.startsWith("http")) u = "https://" + u;
                sp.edit().putString("url", u).apply(); dialogOpen = false; load();
            } })
            .setNeutralButton("Standard", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { sp.edit().remove("url").apply(); dialogOpen = false; load(); } })
            .setNegativeButton("Abbrechen", null)
            .setOnDismissListener(new DialogInterface.OnDismissListener() { public void onDismiss(DialogInterface d) { dialogOpen = false; hideUi(); } })
            .show();
    }

    class Bridge {
        @JavascriptInterface public void setPin(String p) { if (p != null && p.matches("\\d{4,8}")) { pin = p; sp.edit().putString("pin", p).apply(); } }
    }
}
