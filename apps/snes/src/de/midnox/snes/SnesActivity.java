package de.midnox.snes;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.FrameLayout;
import org.json.*;
import java.io.*;
import java.util.*;

/**
 * midnox SNES: Snes9x (WebAssembly) im WebView. Ein lokaler HTTP-Server liefert die Emulator-Seite
 * (127.0.0.1) und die Gamepad-Seite fürs Handy (WLAN-IP). Eingaben (Handy, Fernbedienung,
 * Bluetooth-Controller) laufen als SNES-Bitmaske in den Core: engine.input(player, mask).
 */
public class SnesActivity extends Activity {
    WebView web;
    volatile byte[] rom;                 // aktuell geladenes Spiel
    final int[] pad = {0, 0};            // Tastenstand je Spieler (SNES-Bitmaske)

    // SNES-Tastenbits (müssen zum Core passen): b0 y1 select2 start3 up4 down5 left6 right7 a8 x9 l10 r11
    static final int B=1, Y=2, SELECT=4, START=8, UP=16, DOWN=32, LEFT=64, RIGHT=128, A=256, X=512, L=1024, R=2048;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestStorage();
        Web.start(this);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setBackgroundColor(0xFF000000);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            public boolean onConsoleMessage(ConsoleMessage m) { android.util.Log.d("midnoxSNES", m.message() + " @" + m.lineNumber()); return true; }
        });
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        web.loadUrl("http://127.0.0.1:" + Web.port + "/");
    }

    void requestStorage() {
        if (Build.VERSION.SDK_INT >= 23) try {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- vom Web-Server aufgerufen
    byte[] romBytes() { return rom; }
    void onUpload(String name, byte[] bytes) {
        if (bytes == null || bytes.length < 1024) return;
        rom = bytes;
        push("MX.boot()");
    }
    void onPad(int p, int mask) {
        if (p < 0 || p > 1) return;
        pad[p] = mask;
        push("MX.in(" + p + "," + mask + ")");
    }
    void push(final String js) {
        runOnUiThread(new Runnable() { public void run() { if (web != null) web.evaluateJavascript(js, null); } });
    }

    // ---------------------------------------------------------------- JS-Brücke (vom WebView aufgerufen)
    final class Bridge {
        @JavascriptInterface public String padUrl() { return "http://" + U.ownIp() + ":" + Web.port + "/pad"; }
        @JavascriptInterface public String ip() { return U.ownIp(); }
        @JavascriptInterface public boolean hasRom() { return rom != null; }
        @JavascriptInterface public void log(String m) { android.util.Log.d("midnoxSNES", "JS: " + m); }
        @JavascriptInterface public void exitGame() { rom = null; push("location.reload()"); }

        /** Speicher-Wurzeln (interner Speicher + USB). */
        @JavascriptInterface public String roots() {
            JSONArray a = new JSONArray();
            try {
                File ext = Environment.getExternalStorageDirectory();
                if (ext != null && ext.canRead()) a.put(new JSONObject().put("name", "Interner Speicher").put("path", ext.getAbsolutePath()));
                for (String p : new String[]{"/storage", "/mnt/media_rw", "/mnt/usb"}) {
                    File d = new File(p); File[] fs = d.listFiles();
                    if (fs != null) for (File f : fs) { if (f.isDirectory() && f.canRead() && !f.getName().equals("self") && !f.getName().equals("emulated")) a.put(new JSONObject().put("name", "USB · " + f.getName()).put("path", f.getAbsolutePath())); }
                }
            } catch (Exception ignored) {}
            return a.toString();
        }
        /** Verzeichnisinhalt: Unterordner + ROM-Dateien. */
        @JavascriptInterface public String list(String path) {
            JSONObject out = new JSONObject();
            try {
                File d = new File(path);
                out.put("path", d.getAbsolutePath());
                File parent = d.getParentFile();
                out.put("parent", parent != null && parent.canRead() ? parent.getAbsolutePath() : "");
                JSONArray dirs = new JSONArray(), files = new JSONArray();
                File[] fs = d.listFiles();
                if (fs != null) {
                    Arrays.sort(fs, new Comparator<File>() { public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); } });
                    for (File f : fs) {
                        if (f.getName().startsWith(".")) continue;
                        if (f.isDirectory()) { if (f.canRead()) dirs.put(new JSONObject().put("name", f.getName()).put("path", f.getAbsolutePath())); }
                        else { String n = f.getName().toLowerCase(Locale.ROOT); if (n.endsWith(".sfc") || n.endsWith(".smc") || n.endsWith(".fig") || n.endsWith(".swc") || n.endsWith(".zip")) files.put(new JSONObject().put("name", f.getName()).put("path", f.getAbsolutePath()).put("size", f.length())); }
                    }
                }
                out.put("dirs", dirs); out.put("files", files);
            } catch (Exception e) { try { out.put("error", String.valueOf(e.getMessage())); } catch (Exception ignored) {} }
            return out.toString();
        }
        /** Ausgewählte ROM-Datei laden; danach bootet die Seite. */
        @JavascriptInterface public boolean pick(String path) {
            try {
                File f = new File(path);
                if (!f.canRead() || f.length() < 1024 || f.length() > 64 * 1024 * 1024) return false;
                FileInputStream in = new FileInputStream(f);
                try { rom = U.readStream(in); } finally { in.close(); }
                return true;
            } catch (Exception e) { return false; }
        }
    }

    // ---------------------------------------------------------------- Fernbedienung / Bluetooth-Controller
    static int bit(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return RIGHT;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_BUTTON_A: return B;   // Hauptaktion = SNES B
            case KeyEvent.KEYCODE_BUTTON_B: return A;
            case KeyEvent.KEYCODE_BUTTON_X: return Y;
            case KeyEvent.KEYCODE_BUTTON_Y: return X;
            case KeyEvent.KEYCODE_BUTTON_L1: return L;
            case KeyEvent.KEYCODE_BUTTON_R1: return R;
            case KeyEvent.KEYCODE_BUTTON_START: case KeyEvent.KEYCODE_MENU: return START;
            case KeyEvent.KEYCODE_BUTTON_SELECT: case KeyEvent.KEYCODE_BUTTON_THUMBL: return SELECT;
            default: return 0;
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int kc = e.getKeyCode();
        if (kc == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) { if (rom != null) { rom = null; push("location.reload()"); } else finish(); }
            return true;
        }
        int bit = bit(kc);
        if (bit != 0 && rom != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) pad[0] |= bit;
            else if (e.getAction() == KeyEvent.ACTION_UP) pad[0] &= ~bit;
            else return true;
            push("MX.in(0," + pad[0] + ")");
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK && rom != null) {
            float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
            float x = Math.abs(hx) > 0.3f ? hx : e.getAxisValue(MotionEvent.AXIS_X);
            float y = Math.abs(hy) > 0.3f ? hy : e.getAxisValue(MotionEvent.AXIS_Y);
            int m = pad[0] & ~(UP | DOWN | LEFT | RIGHT);
            if (x < -0.4f) m |= LEFT; else if (x > 0.4f) m |= RIGHT;
            if (y < -0.4f) m |= UP; else if (y > 0.4f) m |= DOWN;
            if (m != pad[0]) { pad[0] = m; push("MX.in(0," + pad[0] + ")"); }
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    @Override protected void onPause() { super.onPause(); if (web != null) web.evaluateJavascript("MX&&MX.pause&&MX.pause()", null); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.evaluateJavascript("MX&&MX.resume&&MX.resume()", null); }
    @Override protected void onDestroy() { if (web != null) web.destroy(); super.onDestroy(); }
}
