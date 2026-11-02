<?php
/*
 * midnox Quiz – weltweite Bestenliste
 * Eine Datei. Ablage im Unterordner data/ (wird automatisch angelegt und von außen gesperrt).
 *   ?a=top&m=million&p=all|week     Bestenliste „Die Million“
 *   ?a=top&m=daily[&d=2026-11-02]   Tages-Duell
 *   POST ?a=submit                  Ergebnis eintragen (von der App signiert)
 *   ohne Parameter                  Bestenliste im Browser
 *   ?a=tts&q=<id> | &p=<satz>        Moderatoren-Stimme (ElevenLabs, einmal erzeugt und gespeichert)
 *   ?a=pack&c=<code>                Eigenes Fragenpaket
 *   POST ?a=group_new|group_join|group_leave, ?a=group&c=<code>   Freundesgruppen
 *   ?admin                          Verwaltung (Einträge, Stimme, Pakete, Gruppen)
 */
declare(strict_types=1);
error_reporting(0);

const SECRET = '__SECRET__';          // muss mit der App übereinstimmen
const MILLION = [0, 50, 100, 200, 300, 500, 1000, 2000, 4000, 8000, 16000, 32000, 64000, 125000, 500000, 1000000];
const DAILY_MAX = 10000;              // 10 Fragen × höchstens 1000 Punkte
const MAX_SUBMITS = 30;               // Einträge je Stunde und IP
const KEEP_DAYS = 60;                 // Tages-Duelle so lange aufheben
const BANK = 'https://raw.githubusercontent.com/Svennie7480/midnox-tv/main/quiz/questions.json';
const PHRASES = [
    'welcome_million' => 'Willkommen bei Die Million! Fünfzehn Fragen trennen dich von einer Million Euro. Viel Glück!',
    'welcome_daily'   => 'Willkommen zum Tages-Duell! Zehn Fragen, zwanzig Sekunden pro Frage. Los geht’s!',
    'welcome_party'   => 'Willkommen zur Quiz-Party! Handys raus – es geht los!',
    'right1' => 'Richtig!', 'right2' => 'Das ist richtig!', 'right3' => 'Sehr gut – richtig!',
    'wrong1' => 'Oh nein, das war leider falsch.', 'wrong2' => 'Schade, leider falsch.',
    'safety5'  => 'Glückwunsch! Die erste Sicherheitsstufe ist erreicht: Fünfhundert Euro sind dir sicher.',
    'safety10' => 'Klasse! Sechzehntausend Euro kann dir jetzt niemand mehr nehmen.',
    'last'     => 'Jetzt geht es um die Million!',
    'million'  => 'Unglaublich! Du hast die Million geknackt!',
    'quit'     => 'Du steigst aus. Eine kluge Entscheidung?',
    'joker50'  => 'Der Computer streicht zwei falsche Antworten.',
    'jokeraud' => 'Das Publikum hat abgestimmt.',
    'jokerphone' => 'Wir rufen jemanden an …',
    'timeup'   => 'Die Zeit ist um!',
    'daily_end' => 'Das war das Tages-Duell. Bis morgen!',
    'party_end' => 'Und jetzt: die Siegerehrung!',
];
const MODEL = 'eleven_multilingual_v2';

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
        out(['ok' => true, 'rank' => $rank, 'players' => count($all[$day]), 'streak' => streak($all, $dev)]);
    }
    out(['ok' => false, 'msg' => 'Unbekannter Modus'], 400);
}


