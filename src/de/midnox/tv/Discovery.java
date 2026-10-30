package de.midnox.tv;

import android.content.Context;
import android.net.nsd.*;
import org.json.JSONObject;
import java.net.*;
import java.util.*;

/** Findet Emby-Server (UDP 7359) und Home Assistant (mDNS) im Heimnetz. */
public class Discovery {
    /** Emby antwortet auf "who is EmbyServer?" mit {Address, Id, Name}. */
    public static List<JSONObject> emby() {
        List<JSONObject> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            s.setBroadcast(true);
            s.setSoTimeout(1500);
            byte[] q = "who is EmbyServer?".getBytes("UTF-8");
            s.send(new DatagramPacket(q, q.length, InetAddress.getByName("255.255.255.255"), 7359));
            long end = System.currentTimeMillis() + 2500;
            byte[] buf = new byte[2048];
            while (System.currentTimeMillis() < end) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try { s.receive(p); } catch (SocketTimeoutException e) { break; }
                try {
                    JSONObject o = new JSONObject(new String(p.getData(), 0, p.getLength(), "UTF-8"));
                    if (seen.add(o.optString("Address"))) out.add(o);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
        } finally { if (s != null) s.close(); }
        return out;
    }

    /** Home Assistant meldet sich per mDNS als _home-assistant._tcp. Liefert Adressen wie http://192.168.178.155:8123 */
    public static List<String> homeAssistant(Context c) {
        final List<String> out = Collections.synchronizedList(new ArrayList<String>());
        final NsdManager nsd = (NsdManager) c.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) return out;
        final List<NsdServiceInfo> found = Collections.synchronizedList(new ArrayList<NsdServiceInfo>());
        NsdManager.DiscoveryListener dl = new NsdManager.DiscoveryListener() {
            public void onStartDiscoveryFailed(String t, int e) {}
            public void onStopDiscoveryFailed(String t, int e) {}
            public void onDiscoveryStarted(String t) {}
            public void onDiscoveryStopped(String t) {}
            public void onServiceFound(NsdServiceInfo i) { found.add(i); }
            public void onServiceLost(NsdServiceInfo i) {}
        };
        try {
            nsd.discoverServices("_home-assistant._tcp.", NsdManager.PROTOCOL_DNS_SD, dl);
            Thread.sleep(3000);
            nsd.stopServiceDiscovery(dl);
        } catch (Exception ignored) {}
        // nacheinander aufloesen (NsdManager erlaubt nur eine Aufloesung gleichzeitig)
        for (NsdServiceInfo i : new ArrayList<>(found)) {
            final Object lock = new Object();
            final String[] res = {null};
            try {
                nsd.resolveService(i, new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo s, int e) { synchronized (lock) { lock.notify(); } }
                    public void onServiceResolved(NsdServiceInfo s) {
                        if (s.getHost() != null && s.getHost() instanceof Inet4Address) res[0] = "http://" + s.getHost().getHostAddress() + ":" + s.getPort();
                        synchronized (lock) { lock.notify(); }
                    }
                });
                synchronized (lock) { lock.wait(3000); }
            } catch (Exception ignored) {}
            if (res[0] != null && !out.contains(res[0])) out.add(res[0]);
        }
        return out;
    }
}
