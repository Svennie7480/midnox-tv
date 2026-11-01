package de.midnox.radio;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Bilder (Logos, Cover) mit Speicher- und Datei-Cache. */
final class Img {
    interface Cb { void done(Bitmap b); }
    static final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    static final Set<String> failed = Collections.synchronizedSet(new HashSet<String>());
    static final ExecutorService pool = Executors.newFixedThreadPool(5);
    static final Handler ui = new Handler(Looper.getMainLooper());
    static File dir;

    static void init(Context c) { dir = new File(c.getCacheDir(), "img"); dir.mkdirs(); }

    static Bitmap now(String url) { return url == null ? null : mem.get(url); }

    static void load(final String url, final int size, final Cb cb) {
        if (url == null || failed.contains(url)) { cb.done(null); return; }
        Bitmap b = mem.get(url);
        if (b != null) { cb.done(b); return; }
        pool.execute(new Runnable() { public void run() {
            final Bitmap r = fetch(url, size);
            if (r != null) mem.put(url, r); else failed.add(url);
            ui.post(new Runnable() { public void run() { cb.done(r); } });
        }});
    }

    static Bitmap fetch(String url, int size) {
        File f = new File(dir, md5(url));
        try {
            if (!f.exists()) {
                byte[] data;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(6000); c.setReadTimeout(10000); c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9) midnoxRadio");
                    if (c.getResponseCode() / 100 != 2) return null;
                    data = readAll(c.getInputStream());
                } catch (UnknownHostException e) {
                    Net.Resp r = Net.open(url);
                    try { if (r.code != 200) return null; data = readAll(r.in); } finally { r.close(); }
                }
                if (data.length < 100) return null;
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int s = 1; while (o.outWidth / (s * 2) >= size && o.outHeight / (s * 2) >= size) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options(); d.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), d);
            if (b == null) f.delete();
            return b;
        } catch (Exception e) { return null; }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[16384]; int n;
        while ((n = in.read(b)) > 0) { o.write(b, 0, n); if (o.size() > 4 * 1024 * 1024) break; }
        in.close(); return o.toByteArray();
    }
    static String md5(String s) {
        try { byte[] h = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8")); StringBuilder sb = new StringBuilder(); for (byte x : h) sb.append(String.format("%02x", x)); return sb.toString(); }
        catch (Exception e) { return String.valueOf(s.hashCode()); }
    }
}
