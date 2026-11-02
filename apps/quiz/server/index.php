<?php
/*
 * midnox Quiz – weltweite Bestenliste
 * Eine Datei. Ablage im Unterordner data/ (wird automatisch angelegt und von außen gesperrt).
 *   ?a=top&m=million&p=all|week     Bestenliste „Die Million“
 *   ?a=top&m=daily[&d=2026-11-02]   Tages-Duell
 *   POST ?a=submit                  Ergebnis eintragen (von der App signiert)
 *   ohne Parameter                  Bestenliste im Browser
 *   ?admin                          Verwaltung (Einträge löschen)
 */
declare(strict_types=1);
error_reporting(0);

const SECRET = '__SECRET__';          // muss mit der App übereinstimmen
const MILLION = [0, 50, 100, 200, 300, 500, 1000, 2000, 4000, 8000, 16000, 32000, 64000, 125000, 500000, 1000000];
const DAILY_MAX = 10000;              // 10 Fragen × höchstens 1000 Punkte
const MAX_SUBMITS = 30;               // Einträge je Stunde und IP
const KEEP_DAYS = 60;                 // Tages-Duelle so lange aufheben

$DATA = __DIR__ . '/data';
date_default_timezone_set('Europe/Berlin');

function guardDir(string $d): void {
    if (!is_dir($d)) @mkdir($d, 0700, true);
    if (!file_exists("$d/.htaccess")) @file_put_contents("$d/.htaccess", "Require all denied\nDeny from all\n");
    if (!file_exists("$d/index.html")) @file_put_contents("$d/index.html", '');
}
function load(string $name, $def) {
    global $DATA;
    $f = "$DATA/$name.php";
    if (!file_exists($f)) return $def;
    $s = (string)@file_get_contents($f);
    $p = strpos($s, "\n");
    $j = json_decode($p === false ? '' : substr($s, $p + 1), true);
    return is_array($j) ? $j : $def;
}
function save(string $name, $v): void {
    global $DATA;
    guardDir($DATA);
    $f = "$DATA/$name.php";
    @file_put_contents("$f.tmp", "<?php http_response_code(403); exit; ?>\n" . json_encode($v, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), LOCK_EX);
    @rename("$f.tmp", $f);
}
function lockAll() { global $DATA; guardDir($DATA); $h = fopen("$DATA/.lock", 'c'); flock($h, LOCK_EX); return $h; }
function out(array $v, int $code = 200): void {
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    header('Access-Control-Allow-Origin: *');
    echo json_encode($v, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function ip(): string { return (string)($_SERVER['REMOTE_ADDR'] ?? '?'); }
function h(string $s): string { return htmlspecialchars($s, ENT_QUOTES, 'UTF-8'); }
function cleanName(string $n): string {
    $n = preg_replace('/[\x00-\x1F<>"\\\\]/u', '', $n) ?? '';
    $n = trim(preg_replace('/\s+/u', ' ', $n) ?? '');
    return mb_substr($n, 0, 16);
}

// ---------------------------------------------------------------- Ranglisten
/** Million: bester Eintrag je Gerät+Name; sortiert nach Gewinn, dann Zeit. */
function topMillion(string $period, int $n = 50): array {
    $all = load('million', []);
    $since = $period === 'week' ? strtotime('monday this week 00:00') : 0;
    $best = [];
    foreach ($all as $e) {
        if ($e['at'] < $since) continue;
        $k = $e['dev'] . '|' . mb_strtolower($e['n']);
        if (!isset($best[$k]) || better($e, $best[$k])) $best[$k] = $e;
    }
    $l = array_values($best);
    usort($l, fn($a, $b) => better($a, $b) ? -1 : (better($b, $a) ? 1 : 0));
    return array_map(fn($e) => ['n' => $e['n'], 's' => $e['s'], 't' => $e['t'], 'at' => $e['at'], 'dev' => substr(hash('sha256', $e['dev']), 0, 8)], array_slice($l, 0, $n));
}
function better(array $a, array $b): bool { return $a['s'] !== $b['s'] ? $a['s'] > $b['s'] : ($a['t'] !== $b['t'] ? $a['t'] < $b['t'] : $a['at'] < $b['at']); }
function topDaily(string $day, int $n = 50): array {
    $all = load('daily', []);
    $l = array_values($all[$day] ?? []);
    usort($l, fn($a, $b) => better($a, $b) ? -1 : (better($b, $a) ? 1 : 0));
    return array_map(fn($e) => ['n' => $e['n'], 's' => $e['s'], 't' => $e['t'], 'at' => $e['at'], 'dev' => substr(hash('sha256', $e['dev']), 0, 8)], array_slice($l, 0, $n));
}

// ---------------------------------------------------------------- API
$a = $_GET['a'] ?? '';
if ($a === 'top') {
    $m = $_GET['m'] ?? 'million';
    if ($m === 'daily') {
        $d = preg_match('/^\d{4}-\d{2}-\d{2}$/', $_GET['d'] ?? '') ? $_GET['d'] : date('Y-m-d');
        $l = topDaily($d);
        out(['ok' => true, 'm' => 'daily', 'd' => $d, 'list' => $l, 'players' => count(load('daily', [])[$d] ?? [])]);
    }
    $p = ($_GET['p'] ?? 'all') === 'week' ? 'week' : 'all';
    out(['ok' => true, 'm' => 'million', 'p' => $p, 'list' => topMillion($p)]);
}
if ($a === 'submit') {
    $in = json_decode((string)file_get_contents('php://input'), true);
    if (!is_array($in)) out(['ok' => false, 'msg' => 'Ungültig'], 400);
    $m = (string)($in['m'] ?? ''); $rawN = (string)($in['n'] ?? ''); $n = cleanName($rawN); $s = (int)($in['s'] ?? -1);
    $t = (int)($in['t'] ?? 0); $dev = preg_replace('/[^a-f0-9]/', '', (string)($in['dev'] ?? '')); $day = (string)($in['day'] ?? ''); $ts = (int)($in['ts'] ?? 0);
    $sig = hash_hmac('sha256', "$m|$rawN|$s|$t|$dev|$day|$ts", SECRET);
    if (!hash_equals($sig, (string)($in['sig'] ?? ''))) out(['ok' => false, 'msg' => 'Signatur ungültig'], 403);
    if (abs(time() - intdiv($ts, 1000)) > 3600) out(['ok' => false, 'msg' => 'Uhrzeit des Geräts stimmt nicht'], 400);
    if (mb_strlen($n) < 2 || strlen($dev) < 12) out(['ok' => false, 'msg' => 'Name fehlt'], 400);

    $lk = lockAll();
    $lim = load('limits', []); $now = time();
    foreach ($lim as $k => $e) if ($now - $e['t'] > 3600) unset($lim[$k]);
    $e = $lim[ip()] ?? ['n' => 0, 't' => $now];
    if ($e['n'] >= MAX_SUBMITS) out(['ok' => false, 'msg' => 'Zu viele Einträge – später nochmal'], 429);
    $e['n']++; $lim[ip()] = $e; save('limits', $lim);

    $row = ['n' => $n, 's' => $s, 't' => $t, 'dev' => $dev, 'at' => $now];
    if ($m === 'million') {
        if (!in_array($s, MILLION, true) || ($s > 0 && $t < 20)) out(['ok' => false, 'msg' => 'Ergebnis unplausibel'], 400);
        $all = load('million', []);
        $all[] = $row;
        if (count($all) > 20000) $all = array_slice($all, -20000);
        save('million', $all);
        $hd = substr(hash('sha256', $dev), 0, 8); $rank = 0; $best = 0;
        foreach (topMillion('all', 100000) as $i => $x) if ($x['dev'] === $hd && mb_strtolower($x['n']) === mb_strtolower($n)) { $rank = $i + 1; $best = $x['s']; break; }
        out(['ok' => true, 'rank' => $rank, 'best' => $best]);
    }
    if ($m === 'daily') {
        $today = date('Y-m-d'); $yest = date('Y-m-d', strtotime('-1 day'));
        if ($day !== $today && $day !== $yest) out(['ok' => false, 'msg' => 'Tag abgelaufen'], 400);
        if ($s < 0 || $s > DAILY_MAX || $t < 10) out(['ok' => false, 'msg' => 'Ergebnis unplausibel'], 400);
        $all = load('daily', []);
        foreach (array_keys($all) as $d) if ($d < date('Y-m-d', strtotime('-' . KEEP_DAYS . ' days'))) unset($all[$d]);
        if (isset($all[$day][$dev])) out(['ok' => false, 'msg' => 'Heute schon gespielt', 'already' => true]);   // erster Versuch zählt
        $all[$day][$dev] = $row;
        save('daily', $all);
        $rank = 1; foreach (topDaily($day, 100000) as $x) { if ($x['n'] === $n && $x['s'] === $s && $x['t'] === $t) break; $rank++; }
        out(['ok' => true, 'rank' => $rank, 'players' => count($all[$day])]);
    }
    out(['ok' => false, 'msg' => 'Unbekannter Modus'], 400);
}

// ---------------------------------------------------------------- Verwaltung
session_start();
if (isset($_GET['admin'])) {
    $cfg = load('config', []);
    $msg = '';
    if (empty($cfg['pw']) && isset($_POST['pw1'])) {
        if (strlen($_POST['pw1']) >= 8) { $cfg['pw'] = password_hash($_POST['pw1'], PASSWORD_DEFAULT); save('config', $cfg); $_SESSION['qa'] = 1; }
        else $msg = 'Mindestens 8 Zeichen.';
    }
    if (!empty($cfg['pw']) && isset($_POST['pw'])) {
        if (password_verify($_POST['pw'], $cfg['pw'])) $_SESSION['qa'] = 1; else { sleep(2); $msg = 'Falsches Passwort.'; }
    }
    if (!empty($_SESSION['qa']) && isset($_POST['del'])) {
        $lk = lockAll();
        [$kind, $dev, $name] = array_pad(explode('|', (string)$_POST['del'], 3), 3, '');
        if ($kind === 'million') { $all = load('million', []); $all = array_values(array_filter($all, fn($e) => !(substr(hash('sha256', $e['dev']), 0, 8) === $dev && $e['n'] === $name))); save('million', $all); }
        else { $all = load('daily', []); foreach ($all as $d => $l) foreach ($l as $k => $e) if (substr(hash('sha256', $e['dev']), 0, 8) === $dev && $e['n'] === $name) unset($all[$d][$k]); save('daily', $all); }
        $msg = 'Gelöscht: ' . $name;
    }
}
$admin = !empty($_SESSION['qa']) && isset($_GET['admin']);

// ---------------------------------------------------------------- Seite
function money(int $v): string { return number_format($v, 0, ',', '.') . ' €'; }
function dur(int $t): string { return sprintf('%d:%02d', intdiv($t, 60), $t % 60); }
$tab = $_GET['t'] ?? 'daily';
?><!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>midnox Quiz – Bestenliste</title>
<style>
body{margin:0;background:#0B0A14;color:#E6E8F2;font:16px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
.w{max-width:720px;margin:0 auto;padding:28px 16px}
h1{font-size:28px;margin:0 0 4px}h1 b{background:linear-gradient(90deg,#7C3AED,#3B5BF0,#06B6D4);-webkit-background-clip:text;background-clip:text;color:transparent}
.sub{color:#8A8FA8;margin:0 0 20px}
.tabs{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:16px}.tabs a{padding:8px 16px;border-radius:999px;background:#15141F;color:#C9CCE0;text-decoration:none}.tabs a.on{background:linear-gradient(90deg,#7C3AED,#3B5BF0,#06B6D4);color:#fff}
table{width:100%;border-collapse:collapse;background:#15141F;border-radius:14px;overflow:hidden}td,th{padding:10px 12px;text-align:left}th{color:#8A8FA8;font-weight:500;font-size:13px}tr+tr td{border-top:1px solid #23212F}
td.r{text-align:right;white-space:nowrap}.p{width:36px;color:#8A8FA8}.g1{color:#FACC15}.g2{color:#CBD5E1}.g3{color:#F59E0B}
input,button{font:inherit;padding:10px 14px;border-radius:10px;border:1px solid #2E2B4A;background:#15141F;color:#fff}button{background:linear-gradient(90deg,#7C3AED,#3B5BF0);border:0;cursor:pointer}
.x{background:#3b1d2a;padding:4px 10px;font-size:13px}.m{color:#fca5a5}.e{color:#8A8FA8;padding:20px;text-align:center}
</style></head><body><div class="w">
<h1><b>midnox</b> Quiz</h1><p class="sub">Weltweite Bestenliste – gespielt wird am Fernseher mit der midnox Quiz App.</p>
<?php if (isset($_GET['admin']) && !$admin) { $cfg = load('config', []); ?>
<form method="post"><p class="m"><?= h($msg) ?></p>
<?php if (empty($cfg['pw'])) { ?><p>Erster Aufruf: Lege ein Verwaltungs-Passwort fest.</p><input type="password" name="pw1" placeholder="Neues Passwort (mind. 8 Zeichen)"> <button>Festlegen</button>
<?php } else { ?><input type="password" name="pw" placeholder="Passwort"> <button>Anmelden</button><?php } ?></form>
<?php } else {
    if (!empty($msg)) echo '<p class="m">' . h($msg) . '</p>';
    $q = $admin ? '&admin' : '';
    echo '<div class="tabs"><a class="' . ($tab === 'daily' ? 'on' : '') . '" href="?t=daily' . $q . '">Tages-Duell heute</a><a class="' . ($tab === 'week' ? 'on' : '') . '" href="?t=week' . $q . '">Die Million · Woche</a><a class="' . ($tab === 'all' ? 'on' : '') . '" href="?t=all' . $q . '">Die Million · Ewig</a></div>';
    $daily = $tab === 'daily';
    $l = $daily ? topDaily(date('Y-m-d')) : topMillion($tab === 'week' ? 'week' : 'all');
    echo '<table><tr><th class="p">#</th><th>Name</th><th class="r">' . ($daily ? 'Punkte' : 'Gewinn') . '</th><th class="r">Zeit</th>' . ($admin ? '<th></th>' : '') . '</tr>';
    if (!$l) echo '<tr><td colspan="5" class="e">Noch keine Einträge – sei die oder der Erste!</td></tr>';
    foreach ($l as $i => $e) {
        echo '<tr><td class="p g' . ($i + 1) . '">' . ($i + 1) . '</td><td>' . h($e['n']) . '</td><td class="r">' . ($daily ? number_format($e['s'], 0, ',', '.') : money($e['s'])) . '</td><td class="r">' . dur($e['t']) . '</td>';
        if ($admin) echo '<td><form method="post" style="margin:0"><input type="hidden" name="del" value="' . h(($daily ? 'daily' : 'million') . '|' . $e['dev'] . '|' . $e['n']) . '"><button class="x">löschen</button></form></td>';
        echo '</tr>';
    }
    echo '</table>';
} ?>
</div></body></html>
