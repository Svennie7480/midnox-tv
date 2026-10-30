package de.midnox.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Detailseite: grosses Bild, Infos, Abspielen, Staffeln und Folgen. */
public class DetailActivity extends Activity implements MediaCard.Listener {
    final Handler h = new Handler(Looper.getMainLooper());
    Emby emby;
    Hero.Backdrop backdrop;
    TextView kind, title, meta, overview, status;
    LinearLayout buttons, seasonRow, episodeRow;
    HorizontalScrollView seasonSv, episodeSv;
    View seasonBox, episodeBox;
    String itemId, type, seriesId, seasonId, playId;
    final Map<String, Bitmap> imgs = new HashMap<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Style.init(this);
        Theme.current(this).apply();
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        itemId = getIntent().getStringExtra("id");
        type = getIntent().getStringExtra("type");
        seriesId = getIntent().getStringExtra("series");
        seasonId = getIntent().getStringExtra("season");

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new Style.Background());
        backdrop = new Hero.Backdrop(this);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(Style.px(64), Style.px(40), 0, 0);
        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.5f);
        kind = Style.text(this, "", 11, Style.MUTED); kind.setLetterSpacing(0.3f);
        title = Style.text(this, "", 30, 0xFFFFFFFF); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END); title.setMaxWidth(maxW);
        title.setPadding(0, Style.px(6), 0, Style.px(6));
        meta = Style.text(this, "", 12, 0xFFB4B8CC); meta.setMaxWidth(maxW);
        overview = Style.text(this, "", 12, 0xCCFFFFFF); overview.setMaxLines(4); overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setMaxWidth(maxW); overview.setLineSpacing(Style.px(3), 1f); overview.setPadding(0, Style.px(10), 0, 0);
        col.addView(kind); col.addView(title); col.addView(meta); col.addView(overview);

        buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        buttons.setPadding(0, Style.px(18), 0, Style.px(6));
        col.addView(buttons);
        status = Style.text(this, "Lade …", 12, Style.MUTED);
        col.addView(status);

        seasonBox = section("STAFFELN");
        seasonSv = (HorizontalScrollView) ((ViewGroup) seasonBox).getChildAt(1);
        seasonRow = (LinearLayout) seasonSv.getChildAt(0);
        episodeBox = section("FOLGEN");
        episodeSv = (HorizontalScrollView) ((ViewGroup) episodeBox).getChildAt(1);
        episodeRow = (LinearLayout) episodeSv.getChildAt(0);
        col.addView(seasonBox); col.addView(episodeBox);
        seasonBox.setVisibility(View.GONE); episodeBox.setVisibility(View.GONE);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        emby = Emby.from(this);
        if (emby == null) { status.setText("Emby ist nicht eingerichtet."); return; }
        load();
    }

    View section(String caption) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setPadding(0, Style.px(10), 0, 0);
        TextView cap = Style.text(this, caption, 11, Style.MUTED);
        cap.setLetterSpacing(0.3f);
        box.addView(cap);
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipChildren(false); sv.setClipToPadding(false);
        sv.setPadding(0, Style.px(8), Style.px(54), Style.px(8));
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        sv.addView(row);
        box.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    void load() {
        new Thread(new Runnable() { public void run() {
            try {
                final JSONObject item = emby.getObj("/emby/Users/" + emby.user + "/Items/" + itemId);
                final Emby.Entry e = emby.entry(item);
                emby.serverId();
                final Bitmap bd = Emby.fetch(e.backdrop);
                String sid = "Series".equals(e.type) ? e.id : e.seriesId;
                JSONArray seasons = null; String season = null; String nextLabel = null;
                playId = e.id;
                if (sid != null && ("Series".equals(e.type) || "Episode".equals(e.type))) {
                    seriesId = sid;
                    seasons = emby.getObj("/emby/Shows/" + sid + "/Seasons?UserId=" + emby.user).optJSONArray("Items");
                    if ("Episode".equals(e.type)) season = e.seasonId;
                    else {
                        JSONArray nu = emby.getObj("/emby/Shows/NextUp?UserId=" + emby.user + "&SeriesId=" + sid + "&Limit=1").optJSONArray("Items");
                        if (nu != null && nu.length() > 0) {
                            JSONObject n = nu.getJSONObject(0);
                            playId = n.getString("Id");
                            season = n.optString("SeasonId", null);
                            nextLabel = "S" + n.optInt("ParentIndexNumber") + " E" + n.optInt("IndexNumber");
                        }
                    }
                    if (season == null && seasons != null && seasons.length() > 0) season = seasons.getJSONObject(0).getString("Id");
                }
                final JSONArray fSeasons = seasons; final String fSeason = season; final String fNext = nextLabel;
                h.post(new Runnable() { public void run() { show(e, bd, fSeasons, fSeason, fNext); } });
            } catch (final Exception ex) {
                h.post(new Runnable() { public void run() { status.setText("Fehler beim Laden: " + ex.getClass().getSimpleName()); } });
            }
        }}).start();
    }

    void show(final Emby.Entry e, Bitmap bd, JSONArray seasons, String season, String nextLabel) {
        status.setVisibility(View.GONE);
        kind.setText(e.kind == null ? "" : e.kind);
        title.setText(e.title);
        meta.setText(e.meta == null ? "" : e.meta);
        overview.setText(e.overview == null ? "" : e.overview);
        backdrop.set(bd);
        buttons.removeAllViews();
        String playLabel = e.progress > 0 ? "▶  Fortsetzen" : "▶  Abspielen";
        if (nextLabel != null) playLabel = "▶  " + nextLabel + " abspielen";
        Pill play = new Pill(this, playLabel, new Runnable() { public void run() { Emby.open(DetailActivity.this, "play", emby.serverId(), playId); } });
        Pill open = new Pill(this, "In Emby öffnen", new Runnable() { public void run() { Emby.open(DetailActivity.this, "items", emby.serverId(), e.id); } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Style.px(14);
        buttons.addView(play, lp);
        buttons.addView(open, new LinearLayout.LayoutParams(-2, -2));
        play.requestFocus();

        if (seasons != null && seasons.length() > 0) {
            seasonBox.setVisibility(View.VISIBLE);
            seasonRow.removeAllViews();
            for (int i = 0; i < seasons.length(); i++) {
                final JSONObject s = seasons.optJSONObject(i);
                final String sid = s.optString("Id");
                Pill p = new Pill(this, s.optString("Name"), null);
                p.active = sid.equals(season);
                final Pill fp = p;
                p.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    for (int k = 0; k < seasonRow.getChildCount(); k++) ((Pill) seasonRow.getChildAt(k)).active = false;
                    fp.active = true;
                    for (int k = 0; k < seasonRow.getChildCount(); k++) seasonRow.getChildAt(k).invalidate();
                    loadEpisodes(sid, null);
                }});
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
                slp.setMargins(Style.px(6), 0, Style.px(6), 0);
                seasonRow.addView(p, slp);
            }
            if (season != null) loadEpisodes(season, "Episode".equals(e.type) ? e.id : playId);
        }
    }

    void loadEpisodes(final String season, final String focusId) {
        episodeBox.setVisibility(View.VISIBLE);
        new Thread(new Runnable() { public void run() {
            try {
                JSONArray arr = emby.getObj("/emby/Shows/" + seriesId + "/Episodes?UserId=" + emby.user + "&SeasonId=" + season + Emby.FIELDS).optJSONArray("Items");
                final List<Emby.Entry> eps = emby.entries(arr, 200);
                for (int i = 0; arr != null && i < arr.length() && i < eps.size(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Emby.Entry en = eps.get(i);
                    en.title = (o.has("IndexNumber") ? o.optInt("IndexNumber") + ". " : "") + o.optString("Name");
                    long ticks = o.optLong("RunTimeTicks", 0);
                    en.sub = ticks > 0 ? (ticks / 600000000L) + " Min." : "";
                    if (o.optJSONObject("ImageTags") != null && o.optJSONObject("ImageTags").has("Primary")) en.img = emby.img(en.id, "Primary", 360);
                    Bitmap b = imgs.get(en.img);
                    if (b == null) { b = Emby.fetch(en.img); if (b != null) imgs.put(en.img, b); }
                    en.bmp = b;
                }
                h.post(new Runnable() { public void run() {
                    episodeRow.removeAllViews();
                    MediaCard toFocus = null;
                    for (Emby.Entry en : eps) {
                        MediaCard mc = new MediaCard(DetailActivity.this, en, episodeSv, DetailActivity.this);
                        mc.selected = en.id.equals(focusId);
                        if (mc.selected) toFocus = mc;
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Style.px(168), Style.px(95));
                        lp.setMargins(Style.px(8), 0, Style.px(8), 0);
                        episodeRow.addView(mc, lp);
                    }
                    if (toFocus != null) {
                        final MediaCard f = toFocus;
                        episodeSv.post(new Runnable() { public void run() { episodeSv.scrollTo(Math.max(0, f.getLeft() - Style.px(40)), 0); } });
                    }
                }});
            } catch (Exception ignored) {}
        }}).start();
    }

    // Folgen-Karten
    @Override public void onFocus(MediaCard card) {}
    @Override public void onClick(MediaCard card) { Emby.open(this, "play", emby.serverId(), card.e.id); }
    @Override public void onLongClick(MediaCard card) { Emby.open(this, "items", emby.serverId(), card.e.id); }
}
