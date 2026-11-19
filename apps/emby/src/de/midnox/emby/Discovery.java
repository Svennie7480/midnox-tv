package de.midnox.emby;

import org.json.*;
import java.net.*;
import java.util.*;

/** Findet Emby- und Jellyfin-Server im Heimnetz (UDP-Broadcast auf Port 7359). */
final class Discovery {
    static String find(int ms) {
        JSONArray out = new JSONArray(); Set<String> seen = new HashSet<>();
        DatagramSocket s = null;
        try {
            s = new DatagramSocket(); s.setBroadcast(true); s.setSoTimeout(400);
            for (String w : new String[]{"who is EmbyServer?", "who is JellyfinServer?"}) {
                byte[] q = w.getBytes("UTF-8");
                s.send(new DatagramPacket(q, q.length, InetAddress.getByName("255.255.255.255"), 7359));
            }
            long end = System.currentTimeMillis() + ms;
            byte[] buf = new byte[4096];
            while (System.currentTimeMillis() < end) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length); s.receive(p);
                    JSONObject o = new JSONObject(new String(p.getData(), 0, p.getLength(), "UTF-8"));
                    String id = o.optString("Id");
                    if (!id.isEmpty() && seen.add(id)) out.put(new JSONObject().put("id", id).put("name", o.optString("Name")).put("address", o.optString("Address")));
                } catch (SocketTimeoutException ignored) {}
            }
        } catch (Exception ignored) {
        } finally { if (s != null) s.close(); }
        return out.toString();
    }
}
