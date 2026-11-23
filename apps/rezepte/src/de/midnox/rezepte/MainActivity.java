package de.midnox.rezepte;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;

public class MainActivity extends Activity {
    WebView web;
    boolean tv;
    String pendingShare;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiModeManager um = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        tv = (um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) || getPackageManager().hasSystemFeature("android.software.leanback");
        Srv.start(this);
        NetService.sync(this);
        if (Build.VERSION.SDK_INT >= 33 && Store.bg() && !tv) try { requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1); } catch (Exception ignored) {}

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(0xFF0B0A12);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String u) { if (pendingShare != null) { share(pendingShare); pendingShare = null; } }
        });
        web.setWebChromeClient(new WebChromeClient() {
            // Datei-Auswahl (Rezeptfoto) auf dem Handy
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<android.net.Uri[]> cb, FileChooserParams p) {
                fileCb = cb;
                try { startActivityForResult(p.createIntent(), 7); } catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setFocusable(true); web.setFocusableInTouchMode(true);
        setContentView(web);
        if (tv) hideBars();
        pendingShare = sharedText(getIntent());
        web.loadUrl("http://127.0.0.1:" + Srv.port + "/" + (tv ? "?tv=1" : "?app=1"));
    }
    ValueCallback<android.net.Uri[]> fileCb;
    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == 7 && fileCb != null) { fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data)); fileCb = null; }
    }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); String s = sharedText(i); if (s != null) share(s); }
    String sharedText(Intent i) { return i != null && Intent.ACTION_SEND.equals(i.getAction()) ? i.getStringExtra(Intent.EXTRA_TEXT) : null; }
    void share(String text) { push("window.APP&&APP.shared(" + org.json.JSONObject.quote(text) + ")"); }

    void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f && tv) hideBars(); }
    void push(final String js) { if (web != null) web.post(new Runnable() { public void run() { web.evaluateJavascript(js, null); } }); }

    @Override public void onBackPressed() { push("APP.back()"); }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int kc = e.getKeyCode();
        if (kc == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) push("APP.back()");
            return true;
        }
        // Fernbedienung: Pfeiltasten/OK gehen an die Oberfläche (nicht in Textfelder, dort schreibt die Tastatur)
        if (!tv) return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        String k = null;
        switch (kc) {
            case KeyEvent.KEYCODE_MENU: k = "menu"; break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: k = "play"; break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: k = "ff"; break;
            case KeyEvent.KEYCODE_MEDIA_REWIND: k = "rew"; break;
        }
        if (k != null) { push("APP.key('" + k + "')"); return true; }
        return super.dispatchKeyEvent(e);
    }

    final class Bridge {
        @JavascriptInterface public boolean tv() { return tv; }
        @JavascriptInterface public void exit() { runOnUiThread(new Runnable() { public void run() { moveTaskToBack(true); } }); }
        @JavascriptInterface public void keepOn(final boolean on) { runOnUiThread(new Runnable() { public void run() {
            if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); } }); }
        @JavascriptInterface public void shareText(String title, String text) {
            Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_SUBJECT, title); i.putExtra(Intent.EXTRA_TEXT, text);
            try { startActivity(Intent.createChooser(i, title)); } catch (Exception ignored) {}
        }
        @JavascriptInterface public void bg() { NetService.sync(MainActivity.this); }
    }
}
