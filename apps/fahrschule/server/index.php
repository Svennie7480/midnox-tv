<?php
/*
 * midnox Führerschein – Sprachausgabe
 * Hochladen nach midnox.de/midnoxtv/fahrschule/index.php. Ablage im Unterordner data/ (wird automatisch angelegt und gesperrt).
 *   ?a=tts&k=<schlüssel>   MP3 zur Frage (ID) oder Erklärung (ID_e) – einmal mit ElevenLabs erzeugt, danach gespeichert
 *   ?a=info                Stimme eingerichtet?
 *   ?a=stats               Anzahl gespeicherter Dateien und verbrauchter Zeichen
 * Gesprochen wird nur Text aus fahrschule/tts.json im GitHub-Repo – beliebiger Text ist nicht möglich.
 */
declare(strict_types=1);
error_reporting(0);

const EL_KEY = '__ELKEY__';                       // ElevenLabs-API-Schlüssel (nur in der hochgeladenen Datei, nie im Repo)
const VOICE = 'onwK4e9ZLuTAKqWW03F9';             // ElevenLabs-Stimme „Daniel“
const MODEL = 'eleven_multilingual_v2';
const TEXTS = 'https://raw.githubusercontent.com/Svennie7480/midnox-tv/main/fahrschule/tts.json';
const MAX_IP_HOUR = 300;                          // neu erzeugte Dateien je IP und Stunde
const MAX_DAY = 1500;                             // neu erzeugte Dateien je Tag insgesamt

$DATA = __DIR__ . '/data';
function guardDir(string $d): void {
    if (!is_dir($d)) @mkdir($d, 0775, true);
    $root = __DIR__ . '/data';
    if (!file_exists("$root/.htaccess")) @file_put_contents("$root/.htaccess", "Require all denied\nDeny from all\n");
}
function load(string $n, $def) { global $DATA; $s = @file_get_contents("$DATA/$n.json"); $v = $s === false ? null : json_decode($s, true); return $v ?? $def; }
function save(string $n, $v): void { global $DATA; guardDir($DATA); file_put_contents("$DATA/$n.json.tmp", json_encode($v, JSON_UNESCAPED_UNICODE)); rename("$DATA/$n.json.tmp", "$DATA/$n.json"); }
function out(array $v, int $code = 200): void { http_response_code($code); header('Content-Type: application/json; charset=utf-8'); header('Access-Control-Allow-Origin: *'); echo json_encode($v, JSON_UNESCAPED_UNICODE); exit; }
function ready(): bool { return EL_KEY !== '' && strpos(EL_KEY, '__') !== 0; }

/** Textliste von GitHub, lokal zwischengespeichert (alle 6 Stunden oder bei unbekanntem Schlüssel höchstens alle 5 Minuten neu) */
function texts(bool $force = false): array {
    global $DATA; guardDir($DATA);
    $f = "$DATA/tts.json"; $age = file_exists($f) ? time() - filemtime($f) : PHP_INT_MAX;
    if ($age > 21600 || ($force && $age > 300)) {
        $ch = curl_init(TEXTS . '?t=' . time());
        curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 20, CURLOPT_FOLLOWLOCATION => true]);
        $r = curl_exec($ch); $c = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE); curl_close($ch);
        if ($c === 200 && is_array(json_decode((string)$r, true)['t'] ?? null)) file_put_contents($f, $r); else @touch($f);
    }
    return json_decode((string)@file_get_contents($f), true)['t'] ?? [];
}

$a = (string)($_GET['a'] ?? '');
if ($a === 'info') out(['ok' => true, 'on' => ready()]);
if ($a === 'stats') { $s = load('stats', ['chars' => 0, 'files' => 0]); $s['stored'] = count(glob("$DATA/mp3/*.mp3") ?: []); out(['ok' => true] + $s); }
if ($a === 'tts') {
    if (!ready()) out(['ok' => false, 'msg' => 'Stimme nicht eingerichtet'], 404);
    $k = preg_replace('/[^A-Za-z0-9._-]/', '', (string)($_GET['k'] ?? ''));
    $t = texts(); $text = $t[$k] ?? null;
    if ($text === null) { $t = texts(true); $text = $t[$k] ?? null; }
    if ($text === null || $text === '') out(['ok' => false, 'msg' => 'Unbekannte Frage'], 404);
    guardDir("$DATA/mp3");
    $f = "$DATA/mp3/" . hash('sha256', VOICE . '|' . MODEL . '|' . $text) . '.mp3';
    if (!file_exists($f)) {
        $lock = fopen("$DATA/.lock", 'c'); flock($lock, LOCK_EX);
        if (!file_exists($f)) {
            $hour = date('YmdH'); $day = date('Ymd'); $ip = hash('sha256', (string)($_SERVER['REMOTE_ADDR'] ?? '?'));
            $lim = load('limits', []); if (($lim['h'] ?? '') !== $hour) $lim = ['h' => $hour, 'ip' => [], 'd' => $lim['d'] ?? '', 'dn' => $lim['dn'] ?? 0];
            if (($lim['d'] ?? '') !== $day) { $lim['d'] = $day; $lim['dn'] = 0; }
            if (($lim['ip'][$ip] ?? 0) >= MAX_IP_HOUR || $lim['dn'] >= MAX_DAY) { flock($lock, LOCK_UN); out(['ok' => false, 'msg' => 'Limit erreicht'], 429); }
            $ch = curl_init('https://api.elevenlabs.io/v1/text-to-speech/' . VOICE . '?output_format=mp3_44100_64');
            curl_setopt_array($ch, [CURLOPT_POST => true, CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 60,
                CURLOPT_HTTPHEADER => ['xi-api-key: ' . EL_KEY, 'Content-Type: application/json', 'Accept: audio/mpeg'],
                CURLOPT_POSTFIELDS => json_encode(['text' => $text, 'model_id' => MODEL, 'language_code' => 'de',
                    'voice_settings' => ['stability' => 0.55, 'similarity_boost' => 0.8, 'style' => 0.15, 'use_speaker_boost' => true]], JSON_UNESCAPED_UNICODE)]);
            $mp3 = curl_exec($ch); $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE); curl_close($ch);
            if ($code !== 200 || strlen((string)$mp3) < 500) { flock($lock, LOCK_UN); out(['ok' => false, 'msg' => 'ElevenLabs: Fehler ' . $code], 502); }
            file_put_contents("$f.tmp", $mp3); rename("$f.tmp", $f);
            $lim['ip'][$ip] = ($lim['ip'][$ip] ?? 0) + 1; $lim['dn']++; save('limits', $lim);
            $st = load('stats', ['chars' => 0, 'files' => 0]); $st['chars'] += mb_strlen($text); $st['files']++; save('stats', $st);
        }
        flock($lock, LOCK_UN);
    }
    header('Content-Type: audio/mpeg'); header('Content-Length: ' . filesize($f)); header('Cache-Control: public, max-age=31536000'); header('Access-Control-Allow-Origin: *');
    readfile($f); exit;
}
header('Content-Type: text/plain; charset=utf-8');
echo "midnox Führerschein – Sprachserver\n" . (ready() ? "Stimme eingerichtet.\n" : "Kein API-Schlüssel eingetragen.\n");
