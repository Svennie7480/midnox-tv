package de.midnox.laden;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/** midnox Laden: Ladesaeulen-Suche mit Preisvergleich, Rechner und Ladelogbuch (WebView-Shell). */
public class MainActivity extends Activity {
    WebView wv;
    String pendingOrigin; GeolocationPermissions.Callback pendingCb;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        wv = new WebView(this);
        wv.setBackgroundColor(0xFF0B0A14);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true); s.setSupportZoom(false); s.setBuiltInZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " MidnoxLaden/1.0");
        wv.addJavascriptInterface(new Bridge(), "Android");
        wv.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    pendingOrigin = origin; pendingCb = cb;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
                } else cb.invoke(origin, true, false);
            }
        });
        wv.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("file:")) return false;
                open(u); return true;
            }
        });
        setContentView(wv);
        wv.loadUrl("file:///android_asset/index.html");
    }

    void open(String u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { Toast.makeText(this, "Keine App zum Öffnen gefunden", Toast.LENGTH_SHORT).show(); }
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (code == 7 && pendingCb != null) {
            boolean ok = r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED;
            pendingCb.invoke(pendingOrigin, ok, false); pendingCb = null;
        }
    }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK) { wv.evaluateJavascript("window.onBack&&window.onBack()", new android.webkit.ValueCallback<String>() {
            public void onReceiveValue(String v) { if (!"true".equals(v)) finish(); } }); return true; }
        return super.onKeyDown(k, e);
    }

    @Override protected void onPause() { super.onPause(); wv.onPause(); }
    @Override protected void onResume() { super.onResume(); wv.onResume(); }

    class Bridge {
        @JavascriptInterface public void openUrl(final String u) { runOnUiThread(new Runnable() { public void run() { open(u); } }); }
        /** HTTP ohne CORS-Huerden (fuer Home Assistant im lokalen Netz). Antwort: window.__http(id, status, body) */
        @JavascriptInterface public void http(final int id, final String method, final String url, final String token, final String body) {
            new Thread(new Runnable() { public void run() {
                int code = 0; String out = "";
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000); c.setReadTimeout(30000); c.setRequestMethod(method);
                    if (token != null && token.length() > 0) c.setRequestProperty("Authorization", "Bearer " + token);
                    c.setRequestProperty("Content-Type", "application/json");
                    if (body != null && body.length() > 0) { c.setDoOutput(true); OutputStream o = c.getOutputStream(); o.write(body.getBytes("UTF-8")); o.close(); }
                    code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    if (in != null) { ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); out = bo.toString("UTF-8"); }
                } catch (Exception e) { code = 0; out = String.valueOf(e.getMessage()); }
                final String js = "window.__http(" + id + "," + code + "," + JSONObject.quote(out) + ")";
                runOnUiThread(new Runnable() { public void run() { wv.evaluateJavascript(js, null); } });
            } }).start();
        }
        @JavascriptInterface public boolean tv() { return getPackageManager().hasSystemFeature("android.software.leanback"); }
    }
}
