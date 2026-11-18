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

$a = (string) ($_GET['a'] ?? '');
if ($a === '') { header('Content-Type: text/plain; charset=utf-8'); echo "midnox Emby Community – ok\n"; exit; }
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
case 'party_new': {
    $b = body(); $item = safe_id((string) ($b['item'] ?? '')); if ($item === '') fail('bad', 400, 'Titel fehlt');
    rate_limit('party', $me['id'], 3);
    $id = substr(bin2hex(random_bytes(6)), 0, 10);
    $r = ['id' => $id, 'item' => $item, 'meta' => meta_in($b['meta'] ?? []), 'host' => $me['id'], 'hostName' => $me['name'], 'created' => time(),
        'open' => !empty($b['open']), 'ver' => 1,
        'state' => ['playing' => false, 'pos' => max(0.0, (float) ($b['pos'] ?? 0)), 'at' => now_ms(), 'by' => $me['name']],
        'members' => [$me['id'] => ['name' => $me['name'], 'last' => time(), 'ready' => false, 'buf' => false, 'pos' => 0]], 'react' => [], 'seq' => 0];
    store('party_' . $id, function (&$d) use ($r) { $d = $r; });
    out(['ok' => true, 'party' => party_view($r, $me['id'], 0)]);
}

case 'party_join': case 'party_leave': case 'party_state': case 'party_react': case 'party_open': {
    $b = body(); $id = safe_id((string) ($b['id'] ?? ''));
    global $DATA; if ($id === '' || !file_exists("$DATA/party_$id.json")) fail('gone', 404, 'Diese Watch-Party ist vorbei');
    if ($a === 'party_react') rate_limit('react', $me['id'], 0.4);
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
