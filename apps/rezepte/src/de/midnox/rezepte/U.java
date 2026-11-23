package de.midnox.rezepte;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.util.*;

final class U {
    static final String UA = "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";

    static byte[] readStream(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[65536]; int r;
        while ((r = in.read(b)) > 0) bo.write(b, 0, r);
        return bo.toByteArray();
    }
    static byte[] asset(Context c, String path) throws IOException {
        InputStream in = c.getAssets().open(path);
        try { return readStream(in); } finally { in.close(); }
    }
    static byte[] readFile(File f) throws IOException { FileInputStream in = new FileInputStream(f); try { return readStream(in); } finally { in.close(); } }
    static String readText(File f, String def) { try { return new String(readFile(f), "UTF-8"); } catch (Exception e) { return def; } }
    /** atomar schreiben: erst .tmp, dann umbenennen – ein Absturz hinterlässt nie eine halbe Datei */
    static void write(File f, byte[] data) throws IOException {
        File t = new File(f.getPath() + ".tmp");
        FileOutputStream o = new FileOutputStream(t);
        try { o.write(data); o.getFD().sync(); } finally { o.close(); }
        if (!t.renameTo(f)) { f.delete(); if (!t.renameTo(f)) throw new IOException("rename"); }
    }
    static void writeText(File f, String s) throws IOException { write(f, s.getBytes("UTF-8")); }

    static String mime(String p) {
        String n = p.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".json") || n.endsWith(".webmanifest")) return "application/json; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
    static String imgMime(byte[] b) {
        if (b.length > 3 && (b[0] & 0xff) == 0x89 && b[1] == 'P') return "image/png";
        if (b.length > 11 && b[8] == 'W' && b[9] == 'E' && b[10] == 'B') return "image/webp";
        return "image/jpeg";
    }
    static String ownIp() {
        String best = null;
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); ) {
                NetworkInterface ni = e.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                    InetAddress ad = a.nextElement();
                    if (ad instanceof Inet4Address && !ad.isLoopbackAddress()) {
                        String h = ad.getHostAddress();
                        if (ni.getName().startsWith("wlan") || ni.getName().startsWith("eth")) return h;
                        if (best == null) best = h;
                    }
                }
            }
        } catch (Exception ignored) {}
        return best == null ? "127.0.0.1" : best;
    }
    static String q(String path, String key) {
        int i = path.indexOf('?');
        if (i < 0) return null;
        for (String kv : path.substring(i + 1).split("&")) {
            int e = kv.indexOf('=');
            String k = e < 0 ? kv : kv.substring(0, e);
            if (k.equals(key)) {
                String v = e < 0 ? "" : kv.substring(e + 1);
                try { return URLDecoder.decode(v, "UTF-8"); } catch (Exception ex) { return v; }
            }
        }
        return null;
    }
    static String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }

    /** einfacher HTTP-Aufruf; body == null → GET */
    static byte[] http(String url, String method, byte[] body, Map<String, String> head, int timeout, int max) throws IOException {
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeout); c.setReadTimeout(timeout * 2); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9");
            if (head != null) for (Map.Entry<String, String> h : head.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            c.setRequestMethod(method);
            if (body != null) { c.setDoOutput(true); c.setFixedLengthStreamingMode(body.length); OutputStream o = c.getOutputStream(); o.write(body); o.close(); }
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) { url = new URL(new URL(url), c.getHeaderField("Location")).toString(); c.disconnect(); if (body != null) break; continue; }
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) throw new IOException("HTTP " + code);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[65536]; int r;
            try { while ((r = in.read(b)) > 0) { bo.write(b, 0, r); if (bo.size() > max) throw new IOException("zu groß"); } } finally { in.close(); c.disconnect(); }
            if (code >= 400) { String msg = new String(bo.toByteArray(), "UTF-8"); throw new IOException("HTTP " + code + (msg.length() < 200 ? ": " + msg : "")); }
            return bo.toByteArray();
        }
        throw new IOException("zu viele Weiterleitungen");
    }
    static String id() { return "r" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(new Random().nextInt(1296), 36); }
}