// ---------------------------------------------------------------- Moderatoren-Stimme (ElevenLabs)
function bank(): array {
    global $DATA; guardDir($DATA);
    $f = "$DATA/bank.json";
    if (!file_exists($f) || time() - filemtime($f) > 6 * 3600) {
        $ctx = stream_context_create(['http' => ['timeout' => 10]]);
        $j = @file_get_contents(BANK . '?t=' . intdiv(time(), 3600), false, $ctx);
        if ($j && is_array(json_decode($j, true))) @file_put_contents($f, $j);
    }
    $b = json_decode((string)@file_get_contents($f), true);
    $m = [];
    foreach (($b['q'] ?? []) as $q) $m[$q['id']] = $q['q'];
    return $m;
}
function elevenCall(string $path, ?array $body, string $key, string $accept = 'application/json') {
    $ch = curl_init('https://api.elevenlabs.io' . $path);
    $hd = ['xi-api-key: ' . $key, 'Accept: ' . $accept];
    if ($body !== null) { $hd[] = 'Content-Type: application/json'; curl_setopt($ch, CURLOPT_POST, true); curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($body, JSON_UNESCAPED_UNICODE)); }
    curl_setopt_array($ch, [CURLOPT_HTTPHEADER => $hd, CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 60]);
    $r = curl_exec($ch); $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE); curl_close($ch);
    return [$code, $r];
}
function ttsText(): ?string {
    if (isset($_GET['p'])) return PHRASES[$_GET['p']] ?? null;
    $id = (string)($_GET['q'] ?? '');
    if (preg_match('/^p_([A-Z0-9]{6})_(\d+)$/', $id, $m)) {
        $pk = load('packs', [])[$m[1]] ?? null;
        return $pk['q'][(int)$m[2]]['q'] ?? null;
    }
    if (!preg_match('/^[a-f0-9]{8}$/', $id)) return null;
    return bank()[$id] ?? null;
}
if ($a === 'tts') {
    $cfg = load('config', []);
    if (isset($_GET['info'])) out(['ok' => true, 'on' => !empty($cfg['el_key']) && !empty($cfg['el_voice']) && empty($cfg['el_off'])]);
    if (empty($cfg['el_key']) || empty($cfg['el_voice']) || !empty($cfg['el_off'])) out(['ok' => false, 'msg' => 'Stimme nicht eingerichtet'], 404);
    $text = ttsText();
    if ($text === null) out(['ok' => false, 'msg' => 'Unbekannt'], 404);
    guardDir("$DATA/tts");
    $f = "$DATA/tts/" . hash('sha256', $cfg['el_voice'] . '|' . MODEL . '|' . $text) . '.mp3';
    if (!file_exists($f)) {
        $lk = lockAll();
        if (!file_exists($f)) {
            [$code, $mp3] = elevenCall('/v1/text-to-speech/' . rawurlencode($cfg['el_voice']) . '?output_format=mp3_44100_96',
                ['text' => $text, 'model_id' => MODEL, 'voice_settings' => ['stability' => 0.45, 'similarity_boost' => 0.8, 'style' => 0.3, 'use_speaker_boost' => true]], $cfg['el_key'], 'audio/mpeg');
            if ($code !== 200 || strlen((string)$mp3) < 500) out(['ok' => false, 'msg' => 'ElevenLabs: Fehler ' . $code], 502);
            file_put_contents($f, $mp3);
            $st = load('tts_stats', ['chars' => 0, 'files' => 0]); $st['chars'] += mb_strlen($text); $st['files']++; save('tts_stats', $st);
        }
    }
    header('Content-Type: audio/mpeg'); header('Content-Length: ' . filesize($f)); header('Cache-Control: public, max-age=31536000');
    readfile($f); exit;
}

// ---------------------------------------------------------------- Fragenpakete
function newCode(array $taken): string {
    $abc = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    do { $c = ''; for ($i = 0; $i < 6; $i++) $c .= $abc[random_int(0, strlen($abc) - 1)]; } while (isset($taken[$c]));
    return $c;
}
/** Zeilen „Frage | richtig | falsch | falsch | falsch“ einlesen. */
function parsePack(string $txt, array &$errors): array {
    $out = [];
    foreach (preg_split('/\r?\n/', $txt) as $n => $line) {
        $line = trim($line);
        if ($line === '' || $line[0] === '#') continue;
        $p = array_map('trim', explode('|', $line));
        if (count($p) !== 5 || in_array('', $p, true)) { $errors[] = 'Zeile ' . ($n + 1) . ': braucht Frage | richtig | falsch | falsch | falsch'; continue; }
        if (count(array_unique(array_map('mb_strtolower', array_slice($p, 1)))) !== 4) { $errors[] = 'Zeile ' . ($n + 1) . ': Antworten müssen verschieden sein'; continue; }
        $out[] = ['q' => mb_substr($p[0], 0, 200), 'a' => array_map(fn($x) => mb_substr($x, 0, 60), array_slice($p, 1))];
    }
    return $out;
}
if ($a === 'pack') {
    $c = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', (string)($_GET['c'] ?? '')));
    $pk = load('packs', [])[$c] ?? null;
    if (!$pk) out(['ok' => false, 'msg' => 'Paket nicht gefunden'], 404);
    $q = [];
    foreach ($pk['q'] as $i => $x) $q[] = ['id' => "p_{$c}_$i", 'q' => $x['q'], 'a' => $x['a']];
    out(['ok' => true, 'code' => $c, 'name' => $pk['name'], 'q' => $q]);
}

