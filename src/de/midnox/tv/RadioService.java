package de.midnox.tv;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.media.*;
import android.os.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Radio-Wiedergabe im Hintergrund: laeuft weiter auf der Startseite, pausiert wenn eine andere App Ton braucht. */
public class RadioService extends Service {
    static volatile RadioService inst;
    static final List<Runnable> listeners = new ArrayList<>();
    static void listen(Runnable r) { synchronized (listeners) { if (!listeners.contains(r)) listeners.add(r); } }
    static void unlisten(Runnable r) { synchronized (listeners) { listeners.remove(r); } }

    final Handler h = new Handler(Looper.getMainLooper());
    final Map<String, Bitmap> covers = Collections.synchronizedMap(new HashMap<String, Bitmap>());
    Stations.Station station;
    Radio.State state;
    final List<Radio.Song> localHistory = new ArrayList<>();
    Radio.Song current;
    MediaPlayer player;
    RadioNet.Proxy proxy;
    boolean playing, preparing;
    String error;
    int session;
    AudioManager am;
    MiniServer wishServer;
    String wishUrl;   // http://<ip>:<port>/ fuer den QR-Code
    AudioManager.OnAudioFocusChangeListener focus;

    static void start(Context c, String action) {
        Intent i = new Intent(c, RadioService.class);
        if (action != null) i.setAction(action);
        c.startService(i);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        Style.init(this);
        inst = this;
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        station = Stations.byId(Style.prefs(this).getString("radio_station", "midnox"));
        focus = new AudioManager.OnAudioFocusChangeListener() { public void onAudioFocusChange(int f) {
            if (f == AudioManager.AUDIOFOCUS_LOSS) stop();                                     // andere App spielt
            else if (f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || f == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) { if (player != null) player.setVolume(0.15f, 0.15f); }
            else if (f == AudioManager.AUDIOFOCUS_GAIN) { if (player != null) player.setVolume(1f, 1f); }
        }};
        h.post(poll);
        startWishes();
        changed();
    }

    // ------------------------------------------------------------ Musikwuensche per Handy (midnox Radio)
    String stationApi() { return Radio.api(this).replace("/api/nowplaying/", "/api/station/"); }

    void startWishes() {
        wishServer = new MiniServer(new MiniServer.Handler() { public String[] handle(String m, String path, String body) {
            if (path.startsWith("/wish") && m.equals("POST")) {
                try {
                    org.json.JSONObject o = new org.json.JSONObject(body);
                    final String name = o.optString("name").trim(), t = o.optString("title"), a = o.optString("artist");
                    h.post(new Runnable() { public void run() {
                        Style.toast(RadioService.this, "\u266A  " + (name.isEmpty() ? "Jemand" : name) + " w\u00fcnscht sich: " + t + (a.isEmpty() ? "" : " \u2013 " + a));
                    }});
                    log("Wunsch: " + name + " -> " + a + " - " + t);
                } catch (Exception ignored) {}
                return new String[]{"application/json", "{\"ok\":true}"};
            }
            return new String[]{"text/html", wishPage()};
        }});
        try { wishUrl = "http://" + SettingsActivity.ownIp() + ":" + wishServer.start(8120) + "/"; } catch (Exception e) { wishServer = null; }
    }

