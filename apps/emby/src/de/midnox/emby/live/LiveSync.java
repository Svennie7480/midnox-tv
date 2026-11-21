package de.midnox.emby.live;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Einstellungen von midnox Live uebernehmen: auf diesem Geraet (ContentProvider) oder per Quickübertragen im Netzwerk. */
final class LiveSync {
    static final int UDP = 8157;
    static final String Q = "MXL?";
    static class Peer { String name = "", ip = ""; int port, count; }

    /** Sucht rund eine Sekunde lang nach midnox-Live-Geraeten (auch auf diesem Geraet). */
    static List<Peer> discover() {
        List<Peer> list = new ArrayList<>();
        DatagramSocket s = null;
        try {
            s = new DatagramSocket(); s.setBroadcast(true);
            byte[] q = Q.getBytes("UTF-8");
            List<InetAddress> bc = broadcasts();
            try { bc.add(InetAddress.getByName("127.0.0.1")); } catch (Exception ignored) {}
            for (InetAddress b : bc) try { s.send(new DatagramPacket(q, q.length, b, UDP)); } catch (Exception ignored) {}
            s.setSoTimeout(1300);
            long end = System.currentTimeMillis() + 1600;
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
                    if (pr.port <= 0) continue;
                    if (seen.add(pr.ip + ":" + pr.port)) list.add(pr);
                } catch (SocketTimeoutException e) { break; }
                catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        finally { if (s != null) s.close(); }
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

    /** Daten von einem midnox-Live-Geraet holen (Netzwerk-Code aus dessen Einstellungen). */
    static int pull(Context c, String ip, int port, String pin) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL("http://" + ip + ":" + port + "/sync?pin=" + URLEncoder.encode(pin == null ? "" : pin.trim(), "UTF-8")).openConnection();
        h.setConnectTimeout(5000); h.setReadTimeout(60000);
        if (h.getResponseCode() != 200) throw new IOException("Gerät antwortet nicht (HTTP " + h.getResponseCode() + ")");
        JSONObject o = new JSONObject(U.read(h.getInputStream()));
        if (!o.has("midnox_live")) throw new Exception(o.optString("msg", "Netzwerk-Code stimmt nicht"));
        return merge(c, o);
    }

    /** Fuehrt eine Sicherung/Uebertragung mit dem eigenen Bestand zusammen (wie in midnox Live). */
    static int merge(Context c, JSONObject o) throws Exception {
        List<Model.Source> have = Lib.sources(c);
        int added = 0;
        JSONArray a = o.optJSONArray("sources");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject so = a.getJSONObject(i);
            Model.Source s = Model.Source.from(so);
            if (s.type.equals("stalker")) {
                String np = Stalker.normPortal(s.url);
                Model.Source match = null;
                for (Model.Source h : have) if (h.type.equals("stalker") && Stalker.normPortal(h.url).equals(np)) { match = h; break; }
                if (match != null) {
                    List<String> hv = Stalker.macs(match); int add = 0;
                    for (String m : Stalker.macs(s)) if (!hv.contains(m)) { hv.add(m); add++; }
                    if (add > 0) { match.mac = android.text.TextUtils.join("\n", hv); added++; }
                    if (match.epg.isEmpty() && !s.epg.isEmpty()) match.epg = s.epg;
                    continue;
                }
            }
            boolean dup = false;
            for (Model.Source h : have) if (h.id.equals(s.id) || (h.type.equals(s.type) && h.url.equals(s.url) && h.user.equals(s.user) && h.mac.equals(s.mac))) dup = true;
            if (dup) continue;
            if (so.has("file")) try { U.writeFile(Lib.m3uFile(c, s.id), so.getString("file")); } catch (Exception ignored) {}
            have.add(s); added++;
        }
        Lib.saveSources(c, have);
        List<String> favs = Lib.favs(c);
        JSONArray f = o.optJSONArray("favs");
        for (int i = 0; f != null && i < f.length(); i++) if (!favs.contains(f.getString(i))) favs.add(f.getString(i));
        Set<String> hid = Lib.hiddenGroups(c);
        JSONArray hg = o.optJSONArray("hidden");
        for (int i = 0; hg != null && i < hg.length(); i++) hid.add(hg.getString(i));
        android.content.SharedPreferences.Editor ed = U.prefs(c).edit().putString("favs", new JSONArray(favs).toString()).putStringSet("hidden", hid);
        if (o.has("epg_extra")) ed.putString("epg_extra", o.getString("epg_extra"));
        if (o.has("mapping")) {
            JSONArray cur = new JSONArray(U.prefs(c).getString("mapping", "[]")), add = o.getJSONArray("mapping");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < cur.length(); i++) seen.add(cur.getJSONObject(i).toString());
            for (int i = 0; i < add.length(); i++) if (seen.add(add.getJSONObject(i).toString())) cur.put(add.get(i));
            ed.putString("mapping", cur.toString());
        }
        ed.apply();
        return added;
    }
}
