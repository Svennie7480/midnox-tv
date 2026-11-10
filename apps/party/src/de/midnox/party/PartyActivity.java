package de.midnox.party;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.*;

public class PartyActivity extends Activity {
    WebView web;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Web.start(this);

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setBackgroundColor(0xFF0B0A14);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        setContentView(web);
        hideBars();

        int port = Web.port;
        web.loadUrl("http://127.0.0.1:" + port + "/tv");
    }

    void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) hideBars(); }

    void push(final String js) { if (web != null) web.post(new Runnable() { public void run() { web.evaluateJavascript(js, null); } }); }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int kc = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) {
            // BACK beim Loslassen: JS entscheidet (sonst App beenden)
            if (kc == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) { push("TV.key('back')"); return true; }
            return super.dispatchKeyEvent(e);
        }
        String k = null;
        switch (kc) {
            case KeyEvent.KEYCODE_DPAD_UP: k = "up"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: k = "down"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: k = "left"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: k = "right"; break;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A: k = "ok"; break;
            case KeyEvent.KEYCODE_BACK: return true; // ACTION_UP behandelt
            case KeyEvent.KEYCODE_MENU: k = "menu"; break;
        }
        if (k != null) { push("TV.key('" + k + "')"); return true; }
        return super.dispatchKeyEvent(e);
    }

    final class Bridge {
        @JavascriptInterface public void log(String m) { android.util.Log.i("party", m); }
        @JavascriptInterface public String ip() { return U.ownIp(); }
        @JavascriptInterface public int port() { return Web.port; }
        @JavascriptInterface public void exit() { finish(); }
    }
}
