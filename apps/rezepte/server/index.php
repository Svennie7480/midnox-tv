<?php
/**
 * midnox Rezepte – Rezeptspeicher auf midnox.de
 * Hochladen nach:  midnox.de/midnoxtv/rezepte/index.php
 * In der App unter Einstellungen den gleichen SCHLÜSSEL eintragen.
 * Test im Browser:  …/rezepte/index.php?a=check
 */
const KEY = 'HIER-SCHLUESSEL-EINTRAGEN';

// ---------------------------------------------------------------------------------------------
// Datenordner mit nicht erratbarem Namen – geschützt auch auf Servern, die .htaccess ignorieren (nginx)
define('DATA', __DIR__ . '/data_' . substr(hash('sha256', 'midnox-rezepte' . KEY), 0, 16));
const MAX_IMG = 8 * 1024 * 1024;

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');

function out($o, $code = 200) { http_response_code($code); echo json_encode($o, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); exit; }
function fail($msg, $code = 400) { out(['ok' => false, 'err' => $msg], $code); }

$a = $_GET['a'] ?? '';

if ($a === 'check') {   // ohne Schlüssel: nur sagen, ob alles bereit ist
    $ok = is_dir(DATA) || @mkdir(DATA, 0775, true);
    out(['ok' => $ok && is_writable(DATA), 'app' => 'midnox Rezepte', 'php' => PHP_VERSION,
         'schluessel_gesetzt' => KEY !== 'HIER-SCHLUESSEL-EINTRAGEN' && strlen(KEY) >= 12,
         'ordner_beschreibbar' => $ok && is_writable(DATA)]);
}

$key = $_SERVER['HTTP_X_KEY'] ?? ($_GET['k'] ?? '');
if (KEY === 'HIER-SCHLUESSEL-EINTRAGEN' || strlen(KEY) < 12) fail('Auf dem Server ist noch kein Schlüssel eingetragen', 500);
if (!is_string($key) || !hash_equals(KEY, $key)) { usleep(400000); fail('Schlüssel falsch', 403); }

if (!is_dir(DATA)) @mkdir(DATA, 0775, true);
if (!is_dir(DATA . '/img')) @mkdir(DATA . '/img', 0775, true);
if (!file_exists(DATA . '/.htaccess')) @file_put_contents(DATA . '/.htaccess', "Require all denied\nDeny from all\n");

$file = DATA . '/recipes.json';

/** Rezeptliste sperren, ändern, zurückschreiben */
function withList($fn) {
    global $file;
    $fh = fopen($file, 'c+'); if (!$fh) fail('Datenordner nicht beschreibbar', 500);
    flock($fh, LOCK_EX);
    $raw = stream_get_contents($fh); $list = json_decode($raw ?: '{}', true); if (!is_array($list)) $list = [];
    $res = $fn($list);
    if ($res !== false) {
        $tmp = $file . '.tmp'; file_put_contents($tmp, json_encode($list, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        rename($tmp, $file);
    }
    flock($fh, LOCK_UN); fclose($fh);
    return $res;
}
function readList() { global $file; $l = json_decode(@file_get_contents($file) ?: '{}', true); return is_array($l) ? $l : []; }
function rid() { $id = $_GET['id'] ?? ''; if (!preg_match('/^[A-Za-z0-9_-]{3,40}$/', $id)) fail('ungültige ID'); return $id; }

switch ($a) {
    case 'ping': out(['ok' => true, 'n' => count(readList())]);

    case 'list': out(['ok' => true, 'list' => array_values(readList())]);

    case 'save': {
        $id = rid();
        $r = json_decode(file_get_contents('php://input'), true);
        if (!is_array($r) || ($r['id'] ?? '') !== $id) fail('Rezept unvollständig');
        if (strlen(json_encode($r)) > 400000) fail('Rezept zu groß');
        unset($r['store']);
        withList(function (&$l) use ($id, $r) { $l[$id] = $r; });
        out(['ok' => true]);
    }

    case 'del': {
        $id = rid();
        withList(function (&$l) use ($id) { unset($l[$id]); });
        @unlink(DATA . "/img/$id.jpg");
        out(['ok' => true]);
    }

    case 'img': {
        $id = rid(); $p = DATA . "/img/$id.jpg";
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            $b = file_get_contents('php://input');
            if (strlen($b) < 100 || strlen($b) > MAX_IMG) fail('Bild ungültig');
            $sig = substr($b, 0, 12);
            if (strpos($sig, "\xFF\xD8") !== 0 && strpos($sig, "\x89PNG") !== 0 && substr($sig, 8, 4) !== 'WEBP') fail('Kein Bild');
            file_put_contents($p, $b);
            out(['ok' => true]);
        }
        if (!is_file($p)) fail('kein Bild', 404);
        $b = file_get_contents($p);
        header('Content-Type: ' . (strpos($b, "\x89PNG") === 0 ? 'image/png' : (substr($b, 8, 4) === 'WEBP' ? 'image/webp' : 'image/jpeg')));
        header('Content-Length: ' . strlen($b));
        echo $b; exit;
    }

    case 'imgdel': { $id = rid(); @unlink(DATA . "/img/$id.jpg"); out(['ok' => true]); }
}
fail('unbekannte Aktion', 404);
