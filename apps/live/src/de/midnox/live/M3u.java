package de.midnox.live;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** M3U/M3U8-Playlisten (#EXTINF mit tvg-id, tvg-logo, group-title, tvg-chno; #EXTGRP; #EXTVLCOPT). */
final class M3u {
    static final Pattern ATTR = Pattern.compile("([a-zA-Z0-9_-]+)=\"([^\"]*)\"");
    static final class Result { final List<Model.Channel> channels = new ArrayList<>(); String epg = ""; }

    static Result parse(BufferedReader r, String src) throws IOException {
        Result out = new Result();
        String line; Model.Channel cur = null; String grp = "";
        int n = 0;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTM3U")) {
                Matcher m = ATTR.matcher(line);
                while (m.find()) { String k = m.group(1).toLowerCase(Locale.ROOT); if ((k.equals("url-tvg") || k.equals("x-tvg-url") || k.equals("tvg-url")) && out.epg.isEmpty()) out.epg = m.group(2).split(",")[0].trim(); }
            } else if (line.startsWith("#EXTINF")) {
                cur = new Model.Channel();
                cur.src = src;
                int comma = lastTitleComma(line);
                cur.name = comma >= 0 ? line.substring(comma + 1).trim() : "";
                Matcher m = ATTR.matcher(comma >= 0 ? line.substring(0, comma) : line);
                while (m.find()) {
                    String k = m.group(1).toLowerCase(Locale.ROOT), v = m.group(2).trim();
                    if (k.equals("tvg-id")) cur.tvgId = v;
                    else if (k.equals("tvg-logo") || k.equals("logo")) cur.logo = v;
                    else if (k.equals("group-title")) cur.group = v;
                    else if (k.equals("tvg-name") && cur.name.isEmpty()) cur.name = v;
                    else if (k.equals("tvg-chno") || k.equals("channel-number")) try { cur.num = Integer.parseInt(v); } catch (Exception ignored) {}
                }
                if (cur.group.isEmpty()) cur.group = grp;
            } else if (line.startsWith("#EXTGRP:")) {
                grp = line.substring(8).trim();
                if (cur != null && cur.group.isEmpty()) cur.group = grp;
            } else if (line.startsWith("#EXTVLCOPT:")) {
                if (cur == null) continue;
                String o = line.substring(11);
                if (o.startsWith("http-user-agent=")) cur.ua = o.substring(16);
                else if (o.startsWith("http-referrer=")) cur.referer = o.substring(14);
            } else if (!line.startsWith("#")) {
                if (cur == null) { cur = new Model.Channel(); cur.src = src; cur.name = line; cur.group = grp; }
                cur.url = line;
                if (cur.name.isEmpty()) cur.name = "Sender " + (n + 1);
                // Nur Live-TV: Filme/Serien-Eintraege aus Xtream-Listen weglassen
                if (!line.matches("(?i).*/(movie|series)/.*")) {
                    cur.id = src + ":" + Integer.toHexString((cur.name + "|" + cur.url).hashCode());
                    out.channels.add(cur);
                    n++;
                }
                cur = null;
            }
        }
        return out;
    }
    /** Komma vor dem Sendernamen (nicht innerhalb von Anfuehrungszeichen). */
    static int lastTitleComma(String s) {
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') q = !q;
            else if (ch == ',' && !q) return i;
        }
        return -1;
    }
}
