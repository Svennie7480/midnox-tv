<?php
/* midnox Infoscreen – eine Datei: Server + Editor (PC) + Player (Fernseher)
   Hochladen nach z. B. midnox.de/midnoxtv/infoscreen/index.php – Ordner data/ und media/ legt die Datei selbst an. */
define('MXI_VER', '1.0');
define('DIR', __DIR__);
define('DATA', DIR . '/data');
define('MEDIA', DIR . '/media');
define('MAXFILE', 600 * 1024 * 1024);
define('QUOTA', 3 * 1024 * 1024 * 1024);
const EXT_IMG = ['jpg', 'jpeg', 'png', 'gif', 'webp'];
const EXT_VID = ['mp4', 'webm', 'm4v', 'mov'];
const THEMES = ['auto', 'midnox', 'clean', 'weihnachten', 'silvester', 'ostern', 'halloween', 'valentin', 'fruehling', 'sommer', 'herbst', 'winter'];
const STYPES = ['image', 'video', 'text', 'menu', 'clock', 'weather', 'qr'];

error_reporting(0);
@mkdir(DATA, 0755, true); @mkdir(MEDIA, 0755, true); @mkdir(DATA . '/sess', 0700, true);
if (!is_file(DATA . '/.htaccess')) {
    @file_put_contents(DATA . '/.htaccess', "Require all denied\n<IfModule !mod_authz_core.c>\nDeny from all\n</IfModule>\n");
    @file_put_contents(DATA . '/index.html', '');
    @file_put_contents(MEDIA . '/.htaccess', "<FilesMatch \"\\.(php|phtml|php3|php4|php5|phar|cgi|pl|py|sh)$\">\nRequire all denied\n</FilesMatch>\nOptions -Indexes\n");
    @file_put_contents(MEDIA . '/index.html', '');
}

