package de.midnox.maus;

import java.io.*;

/** Handy-Seite (assets/pad.html). */
final class Page {
    static String cache;
    static String html() {
        if (cache == null) try {
            InputStream in = MouseService.inst.getAssets().open("pad.html");
            ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            in.close(); cache = o.toString("UTF-8");
        } catch (IOException e) { return "Fehler"; }
        return cache;
    }
    static String denied() {
        return "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><body style='background:#0b0a14;color:#e6e8f2;font-family:system-ui;padding:30px'><h2>midnox Maus</h2><p>Bitte den QR-Code in der App „midnox Maus“ am Fernseher neu scannen.</p>";
    }
}