// ---------------------------------------------------------------- Freundesgruppen
function devOk(string $d): string { $d = preg_replace('/[^a-f0-9]/', '', $d); return strlen($d) >= 12 ? $d : ''; }
function streak(array $daily, string $dev): int {
    $n = 0; $d = date('Y-m-d');
    if (!isset($daily[$d][$dev])) $d = date('Y-m-d', strtotime('-1 day'));
    while (isset($daily[$d][$dev])) { $n++; $d = date('Y-m-d', strtotime($d . ' -1 day')); }
    return $n;
}
if (in_array($a, ['group_new', 'group_join', 'group_leave'], true)) {
    $in = json_decode((string)file_get_contents('php://input'), true) ?: [];
    $dev = devOk((string)($in['dev'] ?? '')); $name = cleanName((string)($in['n'] ?? ''));
    if (!$dev) out(['ok' => false, 'msg' => 'Gerät unbekannt'], 400);
    $lk = lockAll();
    $g = load('groups', []);
    if ($a === 'group_new') {
        $gn = mb_substr(trim(preg_replace('/[\x00-\x1F<>]/u', '', (string)($in['name'] ?? ''))), 0, 24);
        if (mb_strlen($gn) < 2) out(['ok' => false, 'msg' => 'Gruppenname fehlt'], 400);
        $mine = 0; foreach ($g as $x) if (($x['owner'] ?? '') === $dev) $mine++;
        if ($mine >= 10) out(['ok' => false, 'msg' => 'Höchstens 10 eigene Gruppen'], 400);
        $c = newCode($g);
        $g[$c] = ['name' => $gn, 'owner' => $dev, 'at' => time(), 'm' => [$dev => $name]];
        save('groups', $g);
        out(['ok' => true, 'code' => $c, 'name' => $gn]);
    }
    $c = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', (string)($in['code'] ?? '')));
    if (!isset($g[$c])) out(['ok' => false, 'msg' => 'Gruppe nicht gefunden'], 404);
    if ($a === 'group_join') {
        if (count($g[$c]['m']) >= 100 && !isset($g[$c]['m'][$dev])) out(['ok' => false, 'msg' => 'Gruppe ist voll'], 400);
        $g[$c]['m'][$dev] = $name; save('groups', $g);
        out(['ok' => true, 'code' => $c, 'name' => $g[$c]['name']]);
    }
    unset($g[$c]['m'][$dev]);
    if (!$g[$c]['m']) unset($g[$c]);
    save('groups', $g);
    out(['ok' => true]);
}
if ($a === 'group') {
    $c = strtoupper(preg_replace('/[^A-Za-z0-9]/', '', (string)($_GET['c'] ?? '')));
    $g = load('groups', [])[$c] ?? null;
    if (!$g) out(['ok' => false, 'msg' => 'Gruppe nicht gefunden'], 404);
    $members = $g['m']; $daily = load('daily', []); $today = date('Y-m-d');
    $hash = fn($d) => substr(hash('sha256', $d), 0, 8);
    $dl = []; foreach ($daily[$today] ?? [] as $dev => $e) if (isset($members[$dev])) $dl[] = $e;
    usort($dl, fn($x, $y) => better($x, $y) ? -1 : (better($y, $x) ? 1 : 0));
    $best = [];
    foreach (load('million', []) as $e) if (isset($members[$e['dev']]) && (!isset($best[$e['dev']]) || better($e, $best[$e['dev']]))) $best[$e['dev']] = $e;
    $ml = array_values($best); usort($ml, fn($x, $y) => better($x, $y) ? -1 : (better($y, $x) ? 1 : 0));
    $sl = []; foreach ($members as $dev => $n) $sl[] = ['n' => $n ?: '?', 's' => streak($daily, $dev), 't' => 0, 'dev' => $hash($dev)];
    usort($sl, fn($x, $y) => $y['s'] <=> $x['s']);
    $map = fn($e) => ['n' => $e['n'], 's' => $e['s'], 't' => $e['t'], 'dev' => $hash($e['dev'])];
    out(['ok' => true, 'code' => $c, 'name' => $g['name'], 'members' => count($members), 'daily' => array_map($map, $dl), 'million' => array_map($map, $ml), 'streak' => $sl]);
}
if ($a === 'streak') {
    $dev = devOk((string)($_GET['dev'] ?? ''));
    out(['ok' => true, 'streak' => $dev ? streak(load('daily', []), $dev) : 0]);
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
    if (!empty($_SESSION['qa'])) {
        // Stimme
        if (isset($_POST['el_save'])) {
            $lk = lockAll(); $cfg = load('config', []);
            if (trim((string)($_POST['el_key'] ?? '')) !== '') $cfg['el_key'] = trim((string)$_POST['el_key']);
            if (isset($_POST['el_voice'])) $cfg['el_voice'] = preg_replace('/[^A-Za-z0-9]/', '', (string)$_POST['el_voice']);
            $cfg['el_off'] = isset($_POST['el_off']);
            save('config', $cfg); unset($_SESSION['voices']);
            $msg = 'Stimme gespeichert.';
        }
        if (isset($_POST['el_clear'])) { foreach (glob("$DATA/tts/*.mp3") ?: [] as $f) @unlink($f); $msg = 'Gespeicherte Sprachdateien gelöscht.'; }
        // Pakete
        if (isset($_POST['pk_save'])) {
            $lk = lockAll(); $packs = load('packs', []); $err = [];
            $qs = parsePack((string)($_POST['pk_text'] ?? ''), $err);
            $c = strtoupper(preg_replace('/[^A-Z0-9]/i', '', (string)($_POST['pk_code'] ?? '')));
            if (!$c || !isset($packs[$c])) $c = newCode($packs);
            $nm = mb_substr(trim((string)($_POST['pk_name'] ?? '')), 0, 40) ?: 'Mein Paket';
            if (!$qs) $msg = 'Keine gültige Frage gefunden. ' . implode(' · ', $err);
            else { $packs[$c] = ['name' => $nm, 'q' => $qs, 'at' => time()]; save('packs', $packs); $msg = "Paket „{$nm}“ gespeichert – Code $c, " . count($qs) . ' Fragen.' . ($err ? ' Übersprungen: ' . implode(' · ', $err) : ''); $_GET['pk'] = $c; }
        }
        if (isset($_POST['pk_del'])) { $lk = lockAll(); $packs = load('packs', []); unset($packs[(string)$_POST['pk_del']]); save('packs', $packs); $msg = 'Paket gelöscht.'; }
        if (isset($_POST['gr_del'])) { $lk = lockAll(); $g = load('groups', []); unset($g[(string)$_POST['gr_del']]); save('groups', $g); $msg = 'Gruppe gelöscht.'; }
    }
}
$admin = !empty($_SESSION['qa']) && isset($_GET['admin']);

