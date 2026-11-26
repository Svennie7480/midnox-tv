package de.midnox.fahrschule;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;

/** midnox Führerschein: die ganze App ist eine Webseite in den Assets – Lernstand im Speicher der WebView. */
public class MainActivity extends Activity {
    WebView web; boolean tv;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiModeManager um = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        tv = (um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) || getPackageManager().hasSystemFeature("android.software.leanback");
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true); ws.setAllowFileAccess(true); ws.setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(0xFF0B0A12);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setFocusable(true); web.setFocusableInTouchMode(true);
        setContentView(web);
        if (tv) hideBars();
        web.loadUrl("file:///android_asset/www/index.html" + (tv ? "?tv=1" : "?app=1"));
    }
    void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f && tv) hideBars(); }
    void push(final String js) { if (web != null) web.post(new Runnable() { public void run() { web.evaluateJavascript(js, null); } }); }
    @Override public void onBackPressed() { push("APP.back()"); }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) { if (e.getAction() == KeyEvent.ACTION_UP) push("APP.back()"); return true; }
        if (tv && e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_MENU) { push("APP.key('menu')"); return true; }
        return super.dispatchKeyEvent(e);
    }
    @Override protected void onPause() { super.onPause(); push("window.APP&&APP.pause&&APP.pause()"); }

    final class Bridge {
        @JavascriptInterface public boolean tv() { return tv; }
        @JavascriptInterface public void exit() { runOnUiThread(new Runnable() { public void run() { moveTaskToBack(true); } }); }
        @JavascriptInterface public void keepOn(final boolean on) { runOnUiThread(new Runnable() { public void run() {
            if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } }); }
    }
}
