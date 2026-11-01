package de.midnox.live;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Senderlogos mit Speicher- und Datei-Cache. */
final class Img {
    interface Cb { void done(Bitmap b); }
    static final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(32 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    static final Set<String> failed = Collections.synchronizedSet(new HashSet<String>());
    static final Set<String> pending = Collections.synchronizedSet(new HashSet<String>());
    static final ExecutorService pool = Executors.newFixedThreadPool(4);
    static final Handler ui = new Handler(Looper.getMainLooper());
    static File dir;

    static void init(Context c) { dir = new File(c.getCacheDir(), "logos"); dir.mkdirs(); }

    /** Sofort aus dem Speicher; sonst laden und danach cb aufrufen (z. B. invalidate). */
    static Bitmap get(final String url, final Runnable cb) {
        if (url == null || url.isEmpty() || failed.contains(url)) return null;
        Bitmap b = mem.get(url);
        if (b != null) return b;
        if (!pending.add(url)) return null;
        pool.execute(new Runnable() { public void run() {
            Bitmap r = fetch(url, 160);
            pending.remove(url);
            if (r != null) mem.put(url, r); else failed.add(url);
            if (r != null && cb != null) ui.post(cb);
        }});
        return null;
    }

    static Bitmap fetch(String url, int size) {
        File f = new File(dir, md5(url));
        try {
            if (!f.exists() || f.length() < 100) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(6000); c.setReadTimeout(10000); c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", U.UA);
                if (c.getResponseCode() / 100 != 2) return null;
                ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] buf = new byte[16384]; int n;
                InputStream in = c.getInputStream();
                while ((n = in.read(buf)) > 0) { o.write(buf, 0, n); if (o.size() > 3 * 1024 * 1024) break; }
                in.close();
                if (o.size() < 100) return null;
                try (FileOutputStream w = new FileOutputStream(f)) { w.write(o.toByteArray()); }
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
    static String md5(String s) {
        try { byte[] h = MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8")); StringBuilder sb = new StringBuilder(); for (byte x : h) sb.append(String.format("%02x", x)); return sb.toString(); }
        catch (Exception e) { return String.valueOf(s.hashCode()); }
    }
}
