package de.midnox.quiz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.*;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** midnox Quiz – Die Million, Tages-Duell, Party mit Handys, weltweite Bestenliste. */
public class QuizActivity extends Activity {
    static final int HOME = 0, MILLION = 1, DAILY = 2, LOBBY = 3, PARTY = 4, BOARD = 5;
    final Handler h = new Handler(Looper.getMainLooper());
    Stage stage;
    int screen = HOME;
    ToneGenerator tone;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        U.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stage = new Stage(this);
        setContentView(stage);
        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 60); } catch (Exception ignored) {}
        new Thread(new Runnable() { public void run() {
            Bank.load(QuizActivity.this);
            h.post(new Runnable() { public void run() { stage.invalidate(); } });
            Bank.update(QuizActivity.this);
            Scores.flush(QuizActivity.this);
            h.post(new Runnable() { public void run() { stage.invalidate(); } });
        }}).start();
        h.post(tick);
    }
    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); Party.stop(); if (tone != null) tone.release(); super.onDestroy(); }

    final Runnable tick = new Runnable() { public void run() {
        stage.tick();
        h.postDelayed(this, 100);
    }};
    void beep(boolean ok) { try { if (tone != null) tone.startTone(ok ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_NACK, ok ? 180 : 400); } catch (Exception ignored) {} }

    String name() { return U.prefs(this).getString("name", ""); }
    void askName(final Runnable then) {
        final EditText et = new EditText(this);
        et.setSingleLine(); et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setText(name()); et.setSelection(et.getText().length());
        et.setHint("z. B. Sven");
        FrameLayout f = new FrameLayout(this); f.setPadding(U.px(20), U.px(6), U.px(20), 0); f.addView(et);
        final AlertDialog d = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Dein Name für die Bestenliste").setMessage("Wird weltweit angezeigt – 2 bis 16 Zeichen.").setView(f)
            .setPositiveButton("OK", null).setNegativeButton("Abbrechen", null).create();
        d.setOnShowListener(new DialogInterface.OnShowListener() { public void onShow(DialogInterface di) {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                String n = et.getText().toString().trim().replaceAll("\\s+", " ");
                if (n.length() < 2) { et.setError("Mindestens 2 Zeichen"); return; }
                if (n.length() > 16) n = n.substring(0, 16);
                U.prefs(QuizActivity.this).edit().putString("name", n).apply();
                d.dismiss(); stage.invalidate();
                if (then != null) then.run();
            }});
        }});
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return e.getKeyCode() == KeyEvent.KEYCODE_BACK || super.dispatchKeyEvent(e);
        if (stage.key(e.getKeyCode())) return true;
        return super.dispatchKeyEvent(e);
    }

    // ================================================================ Buehne
    final class Stage extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        final List<RectF> hitR = new ArrayList<>(); final List<Runnable> hitA = new ArrayList<>();
        float dp, W, H;

        Stage(Context c) { super(c); setFocusable(true); }

        void hit(RectF r, Runnable a) { hitR.add(new RectF(r)); hitA.add(a); }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            for (int i = hitR.size() - 1; i >= 0; i--) if (hitR.get(i).contains(e.getX(), e.getY())) { hitA.get(i).run(); invalidate(); return true; }
            return true;
        }

        // ------------------------------------------------------------ Zeichnen: Grundlagen
        void bg(Canvas c) {
            c.drawColor(U.BG);
            p.setShader(new RadialGradient(W * 0.12f, 0, W * 0.7f, 0x337C3AED, 0, Shader.TileMode.CLAMP)); c.drawRect(0, 0, W, H, p);
            p.setShader(new RadialGradient(W, H, W * 0.6f, 0x2206B6D4, 0, Shader.TileMode.CLAMP)); c.drawRect(0, 0, W, H, p);
            p.setShader(null);
        }
        void logo(Canvas c, float x, float y, float size) {
            t.setTypeface(U.BOLD); t.setTextSize(size); t.setShader(U.brand(x, x + t.measureText("midnox"))); t.setColor(Color.WHITE);
            c.drawText("midnox", x, y, t); float w = t.measureText("midnox "); t.setShader(null);
            t.setColor(Color.WHITE); c.drawText("Quiz", x + w, y, t);
        }
        void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
            t.setShader(null); t.setTypeface(bold ? U.BOLD : U.POP); t.setTextSize(size); t.setColor(color); c.drawText(s, x, y, t);
        }
        void centerText(Canvas c, String s, float cx, float y, float size, int color, boolean bold) {
            t.setShader(null); t.setTypeface(bold ? U.BOLD : U.POP); t.setTextSize(size); t.setColor(color); c.drawText(s, cx - t.measureText(s) / 2, y, t);
        }
        void card(Canvas c, RectF r, int color, float rad) { p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawRoundRect(r, rad, rad, p); }
        void focus(Canvas c, RectF r, float rad) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3 * dp); p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, rad, rad, p);
            p.setShader(null); p.setStyle(Paint.Style.FILL);
        }
        void pill(Canvas c, RectF r, String s, boolean sel) {
            if (sel) { p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, r.height() / 2, r.height() / 2, p); p.setShader(null); }
            else { p.setColor(0x22FFFFFF); c.drawRoundRect(r, r.height() / 2, r.height() / 2, p); }
            centerText(c, s, r.centerX(), r.centerY() + 6 * dp, 16 * dp, Color.WHITE, sel);
        }

        @Override protected void onDraw(Canvas c) {
            dp = U.dp; W = getWidth(); H = getHeight();
            hitR.clear(); hitA.clear();
            bg(c);
            if (Bank.all.isEmpty()) { centerText(c, "Lade Fragen …", W / 2, H / 2, 20 * dp, U.MUTED, false); return; }
            switch (screen) {
                case HOME: drawHome(c); break;
                case MILLION: drawMillion(c); break;
                case DAILY: drawDaily(c); break;
                case LOBBY: drawLobby(c); break;
                case PARTY: drawParty(c); break;
                case BOARD: drawBoard(c); break;
            }
        }
        void tick() {
            if (screen == MILLION) millionTick();
            else if (screen == DAILY) dailyTick();
            else if (screen == PARTY) partyTick();
            if (screen != HOME && screen != BOARD) invalidate();
            else if (screen == BOARD && boardLoading) invalidate();
        }

        boolean key(int k) {
            boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
            if (Bank.all.isEmpty()) { if (k == KeyEvent.KEYCODE_BACK) finish(); return true; }
            boolean r;
            switch (screen) {
                case HOME: r = homeKey(k, ok); break;
                case MILLION: r = millionKey(k, ok); break;
                case DAILY: r = dailyKey(k, ok); break;
                case LOBBY: r = lobbyKey(k, ok); break;
                case PARTY: r = partyKey(k, ok); break;
                case BOARD: r = boardKey(k, ok); break;
                default: r = false;
            }
            invalidate();
            return r;
        }
        void go(int s) { screen = s; invalidate(); }

        // ============================================================ Startseite
        int hf = 0;   // 0..3 Kacheln, 4 = Name
        final String[] tiles = {"Die Million", "Tages-Duell", "Party", "Bestenliste"};
        final String[] icons = {"€", "📅", "📱", "🏆"};
        void drawHome(Canvas c) {
            float pad = 48 * dp;
            logo(c, pad, 74 * dp, 40 * dp);
            String n = name();
            text(c, n.isEmpty() ? "Willkommen! Wähle ein Spiel." : "Hallo " + n + "! Wähle ein Spiel.", pad, 108 * dp, 18 * dp, U.MUTED, false);
            String today = Scores.today();
            JSONObject dres = dailyResult(today);
            String[] desc = {
                "15 Fragen bis zur Million. Drei Joker, zwei Sicherheitsstufen – und die weltweite Bestenliste.",
                dres != null ? "Heute schon gespielt: " + U.num(dres.optInt("s")) + " Punkte" + (dres.optInt("rank") > 0 ? " · Platz " + dres.optInt("rank") : "") + ". Morgen gibt’s neue Fragen."
                             : "10 Fragen – für alle Spieler weltweit dieselben. Schnell sein bringt Punkte. Ein Versuch pro Tag.",
                "Bis zu 12 Leute spielen mit dem Handy: QR-Code scannen, Namen eingeben, losraten.",
                "Wer hat heute die meisten Punkte? Wer hat die Million geknackt?"};
            float gap = 22 * dp, tw = (W - 2 * pad - 3 * gap) / 4, th = 250 * dp, ty = 150 * dp;
            for (int i = 0; i < 4; i++) {
                RectF r = new RectF(pad + i * (tw + gap), ty, pad + i * (tw + gap) + tw, ty + th);
                boolean f = hf == i;
                if (f) { float g = 6 * dp; r.inset(-g, -g); }
                card(c, r, f ? U.CARD2 : U.CARD, 22 * dp);
                if (f) focus(c, r, 22 * dp);
                // Symbol
                float cx = r.left + 30 * dp + 26 * dp, cy = r.top + 58 * dp;
                p.setShader(U.brand(cx - 28 * dp, cx + 28 * dp)); c.drawCircle(cx, cy, 28 * dp, p); p.setShader(null);
                centerText(c, icons[i], cx, cy + 10 * dp, 26 * dp, Color.WHITE, true);
                text(c, tiles[i], r.left + 30 * dp, r.top + 128 * dp, 24 * dp, Color.WHITE, true);
                t.setTypeface(U.POP); t.setTextSize(15 * dp); t.setColor(0xFFB4B8CC);
                U.multi(c, t, desc[i], r.left + 30 * dp, r.top + 160 * dp, r.width() - 56 * dp, 4, false);
                if (i == 1 && dres != null) { p.setColor(U.OK); c.drawCircle(r.right - 30 * dp, r.top + 34 * dp, 12 * dp, p); centerText(c, "✓", r.right - 30 * dp, r.top + 40 * dp, 16 * dp, Color.WHITE, true); }
                final int idx = i; hit(r, new Runnable() { public void run() { hf = idx; open(idx); } });
            }
            RectF nr = new RectF(pad, ty + th + 34 * dp, pad + 360 * dp, ty + th + 80 * dp);
            card(c, nr, hf == 4 ? U.CARD2 : U.CARD, 23 * dp);
            if (hf == 4) focus(c, nr, 23 * dp);
            text(c, "✎   Name: " + (n.isEmpty() ? "noch keiner" : n), nr.left + 22 * dp, nr.centerY() + 6 * dp, 17 * dp, Color.WHITE, hf == 4);
            hit(nr, new Runnable() { public void run() { hf = 4; askName(null); } });
            text(c, Bank.all.size() + " Fragen in " + Bank.cats.size() + " Kategorien  ·  Bestenliste auch im Browser: " + Scores.WEB, pad, H - 34 * dp, 14 * dp, U.MUTED, false);
        }
        boolean homeKey(int k, boolean ok) {
            if (k == KeyEvent.KEYCODE_BACK) { finish(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && hf > 0 && hf < 4) hf--;
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && hf < 3) hf++;
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) hf = 4;
            else if (k == KeyEvent.KEYCODE_DPAD_UP && hf == 4) hf = 0;
            else if (ok) { if (hf == 4) askName(null); else open(hf); }
            return true;
        }
        void open(final int i) {
            if ((i == 0 || i == 1) && name().length() < 2) { askName(new Runnable() { public void run() { open(i); } }); return; }
            if (i == 0) startMillion();
            else if (i == 1) startDaily();
            else if (i == 2) openLobby();
            else openBoard(0);
        }

        // ============================================================ Die Million
        List<Bank.Q> mq; int mi, msel, mrow, mjf; // mrow 0 = Antworten, 1 = Joker-Leiste
        boolean[] mHidden = new boolean[4], mJoker = new boolean[3];
        int[] mAudience; String mPhone; int mPhoneAns = -1;
        int mState; long mStateAt, mStart, mEndSec; int mWon; String mEndMsg = ""; JSONObject mResult; boolean mQuit;
        static final int M_ASK = 0, M_LOCK = 1, M_RIGHT = 2, M_WRONG = 3, M_END = 4, M_CONFIRMQUIT = 5;

        void startMillion() {
            mq = Bank.million(QuizActivity.this); mi = 0; msel = 0; mrow = 0; mjf = 0;
            mJoker = new boolean[3]; mStart = System.currentTimeMillis(); mResult = null; mQuit = false;
            newQuestion();
            go(MILLION);
        }
        void newQuestion() { mHidden = new boolean[4]; mAudience = null; mPhone = null; mPhoneAns = -1; mState = M_ASK; msel = 0; mrow = 0; Bank.markSeen(QuizActivity.this, mq.get(mi).id); }
        int safety(int answered) { return answered >= 10 ? Scores.LADDER[10] : answered >= 5 ? Scores.LADDER[5] : 0; }
        void millionTick() {
            long now = System.currentTimeMillis();
            if (mState == M_LOCK && now - mStateAt > 1800) {
                boolean right = msel == mq.get(mi).correct;
                beep(right);
                mState = right ? M_RIGHT : M_WRONG; mStateAt = now;
            } else if (mState == M_RIGHT && now - mStateAt > 1600) {
                if (mi == 14) endMillion(Scores.LADDER[15], "Du hast die Million geknackt!");
                else { mi++; newQuestion(); }
            } else if (mState == M_WRONG && now - mStateAt > 2600) {
                endMillion(safety(mi), "Leider falsch.");
            }
        }
        void endMillion(int won, String msg) {
            mWon = won; mEndMsg = msg; mState = M_END; mjf = 0; mEndSec = (System.currentTimeMillis() - mStart) / 1000; mResult = null;
            final int w = won; final long s = Math.max(1, mEndSec);
            new Thread(new Runnable() { public void run() {
                final JSONObject r = Scores.submit(QuizActivity.this, "million", name(), w, s, null);
                h.post(new Runnable() { public void run() { mResult = r; invalidate(); } });
            }}).start();
        }
        void useJoker(int j) {
            if (mJoker[j] || mState != M_ASK) return;
            Bank.Q q = mq.get(mi); Random rnd = new Random();
            mJoker[j] = true;
            if (j == 0) {   // 50:50
                List<Integer> wrong = new ArrayList<>(); for (int i = 0; i < 4; i++) if (i != q.correct) wrong.add(i);
                Collections.shuffle(wrong, rnd); mHidden[wrong.get(0)] = true; mHidden[wrong.get(1)] = true;
                if (mHidden[msel]) msel = q.correct == msel ? msel : firstVisible();
            } else if (j == 1) {   // Publikum
                int[] base = {0, 58, 44, 32}; int spread = 22;
                int corr = base[Math.max(1, Math.min(3, q.level))] + rnd.nextInt(spread);
                int[] v = new int[4]; v[q.correct] = corr; int rest = 100 - corr;
                List<Integer> others = new ArrayList<>(); for (int i = 0; i < 4; i++) if (i != q.correct && !mHidden[i]) others.add(i);
                for (int k = 0; k < others.size(); k++) { int part = k == others.size() - 1 ? rest : rnd.nextInt(rest + 1); v[others.get(k)] = part; rest -= part; }
                if (others.isEmpty()) v[q.correct] = 100;
                // gelegentlich liegt das Publikum bei schweren Fragen daneben
                if (q.level == 3 && rnd.nextInt(5) == 0 && !others.isEmpty()) { int o = others.get(0); int tmp = v[o]; v[o] = v[q.correct]; v[q.correct] = tmp; }
                mAudience = v;
            } else {   // Telefon
                double chance = q.level == 1 ? 0.92 : q.level == 2 ? 0.78 : 0.58;
                int ans = q.correct;
                if (rnd.nextDouble() > chance) { List<Integer> o = new ArrayList<>(); for (int i = 0; i < 4; i++) if (i != q.correct && !mHidden[i]) o.add(i); if (!o.isEmpty()) ans = o.get(rnd.nextInt(o.size())); }
                int conf = ans == q.correct ? 70 + rnd.nextInt(30) : 40 + rnd.nextInt(35);
                String[] who = {"Tante Erika", "dein Kumpel Jens", "Opa Heinz", "deine Kollegin Anja", "Nachbar Klaus"};
                mPhoneAns = ans;
                mPhone = who[rnd.nextInt(who.length)] + ": „" + (conf > 85 ? "Da bin ich mir ganz sicher" : conf > 65 ? "Ich glaube" : "Puh, ich rate mal") + " – " + "ABCD".charAt(ans) + ": " + q.answers[ans] + ". (" + conf + " %)“";
            }
            mrow = 0;
        }
        int firstVisible() { for (int i = 0; i < 4; i++) if (!mHidden[i]) return i; return 0; }
        boolean millionKey(int k, boolean ok) {
            if (mState == M_END) {
                if (k == KeyEvent.KEYCODE_BACK) { go(HOME); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) mjf = Math.max(0, mjf - 1);
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) mjf = Math.min(2, mjf + 1);
                else if (ok) { if (mjf == 0) startMillion(); else if (mjf == 1) openBoard(1); else go(HOME); }
                return true;
            }
            if (mState == M_CONFIRMQUIT) {
                if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) mjf = 1 - mjf;
                else if (ok) { if (mjf == 0) { mQuit = true; endMillion(Scores.LADDER[mi], "Ausgestiegen – kluge Entscheidung?"); } else mState = M_ASK; }
                else if (k == KeyEvent.KEYCODE_BACK) mState = M_ASK;
                return true;
            }
            if (k == KeyEvent.KEYCODE_BACK) { if (mState == M_ASK) { mState = M_CONFIRMQUIT; mjf = 1; } return true; }
            if (mState != M_ASK) return true;
            if (mrow == 0) {
                int col = msel % 2, row = msel / 2;
                if (k == KeyEvent.KEYCODE_DPAD_LEFT && col == 1) moveSel(msel - 1);
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && col == 0) moveSel(msel + 1);
                else if (k == KeyEvent.KEYCODE_DPAD_UP) { if (row == 1) moveSel(msel - 2); else { mrow = 1; mjf = 0; } }
                else if (k == KeyEvent.KEYCODE_DPAD_DOWN && row == 0) moveSel(msel + 2);
                else if (ok && !mHidden[msel]) { mState = M_LOCK; mStateAt = System.currentTimeMillis(); }
            } else {
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) mjf = Math.max(0, mjf - 1);
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) mjf = Math.min(3, mjf + 1);
                else if (k == KeyEvent.KEYCODE_DPAD_DOWN) mrow = 0;
                else if (ok) { if (mjf < 3) useJoker(mjf); else { mState = M_CONFIRMQUIT; mjf = 1; } }
            }
            return true;
        }
        void moveSel(int to) { if (to >= 0 && to < 4) msel = to; }

        void drawMillion(Canvas c) {
            float pad = 40 * dp, ladderW = 250 * dp;
            float lx = W - pad - ladderW;
            // Leiter
            RectF lr = new RectF(lx, 40 * dp, W - pad, H - 40 * dp);
            card(c, lr, 0xCC15141F, 20 * dp);
            float rh = (lr.height() - 24 * dp) / 15;
            for (int i = 15; i >= 1; i--) {
                float y = lr.top + 12 * dp + (15 - i) * rh;
                boolean cur = mState != M_END && i == mi + 1, done = i <= (mState == M_END ? -1 : mi);
                RectF rr = new RectF(lr.left + 10 * dp, y + 1 * dp, lr.right - 10 * dp, y + rh - 1 * dp);
                if (cur) { p.setShader(U.brand(rr.left, rr.right)); c.drawRoundRect(rr, 10 * dp, 10 * dp, p); p.setShader(null); }
                boolean safe = i == 5 || i == 10 || i == 15;
                int col = cur ? Color.WHITE : done ? U.OK : safe ? U.GOLD : 0xFFC9CCE0;
                text(c, String.valueOf(i), rr.left + 12 * dp, rr.centerY() + 5 * dp, 14 * dp, cur ? Color.WHITE : U.MUTED, false);
                t.setTypeface(safe || cur ? U.BOLD : U.POP); t.setTextSize(15 * dp); t.setColor(col);
                String m = U.money(Scores.LADDER[i]); c.drawText(m, rr.right - 12 * dp - t.measureText(m), rr.centerY() + 5 * dp, t);
            }
            float aw = lx - pad - 24 * dp;
            if (mState == M_END) { drawMillionEnd(c, pad, aw); return; }
            Bank.Q q = mq.get(mi);
            // Kopf: Joker + Ausstieg
            String[] jl = {"50:50", "👥 Publikum", "📞 Telefon", "Aussteigen"};
            float jx = pad, jy = 36 * dp;
            for (int j = 0; j < 4; j++) {
                t.setTypeface(U.BOLD); t.setTextSize(16 * dp);
                float w = t.measureText(jl[j]) + 40 * dp;
                RectF r = new RectF(jx, jy, jx + w, jy + 44 * dp);
                boolean used = j < 3 && mJoker[j], f = mrow == 1 && mjf == j && mState == M_ASK;
                if (f) { p.setShader(U.brand(r.left, r.right)); c.drawRoundRect(r, 22 * dp, 22 * dp, p); p.setShader(null); }
                else { p.setColor(used ? 0x11FFFFFF : 0x26FFFFFF); c.drawRoundRect(r, 22 * dp, 22 * dp, p); }
                centerText(c, jl[j], r.centerX(), r.centerY() + 6 * dp, 16 * dp, used ? 0x55FFFFFF : Color.WHITE, true);
                if (used) { p.setColor(0x88EF4444); p.setStrokeWidth(3 * dp); c.drawLine(r.left + 14 * dp, r.centerY(), r.right - 14 * dp, r.centerY(), p); }
                final int jj = j; hit(r, new Runnable() { public void run() { if (mState != M_ASK) return; if (jj < 3) useJoker(jj); else { mState = M_CONFIRMQUIT; mjf = 1; } } });
                jx += w + 12 * dp;
            }
            long el = (System.currentTimeMillis() - mStart) / 1000;
            t.setTypeface(U.POP); t.setTextSize(15 * dp); t.setColor(U.MUTED);
            String info = "Frage " + (mi + 1) + " von 15  ·  " + U.dur(el);
            c.drawText(info, pad + aw - t.measureText(info), jy + 28 * dp, t);
            // Hilfe-Einblendung (Publikum / Telefon)
            float qy = 112 * dp;
            if (mAudience != null || mPhone != null) {
                RectF hr = new RectF(pad, qy - 8 * dp, pad + aw, qy + 92 * dp);
                card(c, hr, 0xCC1E1C2C, 16 * dp);
                if (mAudience != null) {
                    float bw = (aw - 80 * dp) / 4;
                    for (int i = 0; i < 4; i++) {
                        float bx = hr.left + 24 * dp + i * (bw + 10 * dp), bh = 54 * dp * mAudience[i] / 100f;
                        p.setColor(0x22FFFFFF); c.drawRoundRect(new RectF(bx, hr.top + 12 * dp, bx + bw, hr.top + 66 * dp), 6 * dp, 6 * dp, p);
                        p.setShader(U.brand(bx, bx + bw)); c.drawRoundRect(new RectF(bx, hr.top + 66 * dp - bh, bx + bw, hr.top + 66 * dp), 6 * dp, 6 * dp, p); p.setShader(null);
                        centerText(c, "ABCD".charAt(i) + "  " + mAudience[i] + " %", bx + bw / 2, hr.top + 88 * dp, 15 * dp, Color.WHITE, true);
                    }
                } else {
                    t.setTypeface(U.POP); t.setTextSize(19 * dp); t.setColor(Color.WHITE);
                    U.multi(c, t, "📞  " + mPhone, hr.left + 24 * dp, hr.top + 40 * dp, hr.width() - 48 * dp, 2, false);
                }
                qy += 108 * dp;
            }
            // Frage
            float qh = mAudience != null || mPhone != null ? 120 * dp : 190 * dp;
            RectF qr = new RectF(pad, qy, pad + aw, qy + qh);
            card(c, qr, 0xE615141F, 22 * dp);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2 * dp); p.setShader(U.brand(qr.left, qr.right)); c.drawRoundRect(qr, 22 * dp, 22 * dp, p); p.setShader(null); p.setStyle(Paint.Style.FILL);
            text(c, (Bank.cats.containsKey(q.cat) ? Bank.cats.get(q.cat) : q.cat).toUpperCase(Locale.GERMAN) + "  ·  " + U.money(Scores.LADDER[mi + 1]), qr.left + 28 * dp, qr.top + 30 * dp, 13 * dp, 0xFF9EC5FF, true);
            t.setTypeface(U.BOLD); t.setColor(Color.WHITE);
            U.fit(t, q.text, qr.width() - 56 * dp, mAudience != null || mPhone != null ? 2 : 3, 30 * dp, 18 * dp);
            float lh = t.getTextSize() * 1.28f; int lines = Math.min(3, U.wrap(t, q.text, qr.width() - 56 * dp).size());
            float ty0 = qr.top + 30 * dp + (qr.height() - 30 * dp - lines * lh) / 2 + t.getTextSize();
            U.multi(c, t, q.text, qr.left + 28 * dp, ty0, qr.width() - 56 * dp, 3, false);
            // Antworten
            float ay = qr.bottom + 22 * dp, gap = 18 * dp, bw = (aw - gap) / 2, bh = Math.min(96 * dp, (H - 40 * dp - ay - gap) / 2);
            for (int i = 0; i < 4; i++) {
                RectF r = new RectF(pad + (i % 2) * (bw + gap), ay + (i / 2) * (bh + gap), pad + (i % 2) * (bw + gap) + bw, ay + (i / 2) * (bh + gap) + bh);
                drawAnswer(c, r, i, q, true);
                final int ii = i;
                hit(r, new Runnable() { public void run() { if (mState != M_ASK || mHidden[ii]) return; if (msel == ii && mrow == 0) { mState = M_LOCK; mStateAt = System.currentTimeMillis(); } else { msel = ii; mrow = 0; } } });
            }
            if (mState == M_CONFIRMQUIT) drawQuitConfirm(c);
        }
        void drawAnswer(Canvas c, RectF r, int i, Bank.Q q, boolean solo) {
            boolean hidden = mHidden[i];
            boolean sel = msel == i && mrow == 0;
            int fill = U.CARD;
            if (mState == M_LOCK && msel == i) fill = U.LOCK;
            if ((mState == M_RIGHT || mState == M_WRONG) && i == q.correct) fill = U.OK;
            if (mState == M_WRONG && msel == i) fill = U.BAD;
            if (hidden) { card(c, r, 0x10FFFFFF, r.height() / 2); return; }
            card(c, r, fill, r.height() / 2);
            if (sel && mState == M_ASK) focus(c, r, r.height() / 2);
            float cx = r.left + 36 * dp;
            p.setColor(fill == U.CARD ? 0x33FFFFFF : 0x44000000); c.drawCircle(cx, r.centerY(), 20 * dp, p);
            centerText(c, String.valueOf("ABCD".charAt(i)), cx, r.centerY() + 7 * dp, 19 * dp, Color.WHITE, true);
            t.setTypeface(U.BOLD); t.setColor(Color.WHITE);
            U.fit(t, q.answers[i], r.width() - 90 * dp, 2, 21 * dp, 13 * dp);
            int lines = Math.min(2, U.wrap(t, q.answers[i], r.width() - 90 * dp).size());
            float lh = t.getTextSize() * 1.28f;
            U.multi(c, t, q.answers[i], r.left + 72 * dp, r.centerY() - (lines * lh) / 2 + t.getTextSize() * 0.95f, r.width() - 90 * dp, 2, false);
            if (mPhoneAns == i && mState == M_ASK) text(c, "📞", r.right - 40 * dp, r.top + 26 * dp, 16 * dp, Color.WHITE, false);
        }
        void drawQuitConfirm(Canvas c) {
            p.setColor(0xCC000000); c.drawRect(0, 0, W, H, p);
            RectF r = new RectF(W / 2 - 300 * dp, H / 2 - 120 * dp, W / 2 + 300 * dp, H / 2 + 120 * dp);
            card(c, r, U.CARD2, 24 * dp);
            centerText(c, "Aussteigen?", r.centerX(), r.top + 56 * dp, 28 * dp, Color.WHITE, true);
            centerText(c, "Du nimmst " + U.money(Scores.LADDER[mi]) + " mit nach Hause.", r.centerX(), r.top + 96 * dp, 18 * dp, 0xFFC9CCE0, false);
            String[] b = {"Ja, aussteigen", "Weiterspielen"};
            for (int i = 0; i < 2; i++) {
                RectF br = new RectF(r.centerX() - 250 * dp + i * 260 * dp, r.bottom - 76 * dp, r.centerX() - 10 * dp + i * 260 * dp, r.bottom - 28 * dp);
                pill(c, br, b[i], mjf == i);
                final int ii = i; hit(br, new Runnable() { public void run() { if (ii == 0) { mQuit = true; endMillion(Scores.LADDER[mi], "Ausgestiegen – kluge Entscheidung?"); } else mState = M_ASK; } });
            }
        }
        void drawMillionEnd(Canvas c, float pad, float aw) {
            float y = 120 * dp;
            centerText(c, mWon >= 1000000 ? "🏆" : mWon > 0 ? "🎉" : "😬", pad + aw / 2, y, 60 * dp, Color.WHITE, false);
            centerText(c, mEndMsg, pad + aw / 2, y + 60 * dp, 22 * dp, 0xFFC9CCE0, false);
            t.setTypeface(U.BOLD); t.setTextSize(64 * dp);
            String m = U.money(mWon); float mw = t.measureText(m);
            t.setShader(U.brand(pad + aw / 2 - mw / 2, pad + aw / 2 + mw / 2)); c.drawText(m, pad + aw / 2 - mw / 2, y + 150 * dp, t); t.setShader(null);
            if (!mQuit && mState == M_END && mi < 15 && mEndMsg.startsWith("Leider")) {
                Bank.Q q = mq.get(mi);
                centerText(c, "Richtig gewesen: " + q.answers[q.correct], pad + aw / 2, y + 196 * dp, 17 * dp, U.MUTED, false);
            }
            String res = mResult == null ? "Trage in die Bestenliste ein …" : mResult.optBoolean("ok") ? (mResult.optInt("best") > mWon ? "Dein Bestwert " + U.money(mResult.optInt("best")) + " steht auf Platz " + mResult.optInt("rank") : "Weltweit Platz " + mResult.optInt("rank") + "  ·  Zeit " + U.dur(mEndSec))
                : mResult.optString("msg", "Bestenliste gerade nicht erreichbar");
            centerText(c, res, pad + aw / 2, y + 236 * dp, 20 * dp, Color.WHITE, true);
            String[] b = {"Nochmal", "Bestenliste", "Menü"};
            float bw = 190 * dp, total = 3 * bw + 2 * 16 * dp, bx = pad + aw / 2 - total / 2;
            for (int i = 0; i < 3; i++) {
                RectF br = new RectF(bx + i * (bw + 16 * dp), y + 270 * dp, bx + i * (bw + 16 * dp) + bw, y + 320 * dp);
                pill(c, br, b[i], mjf == i);
                final int ii = i; hit(br, new Runnable() { public void run() { if (ii == 0) startMillion(); else if (ii == 1) openBoard(1); else go(HOME); } });
            }
        }

        // ============================================================ Tages-Duell
        List<Bank.Q> dq; int di, dsel, dState; long dStart, dStateAt; int dScore, dCorrect; long dTotalMs; int[] dGain; JSONObject dRes; String dDay; int dFocus;
        static final int D_ASK = 0, D_SHOW = 1, D_END = 2;
        static final long D_MS = 20000;
        JSONObject dailyResult(String day) {
            try { String s = U.prefs(QuizActivity.this).getString("daily_" + day, ""); return s.isEmpty() ? null : new JSONObject(s); } catch (Exception e) { return null; }
        }
        void startDaily() {
            dDay = Scores.today();
            JSONObject done = dailyResult(dDay);
            dq = Bank.daily(dDay);
            if (done != null) { dScore = done.optInt("s"); dCorrect = done.optInt("c"); dTotalMs = done.optLong("t") * 1000; dRes = done.has("rank") ? done : null; dState = D_END; dFocus = 0; dGain = null; go(DAILY); return; }
            di = 0; dScore = 0; dCorrect = 0; dTotalMs = 0; dGain = new int[dq.size()]; dRes = null; dFocus = 0;
            // sofort sperren: Abbrechen zaehlt als gespielt (sonst koennte man die Fragen vorab ansehen)
            saveDaily(false);
            dNext(true);
            go(DAILY);
        }
        void dNext(boolean first) { if (!first) di++; dsel = 0; dState = D_ASK; dStart = System.currentTimeMillis(); }
        void saveDaily(boolean fin) {
            try {
                JSONObject o = new JSONObject().put("s", dScore).put("c", dCorrect).put("t", Math.max(1, dTotalMs / 1000)).put("fin", fin);
                if (dRes != null && dRes.optBoolean("ok")) o.put("rank", dRes.optInt("rank")).put("players", dRes.optInt("players"));
                U.prefs(QuizActivity.this).edit().putString("daily_" + dDay, o.toString()).apply();
            } catch (Exception ignored) {}
        }
        void dailyAnswer(int a) {
            if (dState != D_ASK) return;
            long t = Math.min(D_MS, System.currentTimeMillis() - dStart);
            dTotalMs += t; dsel = a;
            Bank.Q q = dq.get(di);
            boolean ok = a == q.correct;
            int g = ok ? 500 + (int) Math.round(500.0 * (D_MS - t) / D_MS) : 0;
            dGain[di] = g; dScore += g; if (ok) dCorrect++;
            beep(ok);
            dState = D_SHOW; dStateAt = System.currentTimeMillis();
            saveDaily(false);
        }
        void dailyTick() {
            if (dState == D_ASK && System.currentTimeMillis() - dStart >= D_MS) dailyAnswer(-1);
            else if (dState == D_SHOW && System.currentTimeMillis() - dStateAt > 1800) {
                if (di + 1 >= dq.size()) {
                    dState = D_END; saveDaily(true);
                    final int s = dScore; final long sec = Math.max(10, dTotalMs / 1000);
                    new Thread(new Runnable() { public void run() {
                        final JSONObject r = Scores.submit(QuizActivity.this, "daily", name(), s, sec, dDay);
                        h.post(new Runnable() { public void run() { dRes = r; saveDaily(true); invalidate(); } });
                    }}).start();
                } else dNext(false);
            }
        }
        boolean dailyKey(int k, boolean ok) {
            if (dState == D_END) {
                if (k == KeyEvent.KEYCODE_BACK) { go(HOME); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) dFocus = 1 - dFocus;
                else if (ok) { if (dFocus == 0) openBoard(0); else go(HOME); }
                return true;
            }
            if (k == KeyEvent.KEYCODE_BACK) { go(HOME); return true; }   // zaehlt als gespielt
            if (dState != D_ASK) return true;
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && dsel % 2 == 1) dsel--;
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && dsel % 2 == 0) dsel++;
            else if (k == KeyEvent.KEYCODE_DPAD_UP && dsel >= 2) dsel -= 2;
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN && dsel < 2) dsel += 2;
            else if (ok) dailyAnswer(dsel);
            return true;
        }
        void drawDaily(Canvas c) {
            float pad = 48 * dp;
            logo(c, pad, 60 * dp, 26 * dp);
            text(c, "Tages-Duell · " + dDay.substring(8) + "." + dDay.substring(5, 7) + ".", pad + 190 * dp, 60 * dp, 20 * dp, U.MUTED, false);
            t.setTypeface(U.BOLD); t.setTextSize(22 * dp); t.setColor(Color.WHITE);
            String sc = U.num(dScore) + " Punkte"; c.drawText(sc, W - pad - t.measureText(sc), 60 * dp, t);
            if (dState == D_END) { drawDailyEnd(c, pad); return; }
            Bank.Q q = dq.get(di);
            // Fortschritt
            float px = pad, py = 86 * dp, pw = (W - 2 * pad - 9 * 8 * dp) / 10;
            for (int i = 0; i < dq.size(); i++) {
                RectF r = new RectF(px + i * (pw + 8 * dp), py, px + i * (pw + 8 * dp) + pw, py + 6 * dp);
                int col = i < di || (i == di && dState == D_SHOW) ? (dGain[i] > 0 ? U.OK : U.BAD) : i == di ? Color.WHITE : 0x22FFFFFF;
                card(c, r, col, 3 * dp);
            }
            // Zeit
            long left = dState == D_ASK ? Math.max(0, D_MS - (System.currentTimeMillis() - dStart)) : 0;
            RectF tb = new RectF(pad, py + 22 * dp, W - pad, py + 32 * dp);
            card(c, tb, 0x22FFFFFF, 5 * dp);
            if (left > 0) { RectF f = new RectF(tb.left, tb.top, tb.left + tb.width() * left / D_MS, tb.bottom); p.setShader(U.brand(f.left, tb.right)); c.drawRoundRect(f, 5 * dp, 5 * dp, p); p.setShader(null); }
            // Frage
            RectF qr = new RectF(pad, py + 52 * dp, W - pad, py + 232 * dp);
            card(c, qr, 0xE615141F, 22 * dp);
            text(c, "FRAGE " + (di + 1) + " / " + dq.size() + "  ·  " + (Bank.cats.containsKey(q.cat) ? Bank.cats.get(q.cat) : q.cat).toUpperCase(Locale.GERMAN) + (dState == D_ASK ? "  ·  " + ((left + 999) / 1000) + " s" : ""), qr.left + 28 * dp, qr.top + 32 * dp, 13 * dp, 0xFF9EC5FF, true);
            t.setTypeface(U.BOLD); t.setColor(Color.WHITE);
            U.fit(t, q.text, qr.width() - 56 * dp, 3, 32 * dp, 18 * dp);
            int lines = Math.min(3, U.wrap(t, q.text, qr.width() - 56 * dp).size()); float lh = t.getTextSize() * 1.28f;
            U.multi(c, t, q.text, qr.left + 28 * dp, qr.top + 32 * dp + (qr.height() - 32 * dp - lines * lh) / 2 + t.getTextSize(), qr.width() - 56 * dp, 3, false);
            float ay = qr.bottom + 22 * dp, gap = 18 * dp, bw = (W - 2 * pad - gap) / 2, bh = Math.min(100 * dp, (H - 30 * dp - ay - gap) / 2);
            for (int i = 0; i < 4; i++) {
                RectF r = new RectF(pad + (i % 2) * (bw + gap), ay + (i / 2) * (bh + gap), pad + (i % 2) * (bw + gap) + bw, ay + (i / 2) * (bh + gap) + bh);
                int fill = U.ANS[i];
                if (dState == D_SHOW) fill = i == q.correct ? U.OK : i == dsel ? U.BAD : 0xFF23212F;
                card(c, r, fill, 22 * dp);
                if (dState == D_ASK && dsel == i) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5 * dp); p.setColor(Color.WHITE); c.drawRoundRect(r, 22 * dp, 22 * dp, p); p.setStyle(Paint.Style.FILL); }
                text(c, U.SHAPE[i], r.left + 24 * dp, r.centerY() + 10 * dp, 26 * dp, 0xCCFFFFFF, true);
                t.setTypeface(U.BOLD); t.setColor(Color.WHITE);
                U.fit(t, q.answers[i], r.width() - 90 * dp, 2, 22 * dp, 13 * dp);
                int ln = Math.min(2, U.wrap(t, q.answers[i], r.width() - 90 * dp).size()); float l2 = t.getTextSize() * 1.28f;
                U.multi(c, t, q.answers[i], r.left + 70 * dp, r.centerY() - ln * l2 / 2 + t.getTextSize() * 0.95f, r.width() - 90 * dp, 2, false);
                if (dState == D_SHOW && i == dsel && dGain[di] > 0) { t.setTextSize(20 * dp); String g = "+" + dGain[di]; c.drawText(g, r.right - 24 * dp - t.measureText(g), r.top + 32 * dp, t); }
                final int ii = i; hit(r, new Runnable() { public void run() { if (dsel == ii) dailyAnswer(ii); else dsel = ii; } });
            }
        }
        void drawDailyEnd(Canvas c, float pad) {
            float cx = W / 2, y = 150 * dp;
            centerText(c, "Geschafft!", cx, y, 30 * dp, Color.WHITE, true);
            t.setTypeface(U.BOLD); t.setTextSize(70 * dp);
            String s = U.num(dScore); float sw = t.measureText(s);
            t.setShader(U.brand(cx - sw / 2, cx + sw / 2)); c.drawText(s, cx - sw / 2, y + 90 * dp, t); t.setShader(null);
            centerText(c, dCorrect + " von " + dq.size() + " richtig  ·  Zeit " + U.dur(Math.max(1, dTotalMs / 1000)), cx, y + 130 * dp, 19 * dp, 0xFFC9CCE0, false);
            JSONObject done = dailyResult(dDay);
            String r;
            if (dRes != null && dRes.optBoolean("ok")) r = "Heute weltweit Platz " + dRes.optInt("rank") + " von " + dRes.optInt("players");
            else if (dRes != null && dRes.optBoolean("already")) r = "Für heute ist schon ein Ergebnis eingetragen";
            else if (dRes != null) r = dRes.optString("msg", "Bestenliste nicht erreichbar");
            else if (done != null && done.optInt("rank") > 0) r = "Heute weltweit Platz " + done.optInt("rank");
            else if (done != null && done.optBoolean("fin")) r = "Wird eingetragen …";
            else r = "Abgebrochen – zählt für heute.";
            centerText(c, r, cx, y + 178 * dp, 22 * dp, Color.WHITE, true);
            centerText(c, "Morgen gibt es 10 neue Fragen – für alle gleich.", cx, y + 214 * dp, 16 * dp, U.MUTED, false);
            String[] b = {"Tages-Bestenliste", "Menü"};
            for (int i = 0; i < 2; i++) {
                RectF br = new RectF(cx - 260 * dp + i * 270 * dp, y + 250 * dp, cx - 10 * dp + i * 270 * dp, y + 300 * dp);
                pill(c, br, b[i], dFocus == i);
                final int ii = i; hit(br, new Runnable() { public void run() { if (ii == 0) openBoard(0); else go(HOME); } });
            }
        }

        // ============================================================ Party: Lobby
        int lf = 2, lCount = 10, lCat = 0; // lf: 0 Fragen, 1 Kategorie, 2 Start
        final int[] COUNTS = {5, 10, 15, 20};
        io.nayuki.qrcodegen.QrCode qr; String qrUrl = "";
        final int[] PCOL = {0xFF7C3AED, 0xFF06B6D4, 0xFFF59E0B, 0xFFEC4899, 0xFF22C55E, 0xFF3B82F6, 0xFFEF4444, 0xFFA855F7};
        void openLobby() {
            Party.start(QuizActivity.this);
            Party.reset();
            lf = 2;
            go(LOBBY);
        }
        List<String> catKeys() { List<String> l = new ArrayList<>(); l.add(null); l.addAll(Bank.cats.keySet()); return l; }
        String catName(String k) { return k == null ? "Alle Kategorien" : Bank.cats.get(k); }
        boolean lobbyKey(int k, boolean ok) {
            if (k == KeyEvent.KEYCODE_BACK) { Party.stop(); go(HOME); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP) lf = Math.max(0, lf - 1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) lf = Math.min(2, lf + 1);
            else if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int d = k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (lf == 0) { int i = 0; for (int j = 0; j < COUNTS.length; j++) if (COUNTS[j] == lCount) i = j; lCount = COUNTS[(i + d + COUNTS.length) % COUNTS.length]; }
                else if (lf == 1) { int n = catKeys().size(); lCat = (lCat + d + n) % n; }
            }
            else if (ok && lf == 2) startParty();
            else if (ok && lf < 2) { if (lf == 0) lobbyKey(KeyEvent.KEYCODE_DPAD_RIGHT, false); else lobbyKey(KeyEvent.KEYCODE_DPAD_RIGHT, false); }
            return true;
        }
        void startParty() {
            if (Party.players.isEmpty()) { Toast.makeText(QuizActivity.this, "Noch niemand dabei – QR-Code mit dem Handy scannen", Toast.LENGTH_LONG).show(); return; }
            String cat = catKeys().get(lCat);
            int n = Math.min(lCount, Bank.count(cat));
            Party.begin(Bank.party(QuizActivity.this, cat, n));
            pStateAt = System.currentTimeMillis(); pf = 0;
            go(PARTY);
        }
        void drawLobby(Canvas c) {
            float pad = 48 * dp;
            logo(c, pad, 64 * dp, 30 * dp);
            text(c, "Party", pad + 220 * dp, 64 * dp, 30 * dp, U.MUTED, false);
            String url = Party.url();
            if (!url.equals(qrUrl)) { qrUrl = url; try { qr = io.nayuki.qrcodegen.QrCode.encodeText(url, io.nayuki.qrcodegen.QrCode.Ecc.MEDIUM); } catch (Exception e) { qr = null; } }
            // QR
            float qs = Math.min(300 * dp, H - 220 * dp), qx = pad, qy = 100 * dp;
            RectF qrR = new RectF(qx, qy, qx + qs, qy + qs);
            card(c, qrR, Color.WHITE, 20 * dp);
            if (qr != null) {
                float in = qs * 0.07f, cell = (qs - 2 * in) / qr.size; p.setColor(U.BG);
                for (int y = 0; y < qr.size; y++) for (int x = 0; x < qr.size; x++) if (qr.getModule(x, y)) c.drawRect(qx + in + x * cell, qy + in + y * cell, qx + in + (x + 1) * cell + 0.5f, qy + in + (y + 1) * cell + 0.5f, p);
            }
            text(c, "Mit dem Handy scannen", qx, qy + qs + 34 * dp, 20 * dp, Color.WHITE, true);
            text(c, "oder im Browser öffnen: " + url.replace("http://", ""), qx, qy + qs + 60 * dp, 16 * dp, U.MUTED, false);
            text(c, "Handy und Fernseher müssen im selben WLAN sein.", qx, qy + qs + 84 * dp, 14 * dp, U.MUTED, false);
            // Spieler
            float rx = qx + qs + 50 * dp, rw = W - pad - rx;
            List<Party.Player> pl = new ArrayList<>(Party.players.values());
            text(c, pl.isEmpty() ? "Warte auf Mitspieler …" : pl.size() + (pl.size() == 1 ? " Spieler ist dabei" : " Spieler sind dabei"), rx, qy + 24 * dp, 24 * dp, Color.WHITE, true);
            float cw = (rw - 2 * 14 * dp) / 3, ch = 58 * dp;
            for (int i = 0; i < pl.size(); i++) {
                Party.Player pp = pl.get(i);
                RectF r = new RectF(rx + (i % 3) * (cw + 14 * dp), qy + 46 * dp + (i / 3) * (ch + 12 * dp), rx + (i % 3) * (cw + 14 * dp) + cw, qy + 46 * dp + (i / 3) * (ch + 12 * dp) + ch);
                card(c, r, U.CARD, 16 * dp);
                p.setColor(PCOL[pp.color % PCOL.length]); c.drawCircle(r.left + 30 * dp, r.centerY(), 18 * dp, p);
                centerText(c, pp.name.substring(0, 1).toUpperCase(Locale.GERMAN), r.left + 30 * dp, r.centerY() + 7 * dp, 18 * dp, Color.WHITE, true);
                t.setTypeface(U.BOLD); t.setTextSize(18 * dp); t.setColor(Color.WHITE);
                c.drawText(ell(pp.name, cw - 70 * dp), r.left + 58 * dp, r.centerY() + 7 * dp, t);
            }
            // Einstellungen
            float sy = H - 190 * dp;
            String[] lab = {"Fragen", "Kategorie"};
            String[] val = {String.valueOf(lCount), catName(catKeys().get(lCat)) + "  (" + Bank.count(catKeys().get(lCat)) + ")"};
            for (int i = 0; i < 2; i++) {
                RectF r = new RectF(rx, sy + i * 56 * dp, rx + rw, sy + i * 56 * dp + 46 * dp);
                card(c, r, lf == i ? U.CARD2 : U.CARD, 23 * dp);
                if (lf == i) focus(c, r, 23 * dp);
                text(c, lab[i], r.left + 22 * dp, r.centerY() + 6 * dp, 17 * dp, U.MUTED, false);
                t.setTypeface(U.BOLD); t.setTextSize(17 * dp); t.setColor(Color.WHITE);
                String v = "‹  " + val[i] + "  ›"; c.drawText(v, r.right - 22 * dp - t.measureText(v), r.centerY() + 6 * dp, t);
                final int ii = i; hit(r, new Runnable() { public void run() { lf = ii; lobbyKey(KeyEvent.KEYCODE_DPAD_RIGHT, false); } });
            }
            RectF sr = new RectF(rx, sy + 116 * dp, rx + rw, sy + 168 * dp);
            pill(c, sr, pl.isEmpty() ? "Spiel starten (wartet auf Spieler)" : "▶  Spiel starten", lf == 2);
            hit(sr, new Runnable() { public void run() { lf = 2; startParty(); } });
        }
        String ell(String s, float w) { if (t.measureText(s) <= w) return s; while (s.length() > 1 && t.measureText(s + "…") > w) s = s.substring(0, s.length() - 1); return s + "…"; }

        // ============================================================ Party: Spiel
        long pStateAt; int pLastPhase = -1, pf;
        void partyTick() {
            long now = System.currentTimeMillis();
            int ph = Party.phase;
            if (ph != pLastPhase) { pLastPhase = ph; pStateAt = now; if (ph == Party.REVEAL) beep(true); }
            if (ph == Party.QUESTION && (Party.left() <= 0 || Party.allAnswered())) Party.reveal();
            else if (ph == Party.REVEAL && now - pStateAt > 4500) Party.scores();
            else if (ph == Party.SCORES && now - pStateAt > 5000) Party.next();
        }
        boolean partyKey(int k, boolean ok) {
            int ph = Party.phase;
            if (ph == Party.END) {
                if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) pf = 1 - pf;
                else if (ok) { if (pf == 0) { Party.reset(); go(LOBBY); } else { Party.stop(); go(HOME); } }
                else if (k == KeyEvent.KEYCODE_BACK) { Party.reset(); go(LOBBY); }
                return true;
            }
            if (k == KeyEvent.KEYCODE_BACK) {
                new AlertDialog.Builder(QuizActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Spiel abbrechen?")
                    .setPositiveButton("Abbrechen", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { Party.reset(); go(LOBBY); } })
                    .setNegativeButton("Weiterspielen", null).show();
                return true;
            }
            if (ok) {   // OK = weiter (Wartezeit ueberspringen)
                if (ph == Party.QUESTION) Party.reveal();
                else if (ph == Party.REVEAL) Party.scores();
                else if (ph == Party.SCORES) Party.next();
            }
            return true;
        }
        void drawParty(Canvas c) {
            float pad = 44 * dp;
            int ph = Party.phase;
            if (ph == Party.END) { drawPodium(c); return; }
            if (ph == Party.SCORES) { drawRanking(c, false); return; }
            Bank.Q q = Party.qs.get(Party.qn);
            text(c, "Frage " + (Party.qn + 1) + " / " + Party.qs.size(), pad, 52 * dp, 18 * dp, U.MUTED, false);
            String cat = Bank.cats.containsKey(q.cat) ? Bank.cats.get(q.cat) : q.cat;
            t.setTypeface(U.BOLD); t.setTextSize(14 * dp); t.setColor(0xFF9EC5FF); c.drawText(cat.toUpperCase(Locale.GERMAN), W / 2 - t.measureText(cat.toUpperCase(Locale.GERMAN)) / 2, 52 * dp, t);
            // Zeit-Kreis
            long left = ph == Party.QUESTION ? Party.left() : 0;
            float cx = W - pad - 34 * dp, cy = 50 * dp;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(6 * dp); p.setColor(0x22FFFFFF); c.drawCircle(cx, cy, 30 * dp, p);
            p.setShader(U.brand(cx - 30 * dp, cx + 30 * dp)); c.drawArc(new RectF(cx - 30 * dp, cy - 30 * dp, cx + 30 * dp, cy + 30 * dp), -90, 360f * left / Party.Q_MS, false, p); p.setShader(null); p.setStyle(Paint.Style.FILL);
            centerText(c, String.valueOf((left + 999) / 1000), cx, cy + 9 * dp, 24 * dp, Color.WHITE, true);
            // Frage
            RectF qr = new RectF(pad, 92 * dp, W - pad, 262 * dp);
            card(c, qr, 0xE615141F, 22 * dp);
            t.setTypeface(U.BOLD); t.setColor(Color.WHITE);
            U.fit(t, q.text, qr.width() - 60 * dp, 3, 34 * dp, 18 * dp);
            int lines = Math.min(3, U.wrap(t, q.text, qr.width() - 60 * dp).size()); float lh = t.getTextSize() * 1.28f;
            U.multi(c, t, q.text, qr.left + 30 * dp, qr.centerY() - lines * lh / 2 + t.getTextSize() * 0.95f, qr.width() - 60 * dp, 3, true);
            // Antworten
            int[] votes = Party.votes(); int total = Math.max(1, Party.players.size());
            float ay = qr.bottom + 22 * dp, gap = 18 * dp, bw = (W - 2 * pad - gap) / 2, bh = Math.min(104 * dp, (H - 70 * dp - ay - gap) / 2);
            for (int i = 0; i < 4; i++) {
                RectF r = new RectF(pad + (i % 2) * (bw + gap), ay + (i / 2) * (bh + gap), pad + (i % 2) * (bw + gap) + bw, ay + (i / 2) * (bh + gap) + bh);
                boolean rev = ph == Party.REVEAL;
                int fill = rev ? (i == q.correct ? U.OK : 0xFF23212F) : U.ANS[i];
                card(c, r, fill, 22 * dp);
                if (rev) { // Anteil der Stimmen als Balken
                    RectF vb = new RectF(r.left, r.bottom - 8 * dp, r.left + r.width() * votes[i] / total, r.bottom);
                    p.setColor(0x66FFFFFF); c.drawRect(vb, p);
                }
                text(c, U.SHAPE[i], r.left + 24 * dp, r.centerY() + 10 * dp, 28 * dp, 0xCCFFFFFF, true);
                t.setTypeface(U.BOLD); t.setColor(rev && i != q.correct ? 0x88FFFFFF : Color.WHITE);
                U.fit(t, q.answers[i], r.width() - 130 * dp, 2, 24 * dp, 14 * dp);
                int ln = Math.min(2, U.wrap(t, q.answers[i], r.width() - 130 * dp).size()); float l2 = t.getTextSize() * 1.28f;
                U.multi(c, t, q.answers[i], r.left + 72 * dp, r.centerY() - ln * l2 / 2 + t.getTextSize() * 0.95f, r.width() - 130 * dp, 2, false);
                if (rev) { t.setTextSize(24 * dp); t.setColor(Color.WHITE); String v = String.valueOf(votes[i]); c.drawText(v, r.right - 26 * dp - t.measureText(v), r.centerY() + 9 * dp, t); }
            }
            // Fusszeile: wer hat geantwortet
            float fy = H - 30 * dp, fx = pad;
            for (Party.Player pp : new ArrayList<>(Party.players.values())) {
                boolean done = pp.answeredQn == Party.qn;
                p.setColor(done ? PCOL[pp.color % PCOL.length] : 0x22FFFFFF); c.drawCircle(fx + 14 * dp, fy - 6 * dp, 14 * dp, p);
                centerText(c, pp.name.substring(0, 1).toUpperCase(Locale.GERMAN), fx + 14 * dp, fy, 14 * dp, done ? Color.WHITE : U.MUTED, true);
                fx += 36 * dp;
            }
            String st = ph == Party.QUESTION ? Party.answeredCount() + " von " + Party.players.size() + " haben geantwortet" : "OK = weiter";
            t.setTypeface(U.POP); t.setTextSize(16 * dp); t.setColor(U.MUTED); c.drawText(st, W - pad - t.measureText(st), fy, t);
        }
        void drawRanking(Canvas c, boolean fin) {
            float pad = 60 * dp;
            centerText(c, "Zwischenstand", W / 2, 70 * dp, 30 * dp, Color.WHITE, true);
            centerText(c, "nach Frage " + (Party.qn + 1) + " von " + Party.qs.size() + "  ·  OK = weiter", W / 2, 100 * dp, 16 * dp, U.MUTED, false);
            List<Party.Player> l = Party.ranking();
            float y = 130 * dp, rh = Math.min(56 * dp, (H - y - 30 * dp) / Math.max(1, Math.min(8, l.size())));
            int max = Math.max(1, l.isEmpty() ? 1 : l.get(0).score);
            for (int i = 0; i < l.size() && i < 8; i++) {
                Party.Player pp = l.get(i);
                RectF r = new RectF(pad + 120 * dp, y + i * rh, W - pad - 120 * dp, y + i * rh + rh - 10 * dp);
                card(c, r, U.CARD, 14 * dp);
                RectF bar = new RectF(r.left, r.top, r.left + r.width() * pp.score / (float) max, r.bottom);
                p.setColor((PCOL[pp.color % PCOL.length] & 0x00FFFFFF) | 0x55000000); c.drawRoundRect(bar, 14 * dp, 14 * dp, p);
                text(c, (i + 1) + ".", r.left + 18 * dp, r.centerY() + 7 * dp, 20 * dp, U.MUTED, true);
                text(c, pp.name, r.left + 64 * dp, r.centerY() + 7 * dp, 20 * dp, Color.WHITE, true);
                t.setTypeface(U.BOLD); t.setTextSize(20 * dp); t.setColor(Color.WHITE);
                String s = U.num(pp.score) + (pp.gained > 0 ? "   +" + pp.gained : ""); c.drawText(s, r.right - 20 * dp - t.measureText(s), r.centerY() + 7 * dp, t);
                if (pp.streak >= 3) text(c, "🔥 " + pp.streak, r.left + 64 * dp + t.measureText(pp.name) + 20 * dp, r.centerY() + 7 * dp, 16 * dp, U.LOCK, true);
            }
        }
        void drawPodium(Canvas c) {
            List<Party.Player> l = Party.ranking();
            centerText(c, "🏆  Siegerehrung", W / 2, 70 * dp, 32 * dp, Color.WHITE, true);
            float base = H - 150 * dp, pw = 200 * dp, cx = W / 2;
            int[] order = {1, 0, 2}; float[] hgt = {150 * dp, 210 * dp, 110 * dp};
            for (int k = 0; k < 3; k++) {
                int i = order[k]; if (i >= l.size()) continue;
                Party.Player pp = l.get(i);
                float x = cx - 1.5f * pw - 20 * dp + k * (pw + 20 * dp);
                RectF r = new RectF(x, base - hgt[k], x + pw, base);
                p.setShader(new LinearGradient(0, r.top, 0, r.bottom, i == 0 ? U.GOLD : i == 1 ? 0xFFCBD5E1 : 0xFFF59E0B, 0xFF15141F, Shader.TileMode.CLAMP)); c.drawRoundRect(r, 18 * dp, 18 * dp, p); p.setShader(null);
                centerText(c, String.valueOf(i + 1), r.centerX(), r.top + 60 * dp, 48 * dp, Color.WHITE, true);
                p.setColor(PCOL[pp.color % PCOL.length]); c.drawCircle(r.centerX(), r.top - 46 * dp, 30 * dp, p);
                centerText(c, pp.name.substring(0, 1).toUpperCase(Locale.GERMAN), r.centerX(), r.top - 36 * dp, 28 * dp, Color.WHITE, true);
                centerText(c, pp.name, r.centerX(), r.top - 90 * dp, 22 * dp, Color.WHITE, true);
                centerText(c, U.num(pp.score) + " P", r.centerX(), base + 30 * dp, 18 * dp, 0xFFC9CCE0, true);
            }
            if (l.size() > 3) {
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i < l.size(); i++) sb.append(i + 1).append(". ").append(l.get(i).name).append(" (").append(U.num(l.get(i).score)).append(")   ");
                centerText(c, sb.toString().trim(), W / 2, base + 62 * dp, 15 * dp, U.MUTED, false);
            }
            String[] b = {"Nochmal (gleiche Spieler)", "Menü"};
            for (int i = 0; i < 2; i++) {
                RectF br = new RectF(W / 2 - 300 * dp + i * 310 * dp, H - 60 * dp, W / 2 + i * 310 * dp - 10 * dp, H - 16 * dp);
                pill(c, br, b[i], pf == i);
                final int ii = i; hit(br, new Runnable() { public void run() { if (ii == 0) { Party.reset(); go(LOBBY); } else { Party.stop(); go(HOME); } } });
            }
        }

        // ============================================================ Bestenliste
        int bTab; boolean boardLoading; JSONObject bData; String bErr;
        final String[] bTabs = {"Tages-Duell heute", "Die Million · Woche", "Die Million · Ewig"};
        String myHash;
        void openBoard(int tab) { bTab = tab; go(BOARD); loadBoard(); }
        void loadBoard() {
            boardLoading = true; bData = null; bErr = null;
            final int tb = bTab;
            new Thread(new Runnable() { public void run() {
                JSONObject d = null; String err = null;
                try { d = Scores.top(tb == 0 ? "daily" : "million", tb == 1 ? "week" : "all"); } catch (Exception e) { err = "Bestenliste nicht erreichbar"; }
                if (myHash == null) try {
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                    StringBuilder sb = new StringBuilder(); for (byte b : md.digest(Scores.device(QuizActivity.this).getBytes("UTF-8"))) sb.append(String.format("%02x", b & 0xFF));
                    myHash = sb.substring(0, 8);
                } catch (Exception ignored) {}
                final JSONObject fd = d; final String fe = err;
                h.post(new Runnable() { public void run() { if (tb != bTab) return; bData = fd; bErr = fe; boardLoading = false; invalidate(); } });
            }}).start();
        }
        boolean boardKey(int k, boolean ok) {
            if (k == KeyEvent.KEYCODE_BACK) { go(HOME); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && bTab > 0) { bTab--; loadBoard(); }
            else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && bTab < 2) { bTab++; loadBoard(); }
            else if (ok) loadBoard();
            return true;
        }
        void drawBoard(Canvas c) {
            float pad = 60 * dp;
            logo(c, pad, 60 * dp, 26 * dp);
            text(c, "Weltweite Bestenliste", pad + 190 * dp, 60 * dp, 20 * dp, U.MUTED, false);
            float tx = pad;
            for (int i = 0; i < 3; i++) {
                t.setTypeface(U.BOLD); t.setTextSize(16 * dp); float w = t.measureText(bTabs[i]) + 44 * dp;
                RectF r = new RectF(tx, 86 * dp, tx + w, 130 * dp);
                pill(c, r, bTabs[i], bTab == i);
                final int ii = i; hit(r, new Runnable() { public void run() { bTab = ii; loadBoard(); } });
                tx += w + 12 * dp;
            }
            float y = 156 * dp;
            if (boardLoading) { centerText(c, "Lade …", W / 2, y + 80 * dp, 20 * dp, U.MUTED, false); return; }
            if (bErr != null) { centerText(c, bErr + "  ·  OK = nochmal versuchen", W / 2, y + 80 * dp, 20 * dp, U.MUTED, false); return; }
            JSONArray l = bData == null ? null : bData.optJSONArray("list");
            if (l == null || l.length() == 0) { centerText(c, "Noch keine Einträge – sei die oder der Erste!", W / 2, y + 80 * dp, 20 * dp, U.MUTED, false); return; }
            boolean daily = bTab == 0;
            int rows = Math.min(l.length(), Math.max(1, (int) ((H - y - 40 * dp) / (44 * dp))));
            if (rows > 10) rows = Math.min(l.length(), 20);
            int perCol = Math.min(10, rows); int cols = rows > 10 ? 2 : 1;
            float colW = (W - 2 * pad - (cols - 1) * 30 * dp) / cols, rh = Math.min(48 * dp, (H - y - 30 * dp) / perCol);
            for (int i = 0; i < rows; i++) {
                JSONObject e = l.optJSONObject(i);
                float x = pad + (i / perCol) * (colW + 30 * dp), ry = y + (i % perCol) * rh;
                RectF r = new RectF(x, ry, x + colW, ry + rh - 6 * dp);
                boolean mine = myHash != null && myHash.equals(e.optString("dev"));
                card(c, r, mine ? 0xFF2E2B4A : U.CARD, 12 * dp);
                if (mine) focus(c, r, 12 * dp);
                int medal = i == 0 ? U.GOLD : i == 1 ? 0xFFCBD5E1 : i == 2 ? 0xFFF59E0B : U.MUTED;
                text(c, String.valueOf(i + 1), r.left + 16 * dp, r.centerY() + 6 * dp, 17 * dp, medal, true);
                t.setTypeface(U.BOLD); t.setTextSize(17 * dp); t.setColor(Color.WHITE);
                c.drawText(ell(e.optString("n"), colW * 0.45f), r.left + 58 * dp, r.centerY() + 6 * dp, t);
                String sc = daily ? U.num(e.optInt("s")) + " P" : U.money(e.optInt("s"));
                String tm = U.dur(e.optLong("t"));
                t.setTypeface(U.POP); t.setTextSize(14 * dp); t.setColor(U.MUTED);
                c.drawText(tm, r.right - 16 * dp - t.measureText(tm), r.centerY() + 6 * dp, t);
                float tmw = t.measureText(tm);
                t.setTypeface(U.BOLD); t.setTextSize(17 * dp); t.setColor(Color.WHITE);
                c.drawText(sc, r.right - 36 * dp - tmw - t.measureText(sc), r.centerY() + 6 * dp, t);
            }
            if (daily && bData.has("players")) text(c, bData.optInt("players") + " Spieler heute", pad, H - 16 * dp, 14 * dp, U.MUTED, false);
        }
    }
}
