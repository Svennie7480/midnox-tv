<?php
/*
 * midnox Emby – Gemeinschafts-Server
 * Eine Datei. Ablage im Unterordner data/ (wird automatisch angelegt und von außen gesperrt).
 * Anmeldung: Die App schickt ihr Emby-Token; der Server fragt bei Emby nach, wem es gehört.
 *
 *   GET  ?a=hello                         wer bin ich (prüft das Token)
 *   GET  ?a=feed[&w=<titel>]              Admin-Nachrichten, Chat-Stand, offene Watch-Partys, wer ist online
 *   POST ?a=rate {item,stars,comment,meta} Bewertung abgeben (stars 0 = löschen)
 *   GET  ?a=ratings&ids=a,b,c             Community-Schnitt für mehrere Titel
 *   GET  ?a=item&id=<id>                  alle Bewertungen eines Titels
 *   GET  ?a=top                           Bestenliste + neueste Bewertungen
 *   GET  ?a=chat&since=<id>               Empfehlungs-Chat
 *   POST ?a=say {text,item?}              Nachricht/Empfehlung schreiben
 *   POST ?a=chat_del {id}                 Nachricht löschen (eigene oder Admin)
 *   POST ?a=news {text,kind,minutes}      Rundnachricht an alle (nur Admin)
 *   POST ?a=news_del {id}                 Rundnachricht zurückziehen (nur Admin)
 *   POST ?a=party_new {item,meta,pos}     Watch-Party eröffnen
 *   POST ?a=party_join|party_leave {id}
 *   POST ?a=party_state {id,playing,pos}  Abspielstand setzen (Gastgeber oder alle, wenn freigegeben)
 *   GET  ?a=party&id=..&since=..&ready=..&buf=..&pos=..   Zustand abholen (≈1× pro Sekunde)
 *   POST ?a=party_react {id,e}            Emoji-Reaktion
 *   POST ?a=match_new {items,tv}         „Was gucken wir heute?“-Runde anlegen (Code für die Handys)
 *   GET/POST ?a=m_* (ohne Emby-Login, mit Code) Handy-Seite match.html: beitreten, abstimmen, Stand
 *   POST ?a=log {id,type,secs,...}        Wiedergabe protokollieren (für Rückblick & Errungenschaften)
 *   GET  ?a=mystats&year=                 eigenes Protokoll + Zähler (Bewertungen, Chat, Partys, Matches)
 *   POST ?a=ach {got:{id:{lv,t}}, sum}    freigeschaltete Errungenschaften + Jahres-Kurzfassung speichern
 *   GET  ?a=board&year=                   Rangliste/Übersicht aller für den Rückblick der Runde
 *   POST ?a=share_new {kind,...}          Teilen-Karte (Errungenschaft/Rückblick) anlegen → Link + QR fürs Handy
 *   GET  ?s=ID                            öffentliche Teilen-Seite (mit Vorschaubild für WhatsApp & Co.)
 *   GET  ?check                           Selbsttest im Browser
 */
declare(strict_types=1);
error_reporting(0);