    String wishPage() {
        String api = stationApi();
        return MiniServer.page("midnox Radio \u2013 Musikwunsch",
            "<h1>midnox Radio</h1><p class=sub>W\u00fcnsch dir einen Song \u2013 er l\u00e4uft gleich im Radio und dein Wunsch erscheint auf dem Fernseher.</p>"
            + "<input id=n placeholder=\"Dein Name\" maxlength=24><input id=q placeholder=\"Song oder K\u00fcnstler suchen \u2026\" autocomplete=off>"
            + "<div id=m class=msg></div><div id=r></div>",
            "var api='" + api + "',t,n=document.getElementById('n'),q=document.getElementById('q'),r=document.getElementById('r'),m=document.getElementById('m');"
            + "try{n.value=localStorage.getItem('midnox_name')||''}catch(e){}"
            + "function esc(s){return String(s||'').replace(/[&<>\"]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]})}"
            + "function msg(s){m.style.display='block';m.textContent=s}"
            + "function load(){fetch(api+'/requests?rowCount=30&searchPhrase='+encodeURIComponent(q.value)).then(function(x){return x.json()}).then(function(d){"
            + "r.innerHTML='';(d.rows||d).forEach(function(it){var s=it.song,e=document.createElement('div');e.className='it';"
            + "e.innerHTML='<img src=\"'+esc(s.art)+'\"><div><b>'+esc(s.title)+'</b><span>'+esc(s.artist)+'</span></div><button>W\u00fcnschen</button>';"
            + "e.querySelector('button').onclick=function(){wish(it.request_id,s)};r.appendChild(e)})}).catch(function(){msg('Radio gerade nicht erreichbar.')})}"
            + "function wish(id,s){try{localStorage.setItem('midnox_name',n.value)}catch(e){}"
            + "fetch(api+'/request/'+id,{method:'POST'}).then(function(x){return x.json().then(function(j){return{ok:x.ok,j:j}})}).then(function(o){"
            + "if(o.ok&&o.j.success!==false){msg('\u2713 '+(o.j.formatted_message||o.j.message||'Wunsch angenommen!'));"
            + "fetch('/wish',{method:'POST',body:JSON.stringify({name:n.value,title:s.title,artist:s.artist,art:s.art})})}"
            + "else msg(o.j.formatted_message||o.j.message||'Wunsch nicht m\u00f6glich.')}).catch(function(){msg('Wunsch nicht m\u00f6glich.')})}"
            + "q.addEventListener('input',function(){clearTimeout(t);t=setTimeout(load,350)});load();");
    }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        String a = i == null ? null : i.getAction();
        if ("play".equals(a) && !playing && !preparing) play();
        else if ("toggle".equals(a)) toggle();
        else if ("stop".equals(a)) stop();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() { if (wishServer != null) wishServer.stop(); stop(); h.removeCallbacksAndMessages(null); inst = null; super.onDestroy(); }

    void changed() {
        h.post(new Runnable() { public void run() {
            List<Runnable> l; synchronized (listeners) { l = new ArrayList<>(listeners); }
            for (Runnable r : l) r.run();
        }});
    }