// ---------------------------------------------------------------- Verwaltungs-Seiten
function adminVoice(): void {
    global $DATA;
    $cfg = load('config', []); $key = $cfg['el_key'] ?? '';
    $voices = $_SESSION['voices'] ?? null; $sub = null;
    if ($key) {
        if ($voices === null) {
            [$c, $r] = elevenCall('/v1/voices', null, $key);
            $voices = []; foreach ((json_decode((string)$r, true)['voices'] ?? []) as $v) $voices[$v['voice_id']] = $v['name'] . (isset($v['labels']['accent']) ? ' · ' . $v['labels']['accent'] : '') . (isset($v['labels']['gender']) ? ' · ' . $v['labels']['gender'] : '');
            if ($c === 200) $_SESSION['voices'] = $voices; else echo '<p class="m">ElevenLabs antwortet mit Fehler ' . $c . ' – stimmt der API-Schlüssel?</p>';
        }
        [$c, $r] = elevenCall('/v1/user/subscription', null, $key); if ($c === 200) $sub = json_decode((string)$r, true);
    }
    $st = load('tts_stats', ['chars' => 0, 'files' => 0]);
    echo '<form method="post" class="card"><h3>Moderatoren-Stimme (ElevenLabs)</h3>';
    echo '<p class="hint">Der Schlüssel bleibt auf diesem Server. Jede Frage wird nur einmal gesprochen und gespeichert – danach kostet sie keine Zeichen mehr.</p>';
    echo '<label>API-Schlüssel</label><input type="password" name="el_key" placeholder="' . ($key ? 'gespeichert (…' . h(substr($key, -4)) . ') – leer lassen, um ihn zu behalten' : 'ElevenLabs → Profil → API Keys') . '" style="width:100%">';
    if ($voices) {
        echo '<label>Stimme</label><select name="el_voice" style="width:100%">';
        foreach ($voices as $id => $n) echo '<option value="' . h($id) . '"' . (($cfg['el_voice'] ?? '') === $id ? ' selected' : '') . '>' . h($n) . '</option>';
        echo '</select>';
    }
    echo '<label class="ck"><input type="checkbox" name="el_off"' . (!empty($cfg['el_off']) ? ' checked' : '') . '> Stimme vorübergehend abschalten</label>';
    echo '<button name="el_save" value="1">Speichern</button></form>';
    if ($key && !empty($cfg['el_voice'])) {
        echo '<div class="card"><h3>Probehören</h3><audio controls preload="none" src="?a=tts&p=welcome_million"></audio>';
        echo '<p class="hint">Nach einem Stimmenwechsel werden die Sätze neu erzeugt (neue Zeichen).</p></div>';
    }
    echo '<div class="card"><h3>Verbrauch</h3><p>Erzeugt: ' . number_format($st['files'], 0, ',', '.') . ' Sprachdateien · ' . number_format($st['chars'], 0, ',', '.') . ' Zeichen</p>';
    if ($sub) echo '<p>ElevenLabs-Kontingent: ' . number_format((int)$sub['character_count'], 0, ',', '.') . ' von ' . number_format((int)$sub['character_limit'], 0, ',', '.') . ' Zeichen in diesem Abrechnungszeitraum</p>';
    echo '<form method="post" onsubmit="return confirm(\'Alle gespeicherten Sprachdateien löschen?\')"><button class="x" name="el_clear" value="1">Gespeicherte Sprachdateien löschen</button></form></div>';
}
function adminPacks(): void {
    $packs = load('packs', []); $cur = $_GET['pk'] ?? '';
    $p = $packs[$cur] ?? null;
    echo '<div class="card"><h3>Eigene Fragenpakete</h3><p class="hint">Am Fernseher im Party-Modus unter „Kategorie“ → „Paket per Code“ laden.</p>';
    if (!$packs) echo '<p class="hint">Noch keine Pakete.</p>';
    foreach ($packs as $c => $x) echo '<div class="row"><b>' . h($x['name']) . '</b> <span class="code">' . $c . '</span> · ' . count($x['q']) . ' Fragen <a href="?admin&s=packs&pk=' . $c . '">bearbeiten</a> <form method="post" style="display:inline" onsubmit="return confirm(\'Paket löschen?\')"><button class="x" name="pk_del" value="' . $c . '">löschen</button></form></div>';
    echo '</div><form method="post" class="card"><h3>' . ($p ? 'Paket bearbeiten · ' . h($cur) : 'Neues Paket') . '</h3>';
    echo '<input type="hidden" name="pk_code" value="' . h($p ? $cur : '') . '">';
    echo '<label>Name</label><input name="pk_name" value="' . h($p['name'] ?? '') . '" placeholder="z. B. SWL-Quiz" style="width:100%">';
    $txt = ''; if ($p) foreach ($p['q'] as $x) $txt .= $x['q'] . ' | ' . implode(' | ', $x['a']) . "\n";
    echo '<label>Fragen – eine pro Zeile: Frage | richtige Antwort | falsch | falsch | falsch</label>';
    echo '<textarea name="pk_text" rows="14" style="width:100%" placeholder="Wie heißt der Busbetrieb in Lübeck? | SWL mobil | HVV | Autokraft | Rhenus">' . h($txt) . '</textarea>';
    echo '<button name="pk_save" value="1">Speichern</button>' . ($p ? ' <a href="?admin&s=packs">neues Paket anlegen</a>' : '') . '</form>';
}
function adminGroups(): void {
    $g = load('groups', []);
    echo '<div class="card"><h3>Freundesgruppen</h3><p class="hint">Gruppen werden in der App gegründet und per Code geteilt.</p>';
    if (!$g) echo '<p class="hint">Noch keine Gruppen.</p>';
    foreach ($g as $c => $x) echo '<div class="row"><b>' . h($x['name']) . '</b> <span class="code">' . $c . '</span> · ' . count($x['m']) . ' Mitglieder: ' . h(implode(', ', array_filter($x['m']))) . ' <form method="post" style="display:inline" onsubmit="return confirm(\'Gruppe löschen?\')"><button class="x" name="gr_del" value="' . $c . '">löschen</button></form></div>';
    echo '</div>';
}

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
.card{background:#15141F;border-radius:14px;padding:16px 18px;margin-bottom:14px}.card h3{margin:0 0 8px;font-size:17px}.hint{color:#8A8FA8;font-size:14px}
label{display:block;margin:12px 0 4px;color:#C9CCE0;font-size:14px}label.ck{display:flex;gap:8px;align-items:center}select,textarea{font:inherit;padding:10px 12px;border-radius:10px;border:1px solid #2E2B4A;background:#0B0A14;color:#fff;box-sizing:border-box}
.card button{margin-top:12px}.row{padding:8px 0;border-top:1px solid #23212F}.row a{color:#9EC5FF;margin-left:8px}.code{font-family:monospace;background:#23212F;padding:2px 8px;border-radius:6px;letter-spacing:2px}audio{width:100%;margin-top:6px}
</style></head><body><div class="w">
<h1><b>midnox</b> Quiz</h1><p class="sub">Weltweite Bestenliste – gespielt wird am Fernseher mit der midnox Quiz App.</p>
<?php if (isset($_GET['admin']) && !$admin) { $cfg = load('config', []); ?>
<form method="post"><p class="m"><?= h($msg) ?></p>
<?php if (empty($cfg['pw'])) { ?><p>Erster Aufruf: Lege ein Verwaltungs-Passwort fest.</p><input type="password" name="pw1" placeholder="Neues Passwort (mind. 8 Zeichen)"> <button>Festlegen</button>
<?php } else { ?><input type="password" name="pw" placeholder="Passwort"> <button>Anmelden</button><?php } ?></form>
<?php } else {
    if (!empty($msg)) echo '<p class="m">' . h($msg) . '</p>';
    $q = $admin ? '&admin' : '';
    $sec = $admin ? ($_GET['s'] ?? 'list') : 'list';
    if ($admin) {
        echo '<div class="tabs">';
        foreach (['list' => 'Bestenliste', 'voice' => 'Stimme', 'packs' => 'Fragenpakete', 'groups' => 'Gruppen'] as $k => $v) echo '<a class="' . ($sec === $k ? 'on' : '') . '" href="?admin&s=' . $k . '">' . $v . '</a>';
        echo '</div>';
    }
    if ($sec === 'voice') { adminVoice(); echo '</div></body></html>'; exit; }
    if ($sec === 'packs') { adminPacks(); echo '</div></body></html>'; exit; }
    if ($sec === 'groups') { adminGroups(); echo '</div></body></html>'; exit; }
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