// ===== Einstellungen =====
const EMBY = '__EMBY_URL__';   // Adresse deines Emby-Servers, wie er aus dem Internet erreichbar ist, z. B. https://emby.meinedomain.de
const TOKEN_TTL = 43200;       // so lange (s) gilt eine erfolgreiche Token-Prüfung
const CHAT_KEEP = 600;         // so viele Chat-Nachrichten aufheben
const PARTY_IDLE = 25;         // Mitglied gilt nach so vielen s ohne Abfrage als weg
const ONLINE_WINDOW = 120;     // „online", wenn in den letzten s aktiv
const LOG_MAX = 6000;          // Wiedergabe-Einträge pro Person und Jahr
const SHARE_TTL = 86400 * 120; // Teilen-Karten bleiben 4 Monate
const SHARE_PAGE = <<<'SHAREPAGE'
<!doctype html>
<html lang="de"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#0b0a14">
<title>%TITLE%</title>
<meta property="og:type" content="website"><meta property="og:site_name" content="midnox Emby">
<meta property="og:title" content="%TITLE%"><meta property="og:description" content="%DESC%"><meta property="og:url" content="%URL%">
%OGIMG%
<style>
* { box-sizing: border-box; margin: 0; -webkit-tap-highlight-color: transparent; }
html, body { background: #0b0a14; color: #f4f4fb; font: 16px/1.4 Inter, system-ui, -apple-system, Segoe UI, Roboto, sans-serif; }
body::before { content: ''; position: fixed; inset: -20%; background: radial-gradient(40% 35% at 20% 10%, rgba(124,58,237,.35), transparent 70%), radial-gradient(35% 30% at 90% 30%, rgba(6,182,212,.18), transparent 70%); pointer-events: none; z-index: -1; }
.wrap { max-width: 520px; margin: 0 auto; padding: max(16px, env(safe-area-inset-top)) 16px 28px; }
header { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.logo { width: 34px; height: 34px; border-radius: 10px; background: linear-gradient(120deg, #7c3aed, #3b5bf0 55%, #06b6d4); display: grid; place-items: center; font-weight: 900; }
header b { font-size: 15px; } header small { display: block; color: #a3a6c2; font-size: 12px; }
#card { width: 100%; aspect-ratio: 1080/1350; border-radius: 22px; display: block; box-shadow: 0 20px 60px rgba(0,0,0,.6); background: #17152a; }
.btns { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 16px; }
button, a.b { font: inherit; font-weight: 800; font-size: 16px; border: 0; border-radius: 16px; padding: 15px; color: #fff; background: rgba(255,255,255,.08); text-align: center; text-decoration: none; cursor: pointer; }
.pri { grid-column: 1 / -1; background: linear-gradient(120deg, #7c3aed, #3b5bf0 55%, #06b6d4) !important; font-size: 18px !important; box-shadow: 0 10px 30px rgba(59,91,240,.35); }
.wa { background: #1fa855 !important; }
.hint { color: #a3a6c2; font-size: 13px; text-align: center; margin-top: 14px; }
</style></head>
<body><div class="wrap">
<header><div class="logo">m</div><div><b>midnox Emby</b><small>Teilen</small></div></header>
<img id="card" alt="">
<div class="btns">
  <button class="pri" id="share">Teilen …</button>
  <a class="b wa" id="wa" target="_blank" rel="noopener">WhatsApp</a>
  <a class="b" id="dl" download="midnox.png">Bild speichern</a>
  <button id="copy" style="grid-column:1/-1">Link kopieren</button>
</div>
<p class="hint" id="hint">Tipp: Für den WhatsApp-Status das Bild speichern und dort hochladen.</p>
</div>
<script>
'use strict';
const S = %DATA%; const API = '%API%'; const C = S.card;
const LV = ['', 'BRONZE', 'SILBER', 'GOLD', 'PLATIN'];
const LVC = [['#3a3654', '#1a1830', '#555'], ['#f3b37c', '#8a4b22', '#d08a4e'], ['#ffffff', '#8b93a8', '#c9d1e4'], ['#fff3a3', '#b8860b', '#facc15'], ['#c4b5fd', '#22d3ee', '#a5f3fc']];
const W = 1080, H = 1350;
const cv = document.createElement('canvas'); cv.width = W; cv.height = H; const x = cv.getContext('2d');
const F = (w, s) => w + ' ' + s + 'px Inter, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';
const grad = (x0, y0, x1, y1) => { const g = x.createLinearGradient(x0, y0, x1, y1); g.addColorStop(0, '#8b5cf6'); g.addColorStop(.55, '#3b82f6'); g.addColorStop(1, '#06b6d4'); return g; };
function blob(cx, cy, r, col) { const g = x.createRadialGradient(cx, cy, 0, cx, cy, r); g.addColorStop(0, col); g.addColorStop(1, 'rgba(0,0,0,0)'); x.fillStyle = g; x.fillRect(0, 0, W, H); }
function rr(x0, y0, w, h, r) { x.beginPath(); x.moveTo(x0 + r, y0); x.arcTo(x0 + w, y0, x0 + w, y0 + h, r); x.arcTo(x0 + w, y0 + h, x0, y0 + h, r); x.arcTo(x0, y0 + h, x0, y0, r); x.arcTo(x0, y0, x0 + w, y0, r); x.closePath(); }
function text(t, px, py, font, fill, align, maxW) { x.font = font; x.fillStyle = fill; x.textAlign = align || 'left'; x.textBaseline = 'alphabetic'; x.fillText(t, px, py, maxW); }
function wrapText(t, px, py, font, fill, maxW, lh, align, maxLines) {
  x.font = font; const words = String(t).split(/\s+/); const lines = []; let line = '';
  for (const w of words) { const test = line ? line + ' ' + w : w; if (x.measureText(test).width > maxW && line) { lines.push(line); line = w; } else line = test; }
  if (line) lines.push(line); const L = lines.slice(0, maxLines || 3);
  L.forEach((l, i) => text(l, px, py + i * lh, font, fill, align)); return L.length;
}
function medal(cx, cy, r, icon, lv) {
  const c = LVC[lv] || LVC[0];
  if (lv) { x.save(); x.shadowColor = c[2]; x.shadowBlur = r * .5; x.beginPath(); x.arc(cx, cy, r, 0, 7); x.fillStyle = c[2]; x.fill(); x.restore(); }
  let g;
  if (lv === 4) { g = x.createLinearGradient(cx - r, cy - r, cx + r, cy + r); g.addColorStop(0, '#a5f3fc'); g.addColorStop(.35, '#c4b5fd'); g.addColorStop(.7, '#f0abfc'); g.addColorStop(1, '#a5f3fc'); }
  else { g = x.createRadialGradient(cx - r * .3, cy - r * .35, r * .1, cx, cy, r); g.addColorStop(0, c[0]); g.addColorStop(1, c[1]); }
  x.beginPath(); x.arc(cx, cy, r, 0, 7); x.fillStyle = g; x.fill();
  x.lineWidth = r * .07; x.strokeStyle = lv === 4 ? '#fff' : c[2]; x.stroke();
  x.beginPath(); x.arc(cx, cy, r * .8, 0, 7); x.lineWidth = r * .02; x.strokeStyle = 'rgba(255,255,255,.35)'; x.stroke();
  x.font = Math.round(r * .95) + 'px "Apple Color Emoji","Segoe UI Emoji","Noto Color Emoji",sans-serif'; x.textAlign = 'center'; x.textBaseline = 'middle'; x.fillStyle = '#000'; x.fillText(icon || '🏆', cx, cy + r * .05);
}
function frame() {
  x.fillStyle = '#0b0a14'; x.fillRect(0, 0, W, H);
  blob(150, 120, 700, 'rgba(124,58,237,.55)'); blob(1000, 500, 600, 'rgba(59,91,240,.35)'); blob(400, 1350, 700, 'rgba(6,182,212,.25)');
  rr(70, 70, 76, 76, 22); x.fillStyle = grad(70, 70, 146, 146); x.fill(); text('m', 108, 125, F(900, 46), '#fff', 'center');
  text('midnox', 168, 106, F(300, 34), '#fff'); x.font = F(300, 34); const mw = x.measureText('midnox ').width; text('Emby', 168 + mw, 106, F(800, 34), '#fff');
  text((C.kind === 'wrapped' ? 'Jahresrückblick ' + C.year : 'Errungenschaften').toUpperCase(), 168, 140, F(700, 20), '#a5a8c8');
  x.fillStyle = 'rgba(255,255,255,.12)'; x.fillRect(70, H - 130, W - 140, 2);
  text(C.name, 70, H - 72, F(800, 36), '#fff'); text('midnox.de', W - 70, H - 72, F(600, 30), '#a5a8c8', 'right');
}
function kicker(t, y) { x.font = F(800, 26); const w = x.measureText(t).width + 40; const gx = (W - w) / 2; rr(gx - 22, y - 38, w + 44, 56, 28); x.fillStyle = 'rgba(255,255,255,.08)'; x.fill(); x.beginPath(); x.arc(gx + 4, y - 10, 9, 0, 7); x.fillStyle = grad(gx - 5, y, gx + 13, y); x.fill(); text(t, gx + 26, y, F(800, 26), '#d9dbf0'); }
function drawAch() {
  kicker('ERRUNGENSCHAFT FREIGESCHALTET', 300);
  medal(W / 2, 560, 200, C.icon, C.lv);
  text(LV[C.lv] || '', W / 2, 840, F(900, 34), (LVC[C.lv] || LVC[0])[2], 'center');
  wrapText(C.title, W / 2, 940, F(900, 96), '#fff', W - 160, 100, 'center', 2);
  wrapText(C.sub, W / 2, 1030, F(500, 40), '#c9cbe3', W - 200, 52, 'center', 2);
  for (let i = 1; i <= 4; i++) { const cx = W / 2 + (i - 2.5) * 120; x.beginPath(); x.arc(cx, 1150, 16, 0, 7); x.fillStyle = i <= C.lv ? (LVC[i][2]) : 'rgba(255,255,255,.14)'; x.fill(); }
}
function drawBadges() {
  kicker('MEINE ABZEICHEN', 270);
  const t = C.medals.length + ' Abzeichen'; x.font = F(900, 92); const g = grad(W / 2 - 300, 0, W / 2 + 300, 0); text(t, W / 2, 400, F(900, 92), g, 'center');
  text(C.pts + ' Punkte' + (C.persona ? ' · ' + C.personaE + ' ' + C.persona : ''), W / 2, 465, F(600, 36), '#c9cbe3', 'center');
  const n = C.medals.length, cols = 4, r = 76; const rows = Math.ceil(n / cols);
  C.medals.forEach((m, i) => { const row = Math.floor(i / cols), col = i % cols; const inRow = Math.min(cols, n - row * cols); const cx = W / 2 + (col - (inRow - 1) / 2) * 230; const cy = (rows > 2 ? 590 : 630) + row * (rows > 2 ? 240 : 260); medal(cx, cy, r, m.i, m.lv); text(m.n, cx, cy + r + 46, F(700, 24), '#e6e7f5', 'center', 210); });
}
function drawWrapped(poster) {
  const g = grad(70, 0, 900, 0); text(String(C.year), 70, 430, F(900, 250), g);
  wrapText(C.name + 's Jahr bei midnox', 70, 520, F(900, 60), '#fff', W - 140, 66, 'left', 2);
  const st = C.stats.slice(0, 4);
  st.forEach((s, i) => { const col = i % 2, row = Math.floor(i / 2); const bx = 70 + col * 480, by = 600 + row * 190; rr(bx, by, 460, 170, 28); x.fillStyle = 'rgba(255,255,255,.07)'; x.fill(); text(s.v, bx + 34, by + 100, F(900, 72), grad(bx, 0, bx + 400, 0)); text(s.l, bx + 34, by + 145, F(600, 28), '#b9bbd6'); });
  const y0 = 1010;
  if (C.posterName) {
    if (poster) { x.save(); rr(70, y0, 120, 180, 16); x.clip(); x.drawImage(poster, 70, y0, 120, 180); x.restore(); }
    const tx = poster ? 220 : 70;
    text('SERIE DES JAHRES', tx, y0 + 40, F(800, 22), '#a78bfa'); wrapText(C.posterName, tx, y0 + 92, F(900, 46), '#fff', W - tx - 70, 50, 'left', 1);
    if (C.persona) text(C.personaE + ' ' + C.persona + (C.sub ? ' · ' + C.sub : ''), tx, y0 + 150, F(600, 30), '#c9cbe3', 'left', W - tx - 70);
  } else if (C.persona) { text(C.personaE + ' ' + C.persona, 70, y0 + 80, F(900, 56), '#fff'); }
}
function render(poster) {
  frame();
  if (C.kind === 'ach') drawAch(); else if (C.kind === 'badges') drawBadges(); else drawWrapped(poster);
  const url = cv.toDataURL('image/png'); const im = document.getElementById('card'); im.src = url; document.getElementById('dl').href = url;
  if (!S.png) cv.toBlob(b => { if (b) fetch(API + '?a=s_up&id=' + S.id, { method: 'POST', body: b }).catch(() => {}); }, 'image/png');
}
const shareText = C.kind === 'wrapped' ? 'Mein Jahr ' + C.year + ' bei midnox 🍿' : C.kind === 'badges' ? 'Meine midnox-Abzeichen 🏆' : 'Errungenschaft freigeschaltet: ' + C.icon + ' ' + C.title + ' (' + (LV[C.lv] || '').toLowerCase().replace(/^./, c => c.toUpperCase()) + ')';
document.getElementById('wa').href = 'https://wa.me/?text=' + encodeURIComponent(shareText + '\n' + S.url);
document.getElementById('share').onclick = () => {
  cv.toBlob(async (b) => {
    const file = b ? new File([b], 'midnox.png', { type: 'image/png' }) : null;
    try {
      if (file && navigator.canShare && navigator.canShare({ files: [file] })) await navigator.share({ files: [file], text: shareText + '\n' + S.url });
      else if (navigator.share) await navigator.share({ title: 'midnox Emby', text: shareText, url: S.url });
      else document.getElementById('copy').click();
    } catch (e) {}
  }, 'image/png');
};
document.getElementById('copy').onclick = async () => { try { await navigator.clipboard.writeText(S.url); document.getElementById('hint').textContent = 'Link kopiert ✓'; } catch (e) { prompt('Link kopieren:', S.url); } };
if (C.kind === 'wrapped' && C.poster) { const p = new Image(); p.onload = () => render(p); p.onerror = () => render(null); p.src = API + '?a=s_img&id=' + S.id + '&item=' + encodeURIComponent(C.poster); }
else (document.fonts && document.fonts.ready ? document.fonts.ready : Promise.resolve()).then(() => render(null));
</script></body></html>
SHAREPAGE;
const MATCH_TTL = 14400;       // „Was gucken wir heute?“-Runden verfallen nach 4 h
const MATCH_MAX = 60;          // höchstens so viele Titel pro Runde

$DATA = __DIR__ . '/data';
if (!is_dir($DATA)) { @mkdir($DATA, 0775, true); }
if (!file_exists("$DATA/.htaccess")) { @file_put_contents("$DATA/.htaccess", "Require all denied\nDeny from all\n"); @file_put_contents("$DATA/index.html", ''); }

header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: Content-Type, X-Emby-Token, X-Emby-User, X-Emby-Device');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Cache-Control: no-store');
if (($_SERVER['REQUEST_METHOD'] ?? '') === 'OPTIONS') { exit; }

function now_ms(): int { return (int) round(microtime(true) * 1000); }
function out($x, int $code = 200): void { http_response_code($code); header('Content-Type: application/json; charset=utf-8'); echo json_encode($x, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); exit; }
function fail(string $err, int $code = 400, string $msg = ''): void { out(['err' => $err, 'msg' => $msg], $code); }
function body(): array { static $b = null; if ($b === null) { $b = json_decode((string) file_get_contents('php://input'), true); if (!is_array($b)) $b = []; } return $b; }
function clean(string $s, int $max): string { $s = trim(preg_replace('/[\x00-\x08\x0B-\x1F\x7F]/u', '', $s) ?? ''); return mb_substr($s, 0, $max); }
function safe_id(string $s): string { return preg_replace('/[^A-Za-z0-9_-]/', '', $s) ?? ''; }

/** Datei unter Sperre lesen, ändern, schreiben. $fn bekommt das Array per Referenz und gibt das Ergebnis zurück. */
function store(string $name, callable $fn) {
    global $DATA;
    $f = fopen("$DATA/$name.json", 'c+');
    if (!$f) fail('storage', 500, 'Datenordner nicht beschreibbar');
    flock($f, LOCK_EX);
    $raw = stream_get_contents($f);
    $d = $raw ? json_decode($raw, true) : [];
    if (!is_array($d)) $d = [];
    $before = $raw;
    $res = $fn($d);
    $after = json_encode($d, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if ($after !== $before) { ftruncate($f, 0); rewind($f); fwrite($f, $after); fflush($f); }
    flock($f, LOCK_UN); fclose($f);
    return $res;
}
function load(string $name): array {
    global $DATA;
    $p = "$DATA/$name.json"; if (!file_exists($p)) return [];
    $f = fopen($p, 'r'); flock($f, LOCK_SH); $raw = stream_get_contents($f); flock($f, LOCK_UN); fclose($f);
    $d = json_decode((string) $raw, true); return is_array($d) ? $d : [];
}

function emby_get(string $path, string $token, int $timeout = 8): array {
    $url = rtrim(EMBY, '/');
    if (!preg_match('~/emby$~i', $url)) $url .= '/emby';
    $url .= $path;
    $hdr = "X-Emby-Token: $token\r\nAccept: application/json\r\nX-Emby-Authorization: Emby Client=\"midnox-community\", Device=\"server\", DeviceId=\"midnox-community\", Version=\"1.0\"\r\n";
    if (function_exists('curl_init')) {
        $c = curl_init($url);
        curl_setopt_array($c, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => $timeout, CURLOPT_CONNECTTIMEOUT => 5,
            CURLOPT_HTTPHEADER => array_filter(explode("\r\n", $hdr)), CURLOPT_FOLLOWLOCATION => true]);
        $res = curl_exec($c); $code = (int) curl_getinfo($c, CURLINFO_HTTP_CODE); curl_close($c);
        return [$code, $res === false ? null : json_decode((string) $res, true)];
    }
    $ctx = stream_context_create(['http' => ['header' => $hdr, 'timeout' => $timeout, 'ignore_errors' => true]]);
    $res = @file_get_contents($url, false, $ctx);
    $code = 0; if (isset($http_response_header[0]) && preg_match('~\s(\d{3})\s~', $http_response_header[0], $m)) $code = (int) $m[1];
    return [$code, $res === false ? null : json_decode((string) $res, true)];
}

/** Wer steckt hinter dem Token? Ergebnis wird zwischengespeichert. */
/** Zähler pro Person: gesamt und pro Jahr (Watch-Partys, Matches …). */
function bump(string $uid, string $key, int $n = 1): void {
    $y = date('Y');
    store('cnt_' . $uid, function (&$d) use ($key, $n, $y) { $d['all'][$key] = ($d['all'][$key] ?? 0) + $n; $d[$y][$key] = ($d[$y][$key] ?? 0) + $n; });
}
function me(): array {
    $tok = (string) ($_SERVER['HTTP_X_EMBY_TOKEN'] ?? '');
    $uid = safe_id((string) ($_SERVER['HTTP_X_EMBY_USER'] ?? ''));
    $dev = (string) ($_SERVER['HTTP_X_EMBY_DEVICE'] ?? '');
    if (strlen($tok) < 16 || $uid === '') fail('auth', 401, 'Nicht angemeldet');
    if (strpos(EMBY, '__') === 0) fail('config', 503, 'Community-Server ist noch nicht eingerichtet (Emby-Adresse fehlt in index.php)');
    $key = hash('sha256', $tok . '|' . $uid);
    $cache = load('tokens');
    if (isset($cache[$key]) && $cache[$key]['until'] > time()) return touch_user($cache[$key]['u']);
    // Admin-Token? Dann darf es die Nutzerliste sehen.
    [$code, $users] = emby_get('/Users', $tok);
    if ($code === 401) fail('auth', 401, 'Emby-Anmeldung abgelaufen');
    if ($code === 0) fail('emby', 502, 'Community-Server erreicht deinen Emby-Server nicht');
    $u = null;
    if ($code === 200 && is_array($users)) {
        foreach ($users as $x) if (($x['Id'] ?? '') === $uid) $u = ['id' => $uid, 'name' => (string) $x['Name'], 'admin' => !empty($x['Policy']['IsAdministrator'])];
    } else {
        // Normales Konto: Emby zeigt nur die eigenen Sitzungen.
        [$c2, $sess] = emby_get('/Sessions' . ($dev !== '' ? '?DeviceId=' . rawurlencode($dev) : ''), $tok);
        if ($c2 === 200 && is_array($sess)) foreach ($sess as $s) if (($s['UserId'] ?? '') === $uid) { $u = ['id' => $uid, 'name' => (string) ($s['UserName'] ?? 'Gast'), 'admin' => false]; break; }
        if (!$u && $dev !== '') {   // Sitzung evtl. ohne passendes Gerät: ohne Filter erneut
            [$c2, $sess] = emby_get('/Sessions', $tok);
            if ($c2 === 200 && is_array($sess)) foreach ($sess as $s) if (($s['UserId'] ?? '') === $uid) { $u = ['id' => $uid, 'name' => (string) ($s['UserName'] ?? 'Gast'), 'admin' => false]; break; }
        }
    }
    if (!$u) fail('auth', 403, 'Token passt nicht zu diesem Konto');
    store('tokens', function (&$d) use ($key, $u) {
        $t = time(); foreach ($d as $k => $v) if (($v['until'] ?? 0) < $t) unset($d[$k]);
        $d[$key] = ['u' => $u, 'until' => $t + TOKEN_TTL];
    });
    return touch_user($u);
}
function touch_user(array $u): array {
    static $done = false; if ($done) return $u; $done = true;
    $w = isset($_GET['w']) ? clean((string) $_GET['w'], 80) : null;
    store('online', function (&$d) use ($u, $w) {
        $t = time();
        foreach ($d as $k => $v) if (($v['t'] ?? 0) < $t - 86400 * 30) unset($d[$k]);
        $prev = $d[$u['id']] ?? [];
        $d[$u['id']] = ['n' => $u['name'], 't' => $t, 'a' => $u['admin'], 'w' => $w !== null ? $w : ($prev['w'] ?? '')];
    });
    return $u;
}
function need_admin(array $u): void { if (empty($u['admin'])) fail('admin', 403, 'Nur für Administratoren'); }
function meta_in($m): array {
    $m = is_array($m) ? $m : [];
    return ['name' => clean((string) ($m['name'] ?? ''), 120), 'type' => safe_id((string) ($m['type'] ?? '')), 'year' => (int) ($m['year'] ?? 0),
        'series' => clean((string) ($m['series'] ?? ''), 120)];
}
function rate_limit(string $what, string $uid, float $gap): void {
    $ok = store('limits', function (&$d) use ($what, $uid, $gap) {
        $k = "$what:$uid"; $t = microtime(true);
        if (count($d) > 2000) $d = array_filter($d, function ($v) use ($t) { return $v > $t - 3600; });
        if (isset($d[$k]) && $t - $d[$k] < $gap) return false;
        $d[$k] = $t; return true;
    });
    if (!$ok) fail('slow', 429, 'Bitte kurz warten');
}

// ===== Selbsttest =====
if (isset($_GET['check'])) {
    header('Content-Type: text/html; charset=utf-8');
    $w = is_writable($DATA);
    $cfg = strpos(EMBY, '__') !== 0;
    $reach = null;
    if ($cfg) { [$c] = emby_get('/System/Info/Public', ''); $reach = $c; }
    echo '<!doctype html><meta charset=utf-8><title>midnox Emby Community</title><body style="font-family:system-ui;background:#0b0a14;color:#eee;padding:30px">';
    echo '<h2>midnox Emby – Community-Server</h2><ul style="line-height:2">';
    echo '<li>Datenordner beschreibbar: ' . ($w ? '✅' : '❌') . '</li>';
    echo '<li>Emby-Adresse eingetragen: ' . ($cfg ? '✅ ' . htmlspecialchars(EMBY) : '❌ (EMBY in index.php setzen)') . '</li>';
    if ($cfg) echo '<li>Emby erreichbar: ' . ($reach === 200 ? '✅' : '❌ (HTTP ' . (int) $reach . ')') . '</li>';
    echo '<li>PHP ' . PHP_VERSION . ', cURL: ' . (function_exists('curl_init') ? 'ja' : 'nein') . '</li></ul></body>';
    exit;
}

function emby_img(string $id, string $tag = '', int $w = 600): void {
    $url = rtrim(EMBY, '/'); if (!preg_match('~/emby$~i', $url)) $url .= '/emby';
    $url .= '/Items/' . $id . '/Images/Primary?maxWidth=' . $w . '&quality=85' . ($tag !== '' ? '&tag=' . rawurlencode($tag) : '');
    $c = curl_init($url); curl_setopt_array($c, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10, CURLOPT_FOLLOWLOCATION => true]);
    $img = curl_exec($c); $code = (int) curl_getinfo($c, CURLINFO_HTTP_CODE); $ct = (string) curl_getinfo($c, CURLINFO_CONTENT_TYPE); curl_close($c);
    if ($code !== 200 || !$img) fail('img', 404);
    header('Content-Type: ' . ($ct ?: 'image/jpeg')); header('Cache-Control: public, max-age=86400'); header_remove('Pragma'); echo $img; exit;
}

// ===== Teilen: öffentliche Seite + Bild, ohne Login (nur mit der zufälligen ID) =====
function share_load(string $id): array { $id = preg_replace('/[^A-Za-z0-9]/', '', $id); if (strlen($id) < 8) return []; $r = load('share_' . $id); if (!$r || ($r['t'] ?? 0) < time() - SHARE_TTL) return []; return $r; }
if (isset($_GET['s'])) {
    $id = preg_replace('/[^A-Za-z0-9]/', '', (string) $_GET['s']); $r = share_load($id);
    if (!$r) { http_response_code(404); header('Content-Type: text/html; charset=utf-8'); echo '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><body style="background:#0b0a14;color:#fff;font-family:system-ui;padding:40px">Diese Karte gibt es nicht mehr.</body>'; exit; }
    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
    $base = ($https ? 'https' : 'http') . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . preg_replace('~[^/]*$~', '', (string) ($_SERVER['SCRIPT_NAME'] ?? '/'));
    $self = $base . '?s=' . $id; $png = file_exists("$DATA/sharepng_$id.png");
    $h = function ($x) { return htmlspecialchars((string) $x, ENT_QUOTES, 'UTF-8'); };
    $page = str_replace(['%TITLE%', '%DESC%', '%URL%', '%OGIMG%', '%DATA%', '%API%'],
        [$h($r['og_t'] ?? 'midnox Emby'), $h($r['og_d'] ?? ''), $h($self), $png ? '<meta property="og:image" content="' . $h($base . 'index.php?a=s_png&id=' . $id) . '"><meta property="og:image:width" content="1080"><meta property="og:image:height" content="1350"><meta name="twitter:card" content="summary_large_image">' : '',
         json_encode(['id' => $id, 'png' => $png, 'url' => $self, 'card' => $r['card']], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_HEX_TAG | JSON_HEX_AMP), $h($base . 'index.php')], SHARE_PAGE);
    header('Content-Type: text/html; charset=utf-8'); header('Cache-Control: no-cache'); echo $page; exit;
}
$a = (string) ($_GET['a'] ?? '');
if ($a === 's_png' || $a === 's_img' || $a === 's_up') {
    $id = preg_replace('/[^A-Za-z0-9]/', '', (string) ($_GET['id'] ?? '')); $r = share_load($id); if (!$r) fail('gone', 404);
    if ($a === 's_png') { $f = "$DATA/sharepng_$id.png"; if (!file_exists($f)) fail('none', 404); header('Content-Type: image/png'); header('Cache-Control: public, max-age=604800'); readfile($f); exit; }
    if ($a === 's_img') { $it = safe_id((string) ($_GET['item'] ?? '')); if (!in_array($it, $r['items'] ?? [], true)) fail('bad', 404); emby_img($it, '', 500); }
    // s_up: das Handy lädt das gezeichnete Bild einmalig hoch (für die Link-Vorschau)
    $f = "$DATA/sharepng_$id.png"; if (file_exists($f)) out(['ok' => true, 'had' => true]);
    $raw = file_get_contents('php://input', false, null, 0, 3 * 1024 * 1024 + 10);
    if (!$raw || strlen($raw) > 3 * 1024 * 1024 || substr($raw, 0, 8) !== "\x89PNG\r\n\x1a\n") fail('bad', 400, 'Kein PNG');
    $sz = @getimagesizefromstring($raw); if (!$sz || $sz[0] !== 1080 || $sz[1] !== 1350) fail('bad', 400, 'Falsches Format');
    file_put_contents($f, $raw, LOCK_EX); out(['ok' => true]);
}
if ($a === '') { header('Content-Type: text/plain; charset=utf-8'); echo "midnox Emby Community – ok\n"; exit; }

// ===== „Was gucken wir heute?“ – Handy-Seite, ohne Emby-Login (nur mit Rundencode) =====
function match_code(): string { $c = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', (string) ($_GET['c'] ?? body()['c'] ?? '')) ?? ''); return substr($c, 0, 6); }
function match_view(array $r, string $pid): array {
    $t = time(); $players = []; $n = count($r['items']);
    foreach ($r['players'] as $id => $p) $players[] = ['id' => $id, 'name' => $p['name'], 'n' => count($p['votes']), 'tv' => !empty($p['tv']), 'on' => ($p['last'] ?? 0) > $t - 20, 'me' => $id === $pid];
    $likes = []; foreach ($r['players'] as $p) foreach ($p['votes'] as $id => $v) if ($v) $likes[$id] = ($likes[$id] ?? 0) + 1;
    $np = count($r['players']); $matches = [];
    if ($np > 0) foreach ($r['items'] as $it) if (($likes[$it['id']] ?? 0) >= $np) $matches[] = $it['id'];
    $v = ['code' => $r['code'], 'host' => $r['hostName'], 'state' => $r['state'], 'total' => $n, 'players' => $players, 'likes' => (object) $likes, 'matches' => $matches, 'ver' => $r['ver']];
    if ($pid !== '' && isset($r['players'][$pid])) { $v['votes'] = (object) $r['players'][$pid]['votes']; if ($r['state'] !== 'lobby') $v['items'] = $r['items']; }
    return $v;
}
if (strpos($a, 'm_') === 0) {
    $code = match_code(); if (strlen($code) < 4) fail('code', 400, 'Code fehlt');
    $b = body(); $pid = safe_id((string) ($b['p'] ?? $_GET['p'] ?? ''));
    if (!file_exists("$DATA/match_$code.json")) fail('gone', 404, 'Diese Runde gibt es nicht (mehr).');
    if ($a === 'm_img') {   // Poster über diesen Server durchreichen (https, Emby muss nicht direkt erreichbar sein)
        $r = load("match_$code"); $id = safe_id((string) ($_GET['id'] ?? ''));
        $ok = false; foreach ($r['items'] ?? [] as $it) if ($it['id'] === $id) { $ok = true; $tag = (string) ($it['tag'] ?? ''); }
        if (!$ok) fail('bad', 404);
        emby_img($id, $tag);
    }
    $res = store("match_$code", function (&$r) use ($a, $b, &$pid) {
        if (!$r || ($r['created'] ?? 0) < time() - MATCH_TTL) return ['err' => 'gone'];
        $t = time();
        if ($a === 'm_join') {
            $name = clean((string) ($b['name'] ?? ''), 24); if ($name === '') return ['err' => 'name'];
            if ($pid === '' || !isset($r['players'][$pid])) {
                if (count($r['players']) >= 12) return ['err' => 'full'];
                foreach ($r['players'] as $p) if (mb_strtolower($p['name']) === mb_strtolower($name)) $name .= ' ' . (count($r['players']) + 1);
                $pid = substr(bin2hex(random_bytes(8)), 0, 12);
                $r['players'][$pid] = ['name' => $name, 'votes' => [], 'last' => $t, 'joined' => $t];
            } else $r['players'][$pid]['name'] = $name;
            $r['ver']++;
        } elseif (($a === 'm_vote' || $a === 'm_leave') && !isset($r['players'][$pid])) return ['err' => 'who'];
        if (isset($r['players'][$pid])) $r['players'][$pid]['last'] = $t;
        if ($a === 'm_vote') {
            if ($r['state'] !== 'vote') return ['err' => 'state'];
            $id = safe_id((string) ($b['id'] ?? '')); $ok = false; foreach ($r['items'] as $it) if ($it['id'] === $id) $ok = true;
            if (!$ok) return ['err' => 'bad'];
            if (!empty($b['undo'])) unset($r['players'][$pid]['votes'][$id]); else $r['players'][$pid]['votes'][$id] = empty($b['v']) ? 0 : 1;
            $r['ver']++;
        }
        if ($a === 'm_leave') { unset($r['players'][$pid]); $r['ver']++; }
        if ($a === 'm_ctl') {   // nur der Fernseher (hat den Gastgeber-Schlüssel)
            if (!hash_equals((string) $r['key'], (string) ($b['key'] ?? ''))) return ['err' => 'host'];
            $st = (string) ($b['state'] ?? ''); if (in_array($st, ['lobby', 'vote', 'done'], true)) { $r['state'] = $st; $r['ver']++; }
            if (!empty($b['kick'])) { unset($r['players'][safe_id((string) $b['kick'])]); $r['ver']++; }
        }
        return ['ok' => true];
    });
    if (!empty($res['err'])) {
        $msg = ['gone' => 'Diese Runde ist vorbei.', 'name' => 'Bitte gib einen Namen ein.', 'full' => 'Die Runde ist voll.', 'who' => 'Bitte neu beitreten.', 'state' => 'Abstimmung läuft gerade nicht.', 'host' => 'Nur der Fernseher darf das.', 'bad' => 'Unbekannter Titel.'][$res['err']] ?? 'Fehler';
        fail($res['err'], $res['err'] === 'gone' ? 404 : 400, $msg);
    }
    $r = load("match_$code"); $v = match_view($r, $pid); $v['p'] = $pid; out($v);
}

$me = me();

switch ($a) {
case 'hello':
    out(['me' => $me, 'now' => now_ms()]);

case 'feed': {
    $t = time();
    $news = array_values(array_filter(load('news'), function ($n) use ($t) { return ($n['until'] ?? 0) > $t; }));
    $chat = load('chat'); $last = $chat ? end($chat) : null;
    $online = [];
    foreach (load('online') as $id => $o) if (($o['t'] ?? 0) > $t - ONLINE_WINDOW && $id !== $me['id']) $online[] = ['id' => $id, 'name' => $o['n'], 'w' => $o['w'] ?? ''];
    $parties = [];
    global $DATA;
    foreach (glob("$DATA/party_*.json") ?: [] as $p) {
        $r = json_decode((string) @file_get_contents($p), true); if (!is_array($r)) continue;
        $alive = array_filter($r['members'] ?? [], function ($m) use ($t) { return ($m['last'] ?? 0) > $t - PARTY_IDLE; });
        if (!$alive && ($r['created'] ?? 0) < $t - 120) { @unlink($p); continue; }
        if (($r['created'] ?? 0) < $t - 6 * 3600) { @unlink($p); continue; }
        $parties[] = ['id' => $r['id'], 'item' => $r['item'], 'meta' => $r['meta'], 'host' => $r['hostName'], 'hostId' => $r['host'], 'n' => count($alive),
            'names' => array_values(array_map(function ($m) { return $m['name']; }, $alive)), 'created' => $r['created']];
    }
    out(['now' => now_ms(), 'me' => $me, 'news' => $news, 'chatLast' => $last ? $last['id'] : 0,
        'chatLastFrom' => $last ? $last['uid'] : '', 'online' => $online, 'parties' => $parties]);
}

case 'rate': {
    $b = body(); $item = safe_id((string) ($b['item'] ?? '')); $stars = (int) ($b['stars'] ?? 0);
    if ($item === '' || $stars < 0 || $stars > 10) fail('bad', 400, 'Ungültige Bewertung');
    $comment = clean((string) ($b['comment'] ?? ''), 280); $meta = meta_in($b['meta'] ?? []);
    rate_limit('rate', $me['id'], 1.0);
    $r = store('ratings', function (&$d) use ($item, $stars, $comment, $meta, $me) {
        if ($stars === 0) { unset($d[$item]['by'][$me['id']]); if (empty($d[$item]['by'])) unset($d[$item]); }
        else {
            if (!isset($d[$item])) $d[$item] = ['meta' => $meta, 'by' => []];
            if ($meta['name'] !== '') $d[$item]['meta'] = $meta;
            $d[$item]['by'][$me['id']] = ['s' => $stars, 'c' => $comment, 'n' => $me['name'], 't' => time()];
        }
        return summary($d[$item] ?? null, $me['id']);
    });
    out(['ok' => true, 'item' => $item, 'r' => $r]);
}

case 'ratings': {
    $ids = array_slice(array_filter(array_map('safe_id', explode(',', (string) ($_GET['ids'] ?? '')))), 0, 200);
    $all = load('ratings'); $res = [];
    foreach ($ids as $id) if (isset($all[$id])) $res[$id] = summary($all[$id], $me['id']);
    out(['r' => $res]);
}

case 'item': {
    $id = safe_id((string) ($_GET['id'] ?? '')); $all = load('ratings'); $list = [];
    foreach ($all[$id]['by'] ?? [] as $uid => $v) $list[] = ['uid' => $uid, 'name' => $v['n'], 'stars' => $v['s'], 'comment' => $v['c'], 't' => $v['t'], 'me' => $uid === $me['id']];
    usort($list, function ($x, $y) { return $y['t'] <=> $x['t']; });
    out(['r' => isset($all[$id]) ? summary($all[$id], $me['id']) : null, 'list' => $list]);
}

case 'top': {
    $all = load('ratings'); $top = []; $recent = [];
    foreach ($all as $id => $it) {
        $s = summary($it, $me['id']); if (!$s) continue;
        $top[] = ['id' => $id, 'meta' => $it['meta'], 'avg' => $s['avg'], 'n' => $s['n'], 'mine' => $s['mine']];
        foreach ($it['by'] as $uid => $v) $recent[] = ['id' => $id, 'meta' => $it['meta'], 'name' => $v['n'], 'stars' => $v['s'], 'comment' => $v['c'], 't' => $v['t']];
    }
    usort($top, function ($x, $y) { return [$y['avg'], $y['n']] <=> [$x['avg'], $x['n']]; });
    usort($recent, function ($x, $y) { return $y['t'] <=> $x['t']; });
    out(['top' => array_slice($top, 0, 60), 'recent' => array_slice($recent, 0, 40)]);
}

case 'chat': {
    $since = (int) ($_GET['since'] ?? 0); $chat = load('chat');
    $msgs = array_values(array_filter($chat, function ($m) use ($since) { return $m['id'] > $since; }));
    if ($since === 0) $msgs = array_slice($msgs, -80);
    out(['msgs' => $msgs, 'now' => now_ms()]);
}

case 'say': {
    $b = body(); $text = clean((string) ($b['text'] ?? ''), 500);
    $item = null;
    if (!empty($b['item']) && is_array($b['item'])) { $id = safe_id((string) ($b['item']['id'] ?? '')); if ($id !== '') $item = array_merge(['id' => $id], meta_in($b['item'])); }
    if ($text === '' && !$item) fail('bad', 400, 'Leere Nachricht');
    rate_limit('say', $me['id'], 1.5);
    $m = store('chat', function (&$d) use ($text, $item, $me) {
        $last = $d ? end($d) : null; $id = $last ? $last['id'] + 1 : 1;
        $m = ['id' => $id, 'uid' => $me['id'], 'name' => $me['name'], 'admin' => $me['admin'], 'text' => $text, 'item' => $item, 't' => time()];
        $d[] = $m; if (count($d) > CHAT_KEEP) $d = array_slice($d, -CHAT_KEEP);
        return $m;
    });
    out(['ok' => true, 'msg' => $m]);
}

case 'chat_del': {
    $id = (int) (body()['id'] ?? 0);
    $ok = store('chat', function (&$d) use ($id, $me) {
        foreach ($d as $k => $m) if ($m['id'] === $id && ($m['uid'] === $me['id'] || $me['admin'])) { $d[$k]['text'] = ''; $d[$k]['item'] = null; $d[$k]['del'] = true; return true; }
        return false;
    });
    out(['ok' => $ok]);
}

case 'news': {
    need_admin($me); $b = body();
    $text = clean((string) ($b['text'] ?? ''), 400); if ($text === '') fail('bad', 400, 'Text fehlt');
    $kind = in_array($b['kind'] ?? '', ['banner', 'popup'], true) ? $b['kind'] : 'banner';
    $min = max(1, min(60 * 24 * 7, (int) ($b['minutes'] ?? 60)));
    $n = store('news', function (&$d) use ($text, $kind, $min, $me) {
        $t = time(); $d = array_values(array_filter($d, function ($x) use ($t) { return ($x['until'] ?? 0) > $t - 86400; }));
        $id = $t * 1000 + random_int(0, 999);
        $n = ['id' => $id, 'text' => $text, 'kind' => $kind, 'from' => $me['name'], 't' => $t, 'until' => $t + $min * 60];
        $d[] = $n; return $n;
    });
    out(['ok' => true, 'news' => $n]);
}

case 'news_del': {
    need_admin($me); $id = (int) (body()['id'] ?? 0);
    store('news', function (&$d) use ($id) { foreach ($d as $k => $n) if ($n['id'] === $id) $d[$k]['until'] = 0; });
    out(['ok' => true]);
}

case 'online': {
    $t = time(); $list = [];
    foreach (load('online') as $id => $o) $list[] = ['id' => $id, 'name' => $o['n'], 'admin' => !empty($o['a']), 'w' => $o['w'] ?? '', 'ago' => $t - ($o['t'] ?? 0)];
    usort($list, function ($x, $y) { return $x['ago'] <=> $y['ago']; });
    out(['list' => $list]);
}

// ===== Watch-Party =====
case 'log': {
    $b = body(); $id = safe_id((string) ($b['id'] ?? '')); $secs = (int) ($b['secs'] ?? 0);
    if ($id === '' || $secs < 20 || $secs > 6 * 3600) out(['ok' => false]);
    rate_limit('log', $me['id'], 2);
    $t = time(); $y = date('Y', $t);
    $e = ['t' => $t, 'id' => $id, 'k' => substr(preg_replace('/[^A-Za-z]/', '', (string) ($b['type'] ?? '')), 0, 12), 's' => $secs,
        'n' => clean((string) ($b['name'] ?? ''), 80), 'sr' => safe_id((string) ($b['series'] ?? '')), 'sn' => clean((string) ($b['seriesName'] ?? ''), 80),
        'g' => array_slice(array_map(function ($g) { return clean((string) $g, 30); }, (array) ($b['genres'] ?? [])), 0, 4),
        'y' => (int) ($b['year'] ?? 0), 'd' => empty($b['done']) ? 0 : 1, 'p' => empty($b['party']) ? 0 : 1, 'h' => (int) ($b['hour'] ?? -1), 'w' => (int) ($b['wd'] ?? -1), 'day' => substr(preg_replace('/[^0-9-]/', '', (string) ($b['day'] ?? '')), 0, 10)];
    store('log_' . $me['id'] . '_' . $y, function (&$d) use ($e) { $d[] = $e; if (count($d) > LOG_MAX) $d = array_slice($d, -LOG_MAX); });
    out(['ok' => true]);
}

case 'mystats': {
    $y = (string) (int) ($_GET['year'] ?? date('Y'));
    $cnt = load('cnt_' . $me['id']);
    $rat = 0; $rat_y = 0; $best = null;
    foreach (load('ratings') as $id => $it) if (isset($it['by'][$me['id']])) { $x = $it['by'][$me['id']]; $rat++; if (date('Y', $x['t']) === $y) { $rat_y++; if (!$best || $x['s'] > $best['s'] || ($x['s'] === $best['s'] && $x['t'] > $best['t'])) $best = ['id' => $id, 's' => $x['s'], 'c' => $x['c'], 't' => $x['t'], 'name' => $it['meta']['name'] ?? '']; } }
    $chat = 0; $chat_y = 0; $recs = 0;
    foreach (load('chat') as $m) if (($m['uid'] ?? '') === $me['id'] && empty($m['del'])) { $chat++; if (date('Y', $m['t']) === $y) $chat_y++; if (!empty($m['item'])) $recs++; }
    $ach = load('ach_' . $me['id']);
    out(['log' => load('log_' . $me['id'] . '_' . $y), 'cnt' => ['all' => (object) ($cnt['all'] ?? []), 'year' => (object) ($cnt[$y] ?? [])],
        'ratings' => ['all' => $rat, 'year' => $rat_y, 'best' => $best], 'chat' => ['all' => $chat, 'year' => $chat_y, 'recs' => $recs], 'got' => (object) ($ach['got'] ?? [])]);
}

case 'ach': {
    $b = body(); rate_limit('ach', $me['id'], 3);
    $got = []; foreach ((array) ($b['got'] ?? []) as $k => $v) { $k = preg_replace('/[^a-z0-9_]/', '', (string) $k); if ($k !== '' && is_array($v)) $got[$k] = ['lv' => max(1, min(4, (int) ($v['lv'] ?? 1))), 't' => (int) ($v['t'] ?? time())]; }
    $sum = []; foreach ((array) ($b['sum'] ?? []) as $k => $v) { $k = preg_replace('/[^a-zA-Z0-9_]/', '', (string) $k); if ($k !== '' && count($sum) < 24) $sum[$k] = is_numeric($v) ? round((float) $v, 1) : clean((string) $v, 60); }
    $y = (string) (int) ($b['year'] ?? date('Y'));
    store('ach_' . $me['id'], function (&$d) use ($got, $sum, $y, $me) {
        foreach ($got as $k => $v) if (!isset($d['got'][$k]) || $d['got'][$k]['lv'] < $v['lv']) $d['got'][$k] = $v;
        if ($sum) $d['sum'][$y] = $sum; $d['name'] = $me['name']; $d['t'] = time();
    });
    out(['ok' => true]);
}

case 'board': {
    $y = (string) (int) ($_GET['year'] ?? date('Y')); $list = [];
    global $DATA;
    foreach (glob("$DATA/ach_*.json") ?: [] as $f) {
        $d = json_decode((string) @file_get_contents($f), true); if (!is_array($d)) continue;
        $uid = substr(basename($f, '.json'), 4); $pts = 0; foreach ($d['got'] ?? [] as $g) $pts += $g['lv'];
        $list[] = ['uid' => $uid, 'name' => $d['name'] ?? '?', 'me' => $uid === $me['id'], 'n' => count($d['got'] ?? []), 'pts' => $pts, 'sum' => (object) ($d['sum'][$y] ?? [])];
    }
    usort($list, function ($a, $b) { return $b['pts'] <=> $a['pts']; });
    out(['list' => $list]);
}

case 'share_new': {
    $b = body(); rate_limit('share', $me['id'], 1);
    $kind = in_array($b['kind'] ?? '', ['ach', 'badges', 'wrapped'], true) ? $b['kind'] : 'ach';
    $c = (array) ($b['card'] ?? []);
    $str = function ($v, $n) { return clean((string) $v, $n); };
    $card = ['kind' => $kind, 'name' => $me['name'], 'year' => (int) ($c['year'] ?? date('Y')), 'title' => $str($c['title'] ?? '', 60), 'sub' => $str($c['sub'] ?? '', 120),
        'icon' => $str($c['icon'] ?? '', 8), 'lv' => max(0, min(4, (int) ($c['lv'] ?? 0))), 'kicker' => $str($c['kicker'] ?? '', 50),
        'stats' => [], 'medals' => [], 'poster' => '', 'posterName' => $str($c['posterName'] ?? '', 60), 'persona' => $str($c['persona'] ?? '', 40), 'personaE' => $str($c['personaE'] ?? '', 8), 'pts' => (int) ($c['pts'] ?? 0)];
    foreach (array_slice((array) ($c['stats'] ?? []), 0, 6) as $x) if (is_array($x)) $card['stats'][] = ['v' => $str($x['v'] ?? '', 14), 'l' => $str($x['l'] ?? '', 30)];
    foreach (array_slice((array) ($c['medals'] ?? []), 0, 12) as $x) if (is_array($x)) $card['medals'][] = ['i' => $str($x['i'] ?? '', 8), 'n' => $str($x['n'] ?? '', 24), 'lv' => max(1, min(4, (int) ($x['lv'] ?? 1)))];
    $items = []; $p = safe_id((string) ($c['poster'] ?? '')); if ($p !== '') { $card['poster'] = $p; $items[] = $p; }
    $id = substr(str_replace(['+', '/', '='], '', base64_encode(random_bytes(9))), 0, 10);
    $og_t = $kind === 'wrapped' ? $me['name'] . 's Jahr ' . $card['year'] . ' bei midnox' : ($kind === 'badges' ? $me['name'] . ' hat ' . count($card['medals']) . ' Abzeichen' : $me['name'] . ' hat „' . $card['title'] . '“ freigeschaltet');
    $og_d = $kind === 'ach' ? ($card['sub'] . ' · ' . (['', 'Bronze', 'Silber', 'Gold', 'Platin'][$card['lv']] ?? '')) : $card['sub'];
    store('share_' . $id, function (&$d) use ($card, $items, $me, $og_t, $og_d) { $d = ['t' => time(), 'uid' => $me['id'], 'card' => $card, 'items' => $items, 'og_t' => $og_t, 'og_d' => $og_d]; });
    foreach (glob("$DATA/share_*.json") ?: [] as $f) if (filemtime($f) < time() - SHARE_TTL) { @unlink($f); @unlink(str_replace(['share_', '.json'], ['sharepng_', '.png'], $f)); }
    out(['ok' => true, 'id' => $id]);
}

case 'match_new': {
    $b = body(); rate_limit('match', $me['id'], 5);
    $items = [];
    foreach ((array) ($b['items'] ?? []) as $it) {
        if (!is_array($it)) continue; $id = safe_id((string) ($it['id'] ?? '')); if ($id === '') continue;
        $items[] = ['id' => $id, 'name' => clean((string) ($it['name'] ?? ''), 120), 'year' => (int) ($it['year'] ?? 0), 'type' => clean((string) ($it['type'] ?? ''), 12),
            'ov' => clean((string) ($it['ov'] ?? ''), 600), 'genres' => clean((string) ($it['genres'] ?? ''), 80), 'rating' => round((float) ($it['rating'] ?? 0), 1),
            'min' => (int) ($it['min'] ?? 0), 'fsk' => clean((string) ($it['fsk'] ?? ''), 10), 'tag' => clean((string) ($it['tag'] ?? ''), 64)];
        if (count($items) >= MATCH_MAX) break;
    }
    if (count($items) < 3) fail('bad', 400, 'Zu wenige Titel');
    foreach (glob("$DATA/match_*.json") ?: [] as $f) if (filemtime($f) < time() - MATCH_TTL) @unlink($f);
    $abc = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    do { $code = ''; for ($i = 0; $i < 4; $i++) $code .= $abc[random_int(0, strlen($abc) - 1)]; } while (file_exists("$DATA/match_$code.json"));
    $key = bin2hex(random_bytes(10)); $pid = '';
    $r = ['code' => $code, 'key' => $key, 'host' => $me['id'], 'hostName' => $me['name'], 'created' => time(), 'state' => 'lobby', 'ver' => 1, 'items' => $items, 'players' => []];
    if (!empty($b['tv'])) { $pid = 'tv' . substr(bin2hex(random_bytes(5)), 0, 8); $r['players'][$pid] = ['name' => clean((string) ($b['tvName'] ?? $me['name']), 24), 'tv' => true, 'votes' => [], 'last' => time(), 'joined' => time()]; }
    store("match_$code", function (&$d) use ($r) { $d = $r; });
    bump($me['id'], 'match_host');
    out(['ok' => true, 'code' => $code, 'key' => $key, 'p' => $pid, 'view' => match_view($r, $pid)]);
}

case 'party_new': {
    $b = body(); $item = safe_id((string) ($b['item'] ?? '')); if ($item === '') fail('bad', 400, 'Titel fehlt');
    rate_limit('party', $me['id'], 3);
    $id = substr(bin2hex(random_bytes(6)), 0, 10);
    $r = ['id' => $id, 'item' => $item, 'meta' => meta_in($b['meta'] ?? []), 'host' => $me['id'], 'hostName' => $me['name'], 'created' => time(),
        'open' => !empty($b['open']), 'ver' => 1,
        'state' => ['playing' => false, 'pos' => max(0.0, (float) ($b['pos'] ?? 0)), 'at' => now_ms(), 'by' => $me['name']],
        'members' => [$me['id'] => ['name' => $me['name'], 'last' => time(), 'ready' => false, 'buf' => false, 'pos' => 0]], 'react' => [], 'seq' => 0];
    store('party_' . $id, function (&$d) use ($r) { $d = $r; });
    bump($me['id'], 'party_host');
    out(['ok' => true, 'party' => party_view($r, $me['id'], 0)]);
}

case 'party_join': case 'party_leave': case 'party_state': case 'party_react': case 'party_open': {
    $b = body(); $id = safe_id((string) ($b['id'] ?? ''));
    global $DATA; if ($id === '' || !file_exists("$DATA/party_$id.json")) fail('gone', 404, 'Diese Watch-Party ist vorbei');
    if ($a === 'party_react') rate_limit('react', $me['id'], 0.4);
    if ($a === 'party_join') { $pr = load('party_' . $id); if (!isset($pr['members'][$me['id']])) bump($me['id'], 'party_join'); }
    $res = store('party_' . $id, function (&$r) use ($a, $b, $me) {
        if (!$r) return null;
        $t = time();
        if ($a === 'party_join') {
            $r['members'][$me['id']] = ['name' => $me['name'], 'last' => $t, 'ready' => false, 'buf' => false, 'pos' => 0];
            $r['react'][] = ['seq' => ++$r['seq'], 'uid' => $me['id'], 'name' => $me['name'], 'e' => 'join', 't' => now_ms()];
        } elseif ($a === 'party_leave') {
            unset($r['members'][$me['id']]);
            $r['react'][] = ['seq' => ++$r['seq'], 'uid' => $me['id'], 'name' => $me['name'], 'e' => 'leave', 't' => now_ms()];
            if ($r['host'] === $me['id']) {   // Gastgeber geht: an den nächsten übergeben oder beenden
                $alive = array_filter($r['members'], function ($m) use ($t) { return $m['last'] > $t - PARTY_IDLE; });
                if ($alive) { $nid = array_key_first($alive); $r['host'] = $nid; $r['hostName'] = $alive[$nid]['name']; $r['ver']++; }
                else { $r['ended'] = true; }
            }
        } elseif ($a === 'party_state') {
            if ($r['host'] !== $me['id'] && empty($r['open'])) return ['err' => 'host'];
            $r['state'] = ['playing' => !empty($b['playing']), 'pos' => max(0.0, (float) ($b['pos'] ?? 0)), 'at' => now_ms(), 'by' => $me['name']];
            $r['ver']++;
        } elseif ($a === 'party_open') {
            if ($r['host'] !== $me['id']) return ['err' => 'host'];
            $r['open'] = !empty($b['open']); $r['ver']++;
        } else {
            $e = clean((string) ($b['e'] ?? ''), 60); if ($e === '') return ['err' => 'bad'];
            $r['react'][] = ['seq' => ++$r['seq'], 'uid' => $me['id'], 'name' => $me['name'], 'e' => $e, 't' => now_ms()];
        }
        if (count($r['react']) > 60) $r['react'] = array_slice($r['react'], -60);
        return ['ok' => true];
    });
    if (!$res) fail('gone', 404, 'Diese Watch-Party ist vorbei');
    if (isset($res['err'])) fail($res['err'], 403, 'Nur der Gastgeber kann steuern');
    $r = load('party_' . $id);
    if (!empty($r['ended'])) { @unlink("$DATA/party_$id.json"); out(['ok' => true, 'ended' => true]); }
    out(['ok' => true, 'party' => party_view($r, $me['id'], (int) ($b['since'] ?? 0))]);
}

case 'party': {
    $id = safe_id((string) ($_GET['id'] ?? ''));
    global $DATA; if ($id === '' || !file_exists("$DATA/party_$id.json")) fail('gone', 404, 'Diese Watch-Party ist vorbei');
    $since = (int) ($_GET['since'] ?? 0);
    $r = store('party_' . $id, function (&$r) use ($me) {
        if (!$r) return null;
        if (isset($r['members'][$me['id']])) {
            $m = &$r['members'][$me['id']];
            $m['last'] = time();
            if (isset($_GET['ready'])) $m['ready'] = $_GET['ready'] === '1';
            if (isset($_GET['buf'])) $m['buf'] = $_GET['buf'] === '1';
            if (isset($_GET['pos'])) $m['pos'] = (float) $_GET['pos'];
        }
        return $r;
    });
    if (!$r) fail('gone', 404, 'Diese Watch-Party ist vorbei');
    out(['party' => party_view($r, $me['id'], $since)]);
}

default:
    fail('unknown', 404, 'Unbekannte Aktion');
}

function summary(?array $it, string $uid): ?array {
    if (!$it || empty($it['by'])) return null;
    $sum = 0; $n = 0; foreach ($it['by'] as $v) { $sum += $v['s']; $n++; }
    return ['avg' => round($sum / $n, 1), 'n' => $n, 'mine' => $it['by'][$uid]['s'] ?? 0, 'myComment' => $it['by'][$uid]['c'] ?? ''];
}
function party_view(array $r, string $uid, int $since): array {
    $t = time(); $mem = [];
    foreach ($r['members'] as $id => $m) if ($m['last'] > $t - PARTY_IDLE) $mem[] = ['id' => $id, 'name' => $m['name'], 'ready' => $m['ready'], 'buf' => $m['buf'], 'pos' => $m['pos'], 'host' => $id === $r['host']];
    return ['id' => $r['id'], 'item' => $r['item'], 'meta' => $r['meta'], 'host' => $r['host'], 'hostName' => $r['hostName'], 'isHost' => $r['host'] === $uid,
        'open' => !empty($r['open']), 'ver' => $r['ver'], 'state' => $r['state'], 'members' => $mem,
        'react' => array_values(array_filter($r['react'], function ($x) use ($since) { return $x['seq'] > $since; })), 'seq' => $r['seq'], 'now' => now_ms()];
}