    void foreground(boolean on) {
        if (!on) { stopForeground(true); return; }
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel("radio", "midnox Radio", NotificationManager.IMPORTANCE_MIN));
                b = new Notification.Builder(this, "radio");
            } else b = new Notification.Builder(this);
            b.setContentTitle("midnox Radio").setContentText(station.name).setSmallIcon(R.drawable.icon);
            startForeground(43, b.build());
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------ Steuerung
    void switchTo(Stations.Station st) {
        if (st == station && (playing || preparing)) return;
        stop();
        station = st;
        Style.prefs(this).edit().putString("radio_station", st.id).apply();
        session++;
        state = null; current = null; localHistory.clear(); error = null;
        h.removeCallbacks(poll); h.post(poll);
        play();
    }

    void toggle() { if (playing || preparing) stop(); else play(); }

    void play() {
        if (preparing || playing) return;
        if (am.requestAudioFocus(focus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { error = "Ton ist gerade belegt"; changed(); return; }
        preparing = true; error = null; changed();
        foreground(true);
        final Stations.Station st = station;
        final int my = session;
        final String main = st.midnox() && state != null && state.stream != null ? state.stream : st.url;
        new Thread(new Runnable() { public void run() {
            String err = null, local = null;
            RadioNet.Proxy px = null;
            try {
                RadioNet.Resp r = RadioNet.open(main);
                int code = r.code; r.close();
                if (code != 200) err = "HTTP " + code + (code == 401 || code == 403 ? " – Sender lehnt ab" : "");
                else {
                    px = new RadioNet.Proxy(main);
                    if (!st.midnox()) px.listener = new RadioNet.Proxy.TitleListener() { public void title(String t) { onStreamTitle(t, my); } };
                    local = px.start();
                }
            } catch (Exception e) { err = e.getClass().getSimpleName() + ": " + e.getMessage(); }
            log(st.name + ": " + main + " -> " + (err == null ? "OK" : err) + (RadioNet.lastInfo.isEmpty() ? "" : "  (" + RadioNet.lastInfo + ")"));
            final String url = local, msg = err;
            final RadioNet.Proxy fpx = px;
            h.post(new Runnable() { public void run() {
                if (my != session || !preparing) { if (fpx != null) fpx.stop(); return; }
                if (url == null) { preparing = false; error = "Stream: " + msg; foreground(false); changed(); return; }
                proxy = fpx;
                startPlayer(url);
            }});
        }}).start();
    }

    void startPlayer(String url) {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(url);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { public void onPrepared(MediaPlayer mp) {
                preparing = false; playing = true; mp.start(); changed();
            }});
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() { public boolean onError(MediaPlayer mp, int what, int extra) {
                log("MediaPlayer-Fehler " + what + "/" + extra + (proxy != null && proxy.error != null ? " (" + proxy.error + ")" : ""));
                stop(); error = "Wiedergabefehler " + what + "/" + extra; changed(); return true;
            }});
            player.prepareAsync();
        } catch (Exception ex) { log("start: " + ex); stop(); error = "Wiedergabe nicht möglich"; changed(); }
    }

    void stop() {
        preparing = false; playing = false;
        if (player != null) { try { player.stop(); } catch (Exception ignored) {} player.release(); player = null; }
        if (proxy != null) { proxy.stop(); proxy = null; }
        try { am.abandonAudioFocus(focus); } catch (Exception ignored) {}
        foreground(false);
        changed();
    }

    /** Lautstaerke des Radios (0..1), z. B. fuers Ausblenden beim Einschlaf-Timer. */
    void volume(float v) { if (player != null) player.setVolume(v, v); }

    // ------------------------------------------------------------ Daten
    final Runnable poll = new Runnable() { public void run() {
        if (station.midnox()) {
            final int my = session;
            new Thread(new Runnable() { public void run() {
                try {
                    Radio.State st = Radio.fetch(RadioService.this);
                    List<String> arts = new ArrayList<>();
                    if (st.now.art != null) arts.add(st.now.art);
                    for (Radio.Song s : st.history) if (s.art != null) arts.add(s.art);
                    for (String a : arts) if (!covers.containsKey(a)) { Bitmap bm = RadioNet.bitmap(a); if (bm != null) covers.put(a, bm); }
                    if (my == session) { state = st; current = st.now; changed(); }
                } catch (Exception e) { log("API: " + e); }
            }}).start();
        }
        h.postDelayed(this, playing ? 10000 : 30000);
    }};

    /** Neuer Titel aus dem Stream (andere Sender): "Interpret - Titel". */
    void onStreamTitle(final String raw, final int my) {
        h.post(new Runnable() { public void run() {
            if (my != session || station.midnox() || raw.isEmpty()) return;
            String a = "", t = raw;
            int i = raw.indexOf(" - ");
            if (i > 0) { a = raw.substring(0, i).trim(); t = raw.substring(i + 3).trim(); }
            if (current != null && current.title.equals(t) && current.artist.equals(a)) return;
            if (current != null) { localHistory.add(0, current); while (localHistory.size() > 12) localHistory.remove(localHistory.size() - 1); }
            final Radio.Song s = new Radio.Song();
            s.title = t; s.artist = a; s.playedAt = System.currentTimeMillis() / 1000;
            current = s;
            changed();
            if (a.isEmpty()) return;
            new Thread(new Runnable() { public void run() {
                String url = RadioNet.coverUrl(s.artist, s.title);
                if (url == null) return;
                Bitmap bm = covers.containsKey(url) ? covers.get(url) : RadioNet.bitmap(url);
                if (bm == null) return;
                covers.put(url, bm);
                s.art = url;
                changed();
            }}).start();
        }});
    }

    List<Radio.Song> history() { return station.midnox() ? (state == null ? new ArrayList<Radio.Song>() : state.history) : localHistory; }

    void log(String s) {
        try {
            java.io.File f = new java.io.File(getExternalFilesDir(null), "radio_status.txt");
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write(new SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY).format(new Date()) + "  " + s + "\n");
            w.close();
        } catch (Exception ignored) {}
    }
}
