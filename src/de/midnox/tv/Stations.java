package de.midnox.tv;

import java.util.*;

/** Senderliste. midnox (eigene API) + bekannte Sender mit Live-Titel aus dem Stream (ICY). */
public class Stations {
    public static class Station {
        public final String id, name, url; public final int color;
        Station(String id, String name, int color, String url) { this.id = id; this.name = name; this.color = color; this.url = url; }
        public boolean midnox() { return id.equals("midnox"); }
    }

    public static final List<Station> ALL = Arrays.asList(
        new Station("midnox", "midnox Radio", 0xFF7C3AED, "https://radio.midnox.de/listen/midnox/radio.mp3"),
        new Station("rtl890", "89.0 RTL", 0xFFE2001A, "http://stream.89.0rtl.de/live/mp3-256/"),
        new Station("sunshine", "Sunshine Live", 0xFFF2B600, "http://stream.sunshine-live.de/live/mp3-192/stream.sunshine-live.de/"),
        new Station("ndr2", "NDR 2", 0xFF0D58A6, "https://icecast.ndr.de/ndr/ndr2/schleswigholstein/mp3/128/stream.mp3"),
        new Station("ndr1", "NDR 1 Welle Nord", 0xFF1F6FC0, "https://icecast.ndr.de/ndr/ndr1wellenord/luebeck/mp3/128/stream.mp3"),
        new Station("absoluthot", "Absolut HOT", 0xFFE6007E, "https://absolut-hot.live-sm.absolutradio.de/absolut-hot/stream/mp3"),
        new Station("90s90s", "90s90s", 0xFFFF2E8B, "http://streams.90s90s.de/pop/mp3-192/streams.90s90s.de/"),
        new Station("80s80s", "80s80s", 0xFF00B4E6, "http://streams.80s80s.de/web/mp3-192/streams.80s80s.de/"),
        new Station("rsh", "R.SH", 0xFFE30613, "http://streams.rsh.de/rsh-live/mp3-192/mediaplayer"),
        new Station("delta", "delta radio", 0xFF00A0DC, "http://streams.deltaradio.de/delta-live/mp3-128/liveradio/"),
        new Station("rhh", "Radio Hamburg", 0xFF0076BF, "https://frontend.streamonkey.net/rhh-1036"),
        new Station("njoy", "N-JOY", 0xFFD4006E, "https://icecast.ndr.de/ndr/njoy/live/mp3/128/stream.mp3"),
        new Station("1live", "1LIVE", 0xFF6F2C91, "https://wdr-1live-live.icecastssl.wdr.de/wdr/1live/live/mp3/128/stream.mp3"),
        new Station("bigfm", "bigFM", 0xFF00A6E2, "https://streams.bigfm.de/bigfm-deutschland-128-mp3"),
        new Station("energy", "ENERGY Hamburg", 0xFFD5001C, "https://frontend.streamonkey.net/energy-hamburg/stream/mp3"),
        new Station("antenne", "ANTENNE BAYERN", 0xFFF39200, "http://mp3channels.webradio.antenne.de/antenne"),
        new Station("rockantenne", "ROCK ANTENNE", 0xFF9B1C1C, "http://mp3channels.webradio.rockantenne.de/rockantenne"),
        new Station("bob", "RADIO BOB!", 0xFFE0A100, "http://streams.radiobob.de/bob-national/mp3-192/mediaplayer"),
        new Station("kiss", "98.8 KISS FM", 0xFFE6007E, "http://stream.kissfm.de/kissfm/mp3-128/internetradio/"),
        new Station("ostseewelle", "Ostseewelle", 0xFF00A0E3, "http://mp3.ostseewelle.c.nmdn.net/ostseewellelive/livestream.mp3"),
        new Station("dlf", "Deutschlandfunk", 0xFF004A99, "https://st01.sslstream.dlf.de/dlf/01/128/mp3/stream.mp3"),
        new Station("klassik", "Klassik Radio", 0xFFB8975A, "https://live.streams.klassikradio.de/klassikradio-deutschland/stream/mp3")
    );

    static android.content.res.AssetManager assets;
    static final Map<String, android.graphics.Bitmap> logos = new HashMap<>();
    static void init(android.content.Context c) { if (assets == null) assets = c.getApplicationContext().getAssets(); }

    /** Senderlogo aus der App (assets/logos/<id>.jpg), null wenn keins vorhanden. */
    public static synchronized android.graphics.Bitmap logo(Station s) {
        if (logos.containsKey(s.id)) return logos.get(s.id);
        android.graphics.Bitmap b = null;
        if (assets != null) try (java.io.InputStream in = assets.open("logos/" + s.id + ".jpg")) { b = android.graphics.BitmapFactory.decodeStream(in); } catch (Exception ignored) {}
        logos.put(s.id, b);
        return b;
    }

    public static Station byId(String id) { for (Station s : ALL) if (s.id.equals(id)) return s; return ALL.get(0); }
}