/* ---------- Hilfen ---------- */
function out($a, $code = 200) {
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($a, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function fname($n) { return preg_match('/^[a-z0-9_]{1,60}$/', $n) ? DATA . "/$n.php" : null; }
function jr($n) {
    $f = fname($n); if (!$f || !is_file($f)) return null;
    $s = @file_get_contents($f); if ($s === false) return null;
    $p = strpos($s, "\n"); return json_decode(substr($s, $p === false ? 0 : $p + 1), true);
}
function jw($n, $a) {
    $f = fname($n); if (!$f) return false;
    $tmp = $f . '.' . bin2hex(random_bytes(3));
    file_put_contents($tmp, "<?php exit;?>\n" . json_encode($a, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), LOCK_EX);
    return rename($tmp, $f);
}
function jdel($n) { $f = fname($n); if ($f && is_file($f)) @unlink($f); }
function cs($v, $n = 400) { return mb_substr(trim((string)$v), 0, $n); }
function rid($n = 6) { return substr(bin2hex(random_bytes($n)), 0, $n); }
function body() { static $b = null; if ($b === null) { $b = json_decode(file_get_contents('php://input'), true); if (!is_array($b)) $b = []; } return $b; }
function csrf() { if (($_SERVER['HTTP_X_MX'] ?? '') !== '1') out(['err' => 'csrf'], 403); }

session_name('mxi');
session_save_path(DATA . '/sess');
session_set_cookie_params(['lifetime' => 0, 'path' => '/', 'httponly' => true, 'samesite' => 'Lax', 'secure' => !empty($_SERVER['HTTPS'])]);
function sess() { if (session_status() !== PHP_SESSION_ACTIVE) session_start(); }

function accounts() { $a = jr('accounts'); return is_array($a) ? $a : ['accounts' => []]; }
function me() {
    sess(); if (empty($_SESSION['u'])) return null;
    $all = accounts(); return $all['accounts'][$_SESSION['u']] ?? null;
}
function need() { $m = me(); if (!$m) out(['err' => 'login'], 401); return $m; }
function acting($m) {   // Hauptkonto darf als Kunde arbeiten
    if (!empty($m['master']) && !empty($_SESSION['as'])) { $all = accounts(); if (isset($all['accounts'][$_SESSION['as']])) return $_SESSION['as']; }
    return $m['id'];
}
function adata($aid) { $d = jr('d_' . $aid); return is_array($d) ? $d : ['screens' => []]; }
function mediaUrlOk($u, $aid) { return $u === '' || preg_match('~^media/' . preg_quote($aid, '~') . '/[a-z0-9_.-]+$~', $u); }
function hhmm($v) { return preg_match('/^([01]\d|2[0-3]):[0-5]\d$/', (string)$v) ? $v : ''; }
function ymd($v) { return preg_match('/^\d{4}-\d{2}-\d{2}$/', (string)$v) ? $v : ''; }

function clean_slide($s, $aid) {
    if (!is_array($s)) return null;
    $t = $s['type'] ?? ''; if (!in_array($t, STYPES, true)) return null;
    $o = ['id' => preg_replace('/[^a-z0-9]/', '', strtolower($s['id'] ?? '')) ?: rid(6), 'type' => $t, 'on' => !empty($s['on']),
        'name' => cs($s['name'] ?? '', 80), 'dur' => max(3, min(900, (int)($s['dur'] ?? 10)))];
    $sc = $s['sch'] ?? [];
    $days = []; foreach (($sc['days'] ?? []) as $d) { $d = (int)$d; if ($d >= 0 && $d <= 6) $days[$d] = $d; }
    $o['sch'] = ['use' => !empty($sc['use']), 'from' => ymd($sc['from'] ?? ''), 'to' => ymd($sc['to'] ?? ''), 'days' => array_values($days), 't1' => hhmm($sc['t1'] ?? ''), 't2' => hhmm($sc['t2'] ?? '')];
    $m = function ($k) use ($s, $aid) { $v = cs($s[$k] ?? '', 200); return mediaUrlOk($v, $aid) ? $v : ''; };
    switch ($t) {
        case 'image': $o += ['src' => $m('src'), 'fit' => ($s['fit'] ?? '') === 'cover' ? 'cover' : 'contain', 'kb' => !empty($s['kb']), 'cap' => cs($s['cap'] ?? '', 200)]; break;
        case 'video': $o += ['src' => $m('src'), 'sound' => !empty($s['sound']), 'fit' => ($s['fit'] ?? '') === 'cover' ? 'cover' : 'contain', 'cap' => cs($s['cap'] ?? '', 200)]; break;
        case 'text': $o += ['head' => cs($s['head'] ?? '', 200), 'body' => cs($s['body'] ?? '', 1500), 'badge' => cs($s['badge'] ?? '', 40), 'align' => in_array($s['align'] ?? '', ['left', 'center'], true) ? $s['align'] : 'center', 'bg' => $m('bg')]; break;
        case 'menu':
            $items = []; foreach (array_slice($s['items'] ?? [], 0, 80) as $i) { if (!is_array($i)) continue; $items[] = ['n' => cs($i['n'] ?? '', 80), 'd' => cs($i['d'] ?? '', 140), 'p' => cs($i['p'] ?? '', 20), 'h' => !empty($i['h'])]; }
            $o += ['head' => cs($s['head'] ?? '', 120), 'sub' => cs($s['sub'] ?? '', 200), 'items' => $items, 'foot' => cs($s['foot'] ?? '', 300), 'bg' => $m('bg')]; break;
        case 'clock': $o += ['head' => cs($s['head'] ?? '', 120)]; break;
        case 'weather': $o += ['city' => cs($s['city'] ?? '', 80), 'lat' => round((float)($s['lat'] ?? 0), 4), 'lon' => round((float)($s['lon'] ?? 0), 4)]; break;
        case 'qr': $o += ['head' => cs($s['head'] ?? '', 120), 'url' => cs($s['url'] ?? '', 500), 'cap' => cs($s['cap'] ?? '', 300)]; break;
    }
    return $o;
}
function clean_set($s, $aid) {
    $s = is_array($s) ? $s : [];
    $acc = cs($s['accent'] ?? '', 7);
    return [
        'theme' => in_array($s['theme'] ?? 'auto', THEMES, true) ? $s['theme'] : 'auto',
        'accent' => preg_match('/^#[0-9a-fA-F]{6}$/', $acc) ? $acc : '',
        'orient' => in_array((int)($s['orient'] ?? 0), [0, 90, 180, 270], true) ? (int)$s['orient'] : 0,
        'trans' => in_array($s['trans'] ?? 'fade', ['fade', 'slide', 'zoom', 'none'], true) ? $s['trans'] : 'fade',
        'clock' => !empty($s['clock']),
        'tickerOn' => !empty($s['tickerOn']), 'ticker' => cs($s['ticker'] ?? '', 400),
        'logo' => (function () use ($s, $aid) { $v = cs($s['logo'] ?? '', 200); return mediaUrlOk($v, $aid) ? $v : ''; })(),
        'pin' => preg_match('/^\d{4,8}$/', (string)($s['pin'] ?? '')) ? (string)$s['pin'] : '1234',
        'effects' => !isset($s['effects']) || !empty($s['effects']),
        'font' => in_array($s['font'] ?? 'sans', ['sans', 'serif', 'round'], true) ? $s['font'] : 'sans',
    ];
}
function dir_size($d) { $n = 0; foreach (@scandir($d) ?: [] as $f) if ($f[0] !== '.') $n += (int)@filesize("$d/$f"); return $n; }
function rrmdir($d) { foreach (@scandir($d) ?: [] as $f) { if ($f === '.' || $f === '..') continue; is_dir("$d/$f") ? rrmdir("$d/$f") : @unlink("$d/$f"); } @rmdir($d); }
function gc_tmp() {
    foreach (glob(DATA . '/up_*.part') ?: [] as $f) if (filemtime($f) < time() - 86400) @unlink($f);
    foreach (glob(DATA . '/pend_*.php') ?: [] as $f) if (filemtime($f) < time() - 3600) { $x = jr(basename($f, '.php')); if ($x) jdel('pd_' . $x['dev']); @unlink($f); }
}
function screen_find(&$d, $sid) { foreach ($d['screens'] as $i => $s) if ($s['id'] === $sid) return $i; return -1; }
function content_for($scr) {
    $slides = array_values(array_filter($scr['slides'] ?? [], function ($s) { return !empty($s['on']); }));
    return ['ver' => (int)($scr['ver'] ?? 1), 'name' => $scr['name'], 'set' => $scr['set'], 'slides' => $slides, 'now' => (int)(microtime(true) * 1000)];
}

/* ---------- Routing ---------- */
$a = $_GET['a'] ?? '';
function send_cached($body, $type, $cc) {   // ETag: Fernseher kann die Seite auch ohne Internet aus dem Zwischenspeicher laden
    $et = '"' . md5($body) . '"';
    header('ETag: ' . $et); header('Content-Type: ' . $type); header('Cache-Control: ' . $cc);
    if (($_SERVER['HTTP_IF_NONE_MATCH'] ?? '') === $et) { http_response_code(304); exit; }
    echo $body; exit;
}
if ($a === '' || $a === 'editor') send_cached(MXI_EDITOR, 'text/html; charset=utf-8', 'no-cache');
if ($a === 'player') send_cached(MXI_PLAYER, 'text/html; charset=utf-8', 'no-cache');
if ($a === 'qr.js') send_cached(MXI_QR, 'application/javascript; charset=utf-8', 'public, max-age=86400');
if ($a === 'ping') out(['ok' => 1, 'v' => MXI_VER]);

/* --- Geräte (Fernseher) --- */
if ($a === 'reg') {
    gc_tmp();
    $dev = preg_match('/^[a-f0-9]{16}$/', $_GET['dev'] ?? '') ? $_GET['dev'] : bin2hex(random_bytes(8));
    if (jr('dev_' . $dev)) out(['dev' => $dev, 'paired' => true]);
    $pd = jr('pd_' . $dev);
    if ($pd && file_exists(fname('pend_' . $pd['code'])) && $pd['ts'] > time() - 1800) out(['dev' => $dev, 'code' => $pd['code'], 'paired' => false]);
    do { $code = (string)random_int(100000, 999999); } while (jr('pend_' . $code));
    jw('pend_' . $code, ['dev' => $dev, 'ts' => time()]); jw('pd_' . $dev, ['code' => $code, 'ts' => time()]);
    out(['dev' => $dev, 'code' => $code, 'paired' => false]);
}
if ($a === 'poll') {
    $dev = preg_match('/^[a-f0-9]{16}$/', $_GET['dev'] ?? '') ? $_GET['dev'] : '';
    out(['paired' => $dev && jr('dev_' . $dev) ? true : false]);
}
if ($a === 'content') {
    if (!empty($_GET['prev'])) {   // Vorschau im Editor
        $m = need(); $d = adata(acting($m)); $i = screen_find($d, $_GET['prev']);
        if ($i < 0) out(['err' => 'nf'], 404);
        $c = content_for($d['screens'][$i]); $c['aid'] = acting($m); out($c);
    }
    $dev = preg_match('/^[a-f0-9]{16}$/', $_GET['dev'] ?? '') ? $_GET['dev'] : '';
    $dv = $dev ? jr('dev_' . $dev) : null;
    if (!$dv) out(['unpaired' => true]);
    $d = adata($dv['aid']); $i = screen_find($d, $dv['sid']);
    if ($i < 0) { jdel('dev_' . $dev); out(['unpaired' => true]); }
    $dv['seen'] = time(); jw('dev_' . $dev, $dv);
    $c = content_for($d['screens'][$i]);
    if ((int)($_GET['v'] ?? 0) === $c['ver'] && !isset($_GET['full'])) out(['same' => true, 'ver' => $c['ver'], 'now' => $c['now']]);
    out($c);
}

/* --- Konto / Anmeldung --- */
sess();
if ($a === 'me') {
    $m = me(); $all = accounts();
    if (!$m) out(['login' => false, 'setup' => count($all['accounts']) === 0]);
    $r = ['login' => true, 'name' => $m['name'], 'user' => $m['user'], 'master' => !empty($m['master']), 'aid' => acting($m), 'ver' => MXI_VER];
    if (!empty($m['master'])) {
        $r['accounts'] = array_values(array_map(function ($x) { return ['id' => $x['id'], 'name' => $x['name'], 'user' => $x['user'], 'master' => !empty($x['master'])]; }, $all['accounts']));
        $r['as'] = $_SESSION['as'] ?? '';
    }
    out($r);
}
if ($a === 'setup') {
    csrf(); $all = accounts(); if (count($all['accounts'])) out(['err' => 'done'], 403);
    $b = body(); $u = strtolower(cs($b['user'] ?? '', 40)); $p = (string)($b['pw'] ?? '');
    if (!preg_match('/^[a-z0-9._-]{3,40}$/', $u) || strlen($p) < 8) out(['err' => 'Benutzername (3+ Zeichen, a–z/0–9) und Passwort (mind. 8 Zeichen) nötig'], 400);
    $id = 'a1'; $all['accounts'][$id] = ['id' => $id, 'name' => cs($b['name'] ?? 'midnox', 80) ?: 'midnox', 'user' => $u, 'hash' => password_hash($p, PASSWORD_DEFAULT), 'master' => true, 'created' => time()];
    jw('accounts', $all); session_regenerate_id(true); $_SESSION['u'] = $id; out(['ok' => 1]);
}
if ($a === 'login') {
    csrf(); $b = body(); $u = strtolower(cs($b['user'] ?? '', 40)); $p = (string)($b['pw'] ?? '');
    $lk = 'lg_' . md5($_SERVER['REMOTE_ADDR'] ?? ''); $lg = jr($lk) ?: ['n' => 0, 'ts' => time()];
    if ($lg['ts'] < time() - 600) $lg = ['n' => 0, 'ts' => time()];
    if ($lg['n'] >= 8) out(['err' => 'Zu viele Versuche – bitte 10 Minuten warten'], 429);
    foreach (accounts()['accounts'] as $x) if ($x['user'] === $u && password_verify($p, $x['hash'])) { jdel($lk); session_regenerate_id(true); $_SESSION['u'] = $x['id']; unset($_SESSION['as']); out(['ok' => 1]); }
    $lg['n']++; jw($lk, $lg); out(['err' => 'Benutzername oder Passwort falsch'], 401);
}
if ($a === 'logout') { csrf(); $_SESSION = []; session_destroy(); out(['ok' => 1]); }

/* --- ab hier nur angemeldet --- */
$m = need();
if ($_SERVER['REQUEST_METHOD'] === 'POST') csrf();
$aid = acting($m);

switch ($a) {
    case 'pw_change': {
        $b = body(); $all = accounts(); $x = $all['accounts'][$m['id']];
        if (!password_verify((string)($b['old'] ?? ''), $x['hash'])) out(['err' => 'Altes Passwort stimmt nicht'], 400);
        if (strlen((string)($b['new'] ?? '')) < 8) out(['err' => 'Neues Passwort: mind. 8 Zeichen'], 400);
        $all['accounts'][$m['id']]['hash'] = password_hash($b['new'], PASSWORD_DEFAULT); jw('accounts', $all); out(['ok' => 1]);
    }
    case 'acc_add': case 'acc_del': case 'acc_pw': case 'acc_as': {
        if (empty($m['master'])) out(['err' => 'Nur Hauptkonto'], 403);
        $b = body(); $all = accounts();
        if ($a === 'acc_add') {
            $u = strtolower(cs($b['user'] ?? '', 40)); $p = (string)($b['pw'] ?? '');
            if (!preg_match('/^[a-z0-9._-]{3,40}$/', $u) || strlen($p) < 8) out(['err' => 'Benutzername (3+ Zeichen) und Passwort (mind. 8 Zeichen) nötig'], 400);
            foreach ($all['accounts'] as $x) if ($x['user'] === $u) out(['err' => 'Benutzername schon vergeben'], 400);
            do { $id = 'a' . random_int(2, 99999); } while (isset($all['accounts'][$id]));
            $all['accounts'][$id] = ['id' => $id, 'name' => cs($b['name'] ?? '', 80) ?: $u, 'user' => $u, 'hash' => password_hash($p, PASSWORD_DEFAULT), 'master' => false, 'created' => time()];
            jw('accounts', $all); out(['ok' => 1, 'id' => $id]);
        }
        $id = (string)($b['id'] ?? '');
        if ($a === 'acc_as') { if ($id === '') unset($_SESSION['as']); elseif (isset($all['accounts'][$id])) $_SESSION['as'] = $id; out(['ok' => 1]); }
        if (!isset($all['accounts'][$id])) out(['err' => 'Konto nicht gefunden'], 404);
        if ($a === 'acc_pw') { if (strlen((string)($b['pw'] ?? '')) < 8) out(['err' => 'Mind. 8 Zeichen'], 400); $all['accounts'][$id]['hash'] = password_hash($b['pw'], PASSWORD_DEFAULT); jw('accounts', $all); out(['ok' => 1]); }
        if ($a === 'acc_del') {
            if (!empty($all['accounts'][$id]['master'])) out(['err' => 'Hauptkonto kann nicht gelöscht werden'], 400);
            $d = adata($id); foreach ($d['screens'] as $s) foreach ($s['devs'] ?? [] as $dv) jdel('dev_' . $dv);
            jdel('d_' . $id); rrmdir(MEDIA . '/' . $id); unset($all['accounts'][$id]); jw('accounts', $all); unset($_SESSION['as']); out(['ok' => 1]);
        }
    }
    case 'scr_list': {
        $d = adata($aid); $r = [];
        foreach ($d['screens'] as $s) {
            $devs = [];
            foreach ($s['devs'] ?? [] as $dv) { $x = jr('dev_' . $dv); if ($x) $devs[] = ['dev' => $dv, 'seen' => (int)($x['seen'] ?? 0), 'since' => (int)($x['since'] ?? 0)]; }
            $s['devices'] = $devs; unset($s['devs']); $r[] = $s;
        }
        out(['screens' => $r, 'now' => time(), 'used' => dir_size(MEDIA . '/' . $aid), 'quota' => QUOTA]);
    }
    case 'scr_add': {
        $b = body(); $d = adata($aid); if (count($d['screens']) >= 50) out(['err' => 'Maximal 50 Bildschirme'], 400);
        $s = ['id' => 's' . rid(6), 'name' => cs($b['name'] ?? '', 80) ?: 'Neuer Bildschirm', 'set' => clean_set([], $aid), 'slides' => [], 'devs' => [], 'ver' => 1];
        $s['slides'][] = clean_slide(['type' => 'text', 'on' => true, 'name' => 'Willkommen', 'head' => 'Herzlich willkommen!', 'body' => 'Diesen Text und weitere Folien ändern Sie im Editor.', 'dur' => 10], $aid);
        $d['screens'][] = $s; jw('d_' . $aid, $d); out(['ok' => 1, 'id' => $s['id']]);
    }
    case 'scr_save': {
        $b = body(); $d = adata($aid); $i = screen_find($d, (string)($b['id'] ?? ''));
        if ($i < 0) out(['err' => 'Bildschirm nicht gefunden'], 404);
        $in = $b['screen'] ?? []; $sl = [];
        foreach (array_slice($in['slides'] ?? [], 0, 200) as $x) { $c = clean_slide($x, $aid); if ($c) $sl[] = $c; }
        $s = &$d['screens'][$i];
        $s['name'] = cs($in['name'] ?? $s['name'], 80) ?: $s['name']; $s['set'] = clean_set($in['set'] ?? [], $aid); $s['slides'] = $sl; $s['ver'] = (int)($s['ver'] ?? 1) + 1;
        jw('d_' . $aid, $d); out(['ok' => 1, 'ver' => $s['ver'], 'slides' => $sl, 'set' => $s['set']]);
    }
    case 'scr_del': {
        $b = body(); $d = adata($aid); $i = screen_find($d, (string)($b['id'] ?? '')); if ($i < 0) out(['err' => 'nf'], 404);
        foreach ($d['screens'][$i]['devs'] ?? [] as $dv) jdel('dev_' . $dv);
        array_splice($d['screens'], $i, 1); jw('d_' . $aid, $d); out(['ok' => 1]);
    }
    case 'claim': {
        $b = body(); $code = preg_replace('/\D/', '', (string)($b['code'] ?? '')); $d = adata($aid); $i = screen_find($d, (string)($b['sid'] ?? ''));
        if ($i < 0) out(['err' => 'Bildschirm nicht gefunden'], 404);
        $p = strlen($code) === 6 ? jr('pend_' . $code) : null;
        if (!$p || $p['ts'] < time() - 3600) out(['err' => 'Code nicht gefunden oder abgelaufen – am Fernseher steht ein neuer Code'], 404);
        jw('dev_' . $p['dev'], ['aid' => $aid, 'sid' => $d['screens'][$i]['id'], 'since' => time(), 'seen' => 0]);
        $d['screens'][$i]['devs'] = array_values(array_unique(array_merge($d['screens'][$i]['devs'] ?? [], [$p['dev']])));
        jw('d_' . $aid, $d); jdel('pend_' . $code); jdel('pd_' . $p['dev']); out(['ok' => 1]);
    }
    case 'unpair': {
        $b = body(); $dev = (string)($b['dev'] ?? ''); $d = adata($aid);
        foreach ($d['screens'] as &$s) $s['devs'] = array_values(array_diff($s['devs'] ?? [], [$dev]));
        unset($s); $x = jr('dev_' . $dev); if ($x && $x['aid'] === $aid) jdel('dev_' . $dev); jw('d_' . $aid, $d); out(['ok' => 1]);
    }
    case 'media_list': {
        $r = []; $dd = MEDIA . '/' . $aid;
        foreach (@scandir($dd) ?: [] as $f) {
            if ($f[0] === '.') continue; $e = strtolower(pathinfo($f, PATHINFO_EXTENSION));
            $meta = jr('mm_' . $aid . '_' . preg_replace('/[^a-z0-9]/', '', pathinfo($f, PATHINFO_FILENAME))) ?: [];
            $r[] = ['url' => "media/$aid/$f", 'type' => in_array($e, EXT_VID) ? 'video' : 'image', 'size' => filesize("$dd/$f"), 'time' => filemtime("$dd/$f"), 'name' => $meta['name'] ?? $f];
        }
        usort($r, function ($x, $y) { return $y['time'] <=> $x['time']; });
        out(['media' => $r, 'used' => dir_size($dd), 'quota' => QUOTA]);
    }
    case 'media_del': {
        $b = body(); $u = (string)($b['url'] ?? '');
        if (!preg_match('~^media/' . preg_quote($aid, '~') . '/([a-z0-9_.-]+)$~', $u, $mm) || strpos($mm[1], '..') !== false) out(['err' => 'ungültig'], 400);
        @unlink(DIR . '/' . $u); jdel('mm_' . $aid . '_' . preg_replace('/[^a-z0-9]/', '', pathinfo($mm[1], PATHINFO_FILENAME))); out(['ok' => 1]);
    }
    case 'up': {   // Upload in Stücken (umgeht kleine Upload-Limits des Hosters)
        gc_tmp();
        $uid = preg_replace('/[^a-z0-9]/', '', $_GET['uid'] ?? ''); $i = (int)($_GET['i'] ?? 0); $n = (int)($_GET['n'] ?? 1);
        $name = cs($_GET['name'] ?? 'datei', 120); $e = strtolower(pathinfo($name, PATHINFO_EXTENSION));
        if (!$uid || $n < 1 || $i < 0 || $i >= $n) out(['err' => 'Parameter'], 400);
        if (!in_array($e, array_merge(EXT_IMG, EXT_VID), true)) out(['err' => 'Dateityp nicht erlaubt (erlaubt: JPG, PNG, GIF, WebP, MP4, WebM, MOV)'], 400);
        $part = DATA . "/up_{$aid}_$uid.part";
        if ($i === 0) @unlink($part); elseif (!is_file($part)) out(['err' => 'Upload unterbrochen'], 400);
        $chunk = file_get_contents('php://input');
        if (strlen($chunk) > 8 * 1024 * 1024) out(['err' => 'Stück zu groß'], 413);
        file_put_contents($part, $chunk, FILE_APPEND | LOCK_EX);
        if (filesize($part) > MAXFILE) { @unlink($part); out(['err' => 'Datei zu groß (max. 600 MB)'], 413); }
        if ($i < $n - 1) out(['ok' => 1]);
        if (dir_size(MEDIA . '/' . $aid) + filesize($part) > QUOTA) { @unlink($part); out(['err' => 'Speicherplatz voll – bitte Dateien löschen'], 413); }
        if (in_array($e, EXT_IMG, true) && !@getimagesize($part)) { @unlink($part); out(['err' => 'Das ist kein gültiges Bild'], 400); }
        @mkdir(MEDIA . '/' . $aid, 0755, true); $fid = rid(10); $fn = "$fid.$e";
        rename($part, MEDIA . "/$aid/$fn");
        jw('mm_' . $aid . '_' . $fid, ['name' => $name]);
        out(['ok' => 1, 'url' => "media/$aid/$fn", 'type' => in_array($e, EXT_VID, true) ? 'video' : 'image', 'name' => $name, 'size' => filesize(MEDIA . "/$aid/$fn")]);
    }
}
out(['err' => 'unbekannt'], 404);
