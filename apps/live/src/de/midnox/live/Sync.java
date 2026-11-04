package de.midnox.live;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Quickübertragen: synchronisiert Quellen (MACs/M3Us), Favoriten, ausgeblendete Gruppen und
 * Zuordnungen direkt zwischen mehreren midnox-Live-Geräten im selben Netzwerk – ohne externen Server.
 * Geräte finden sich per UDP-Broadcast; die Daten laufen über denselben lokalen HTTP-Server (/sync).
 * Teilnahme nur mit gesetztem Netzwerk-Code (auf allen Geräten gleich).
 */
final class Sync {
    static final int UDP = 8157;
    static final String Q = "MXL?";
    static DatagramSocket responder;

    static class Peer { String name = "", ip = ""; int port, count; }

    /** Beantwortet Suchanfragen anderer midnox-Live-Geräte. Einmal beim Start der App aufrufen. */
    static synchronized void startResponder(Context ctx) {
        if (responder != null) return;
        final Context c = ctx.getApplicationContext();
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) c.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) { android.net.wifi.WifiManager.MulticastLock ml = wm.createMulticastLock("midnox-sync"); ml.setReferenceCounted(false); ml.acquire(); }
        } catch (Exception ignored) {}
        try { responder = new DatagramSocket(null); responder.setReuseAddress(true); responder.bind(new InetSocketAddress(UDP)); }
        catch (Exception e) { responder = null; return; }
        new Thread(new Runnable() { public void run() {
            byte[] buf = new byte[256];
            while (true) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    responder.receive(p);
                    String m = new String(p.getData(), p.getOffset(), p.getLength(), "UTF-8").trim();
                    if (!m.startsWith(Q)) continue;
                    if (U.prefs(c).getString("sync_pin", "").isEmpty()) continue;   // nur mit gesetztem Code sichtbar
                    JSONObject r = new JSONObject().put("n", Web.devName(c)).put("ip", U.ownIp()).put("p", Web.port).put("c", Lib.channels.size());
                    byte[] out = r.toString().getBytes("UTF-8");
                    responder.send(new DatagramPacket(out, out.length, p.getAddress(), p.getPort()));
                } catch (Exception e) { if (responder == null) return; }
            }
        }}, "mxl-sync").start();
    }

    /** Sucht rund eine Sekunde lang nach anderen Geräten im Netz. */
    static List<Peer> discover() {
        List<Peer> list = new ArrayList<>();
        DatagramSocket s = null;
        try {
            s = new DatagramSocket(); s.setBroadcast(true);
            byte[] q = Q.getBytes("UTF-8");
            for (InetAddress b : broadcasts()) try { s.send(new DatagramPacket(q, q.length, b, UDP)); } catch (Exception ignored) {}
            s.setSoTimeout(1300);
            String me = U.ownIp();
            long end = System.currentTimeMillis() + 1500;
            byte[] buf = new byte[1024];
            Set<String> seen = new HashSet<>();
            while (System.currentTimeMillis() < end) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    s.receive(p);
                    JSONObject o = new JSONObject(new String(p.getData(), p.getOffset(), p.getLength(), "UTF-8"));
                    Peer pr = new Peer();
                    pr.name = o.optString("n", "TV"); pr.ip = o.optString("ip", p.getAddress().getHostAddress());
                    pr.port = o.optInt("p"); pr.count = o.optInt("c");
                    if (pr.port <= 0 || pr.ip.equals(me)) continue;   // nicht man selbst
                    if (seen.add(pr.ip + ":" + pr.port)) list.add(pr);
                } catch (SocketTimeoutException e) { break; }
                catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        finally { if (s != null) s.close(); }
        Collections.sort(list, new Comparator<Peer>() { public int compare(Peer a, Peer b) { return a.name.compareToIgnoreCase(b.name); } });
        return list;
    }

    static List<InetAddress> broadcasts() {
        List<InetAddress> l = new ArrayList<>();
        try { l.add(InetAddress.getByName("255.255.255.255")); } catch (Exception ignored) {}
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
                NetworkInterface ni = e.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) { InetAddress b = ia.getBroadcast(); if (b != null) l.add(b); }
            }
        } catch (Exception ignored) {}
        return l;
    }

    /** Eigene Daten an einen Peer senden. */
    static String push(Context c, Peer p) {
        try {
            JSONObject r = http(c, p, "import=1", Web.export(c).toString());
            if (!r.optBoolean("ok")) return "„" + p.name + "“: " + r.optString("msg", "Fehler");
            int n = r.optInt("added");
            return "„" + p.name + "“: " + (n == 0 ? "schon aktuell" : n + (n == 1 ? " übernommen" : " übernommen"));
        } catch (Exception e) { return "„" + p.name + "“ nicht erreichbar"; }
    }

    /** Daten von einem Peer holen und lokal zusammenführen. */
    static String pull(Context c, Peer p) {
        try {
            JSONObject o = http(c, p, null, null);
            if (!o.has("midnox_live")) return "„" + p.name + "“: " + o.optString("msg", "keine Daten");
            int n = Web.merge(c, o);
            return n == 0 ? "Schon aktuell – nichts Neues von „" + p.name + "“" : n + (n == 1 ? " Quelle" : " Quellen") + " von „" + p.name + "“ übernommen";
        } catch (Exception e) { return "„" + p.name + "“ nicht erreichbar"; }
    }

    static JSONObject http(Context c, Peer p, String extra, String post) throws Exception {
        String code = U.prefs(c).getString("sync_pin", "");
        String q = "?pin=" + URLEncoder.encode(code, "UTF-8") + (extra != null ? "&" + extra : "");
        HttpURLConnection h = (HttpURLConnection) new URL("http://" + p.ip + ":" + p.port + "/sync" + q).openConnection();
        h.setConnectTimeout(5000); h.setReadTimeout(60000);
        if (post != null) {
            byte[] b = post.getBytes("UTF-8");
            h.setDoOutput(true); h.setRequestMethod("POST"); h.setFixedLengthStreamingMode(b.length);
            h.setRequestProperty("Content-Type", "application/json");
            OutputStream o = h.getOutputStream(); o.write(b); o.close();
        }
        if (h.getResponseCode() != 200) throw new IOException("HTTP " + h.getResponseCode());
        return new JSONObject(U.read(h.getInputStream()));
    }
}
