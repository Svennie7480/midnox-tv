<?php
/*
 * midnox Radio – Musikwünsche mit Namen
 * Gäste suchen einen Song, wünschen ihn direkt bei AzuraCast (aus ihrem eigenen Browser)
 * und tragen ihren Namen ein. Der midnox-TV-Launcher holt die Liste und blendet die Wünsche ein.
 * Einfach in das Hauptverzeichnis von midnox.de hochladen (PHP 7+). Speichert in radio-wuensche.json daneben.
 */
$API   = 'https://radio.midnox.de/api/station/midnox';
$STORE = __DIR__ . '/radio-wuensche.json';
$KEEP  = 40;      // so viele Wünsche merken
$WAIT  = 20;      // Sekunden zwischen zwei Namenseinträgen pro Gerät

function load_store($f) {
    if (!is_file($f)) return ['next' => 1, 'wishes' => [], 'ips' => []];
    $d = json_decode((string)@file_get_contents($f), true);
    return is_array($d) ? $d + ['next' => 1, 'wishes' => [], 'ips' => []] : ['next' => 1, 'wishes' => [], 'ips' => []];
}
function clean($s, $n) { $s = trim(preg_replace('/\s+/u', ' ', strip_tags((string)$s))); return mb_substr($s, 0, $n); }

if (isset($_GET['ping'])) { header('Content-Type: text/plain'); header('Access-Control-Allow-Origin: *'); echo 'midnox-wunsch'; exit; }

if (isset($_GET['list'])) {
    header('Content-Type: application/json; charset=utf-8');
    header('Access-Control-Allow-Origin: *');
    header('Cache-Control: no-store');
    $d = load_store($STORE);
    $since = (int)($_GET['since'] ?? 0);
    $out = array_values(array_filter($d['wishes'], function ($w) use ($since) { return $w['id'] > $since; }));
    echo json_encode(['wishes' => $out], JSON_UNESCAPED_UNICODE);
    exit;
}

if (isset($_GET['add']) && $_SERVER['REQUEST_METHOD'] === 'POST') {
    header('Content-Type: application/json; charset=utf-8');
    $in = json_decode(file_get_contents('php://input'), true) ?: [];
    $title = clean($in['title'] ?? '', 120);
    if ($title === '') { echo '{"ok":false}'; exit; }
    $fp = fopen($STORE, 'c+');
    if (!$fp) { echo '{"ok":false}'; exit; }
    flock($fp, LOCK_EX);
    $d = json_decode(stream_get_contents($fp), true);
    if (!is_array($d)) $d = ['next' => 1, 'wishes' => [], 'ips' => []];
    $ip = hash('sha256', ($_SERVER['REMOTE_ADDR'] ?? '') . 'midnox');
    $now = time();
    foreach ($d['ips'] as $k => $t) if ($now - $t > 3600) unset($d['ips'][$k]);
    if (isset($d['ips'][$ip]) && $now - $d['ips'][$ip] < $WAIT) { flock($fp, LOCK_UN); fclose($fp); echo '{"ok":false,"wait":true}'; exit; }
    $d['ips'][$ip] = $now;
    $d['wishes'][] = ['id' => (int)$d['next'], 'time' => $now, 'name' => clean($in['name'] ?? '', 24),
                      'title' => $title, 'artist' => clean($in['artist'] ?? '', 80)];
    $d['next'] = (int)$d['next'] + 1;
    $d['wishes'] = array_slice($d['wishes'], -$KEEP);
    ftruncate($fp, 0); rewind($fp);
    fwrite($fp, json_encode($d, JSON_UNESCAPED_UNICODE));
    fflush($fp); flock($fp, LOCK_UN); fclose($fp);
    echo '{"ok":true}';
    exit;
}
?><!doctype html>
<html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>midnox Radio – Musikwunsch</title>
<style>
:root{--g:linear-gradient(90deg,#7C3AED,#3B5BF0,#06B6D4)}*{box-sizing:border-box}
body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0b0a14;color:#e6e8f2;padding:24px 16px 60px}
.w{max-width:540px;margin:0 auto}h1{font-weight:300;font-size:34px;margin:0 0 4px;background:var(--g);-webkit-background-clip:text;background-clip:text;color:transparent}
.sub{color:#8a8fa8;margin:0 0 18px;font-size:14px}
.now{display:flex;gap:12px;align-items:center;background:#15141f;border:1px solid #26243a;border-radius:14px;padding:10px;margin-bottom:16px}
.now img{width:56px;height:56px;border-radius:10px;object-fit:cover}.now small{color:#8a8fa8;display:block;font-size:11px;letter-spacing:.2em}
input{width:100%;padding:14px;border-radius:12px;border:1px solid #2e2c45;background:#0e0d18;color:#fff;font-size:17px;margin-bottom:10px}
input:focus{outline:none;border-color:#3B5BF0}
.it{display:flex;gap:12px;align-items:center;padding:10px;border-radius:12px;background:#15141f;margin-bottom:8px;border:1px solid #26243a}
.it img{width:52px;height:52px;border-radius:8px;object-fit:cover;background:#222}.it b{display:block;font-weight:500}.it span{color:#8a8fa8;font-size:13px}
.it button{margin-left:auto;padding:10px 16px;border:0;border-radius:999px;background:var(--g);color:#fff;font-size:14px}
.msg{padding:12px;border-radius:12px;background:#15141f;margin:10px 0;display:none}
</style></head><body><div class="w">
<h1>midnox Radio</h1><p class="sub">Wünsch dir einen Song – er läuft gleich im Radio, und dein Name erscheint bei uns auf dem Fernseher.</p>
<div class="now" id="now" style="display:none"></div>
<input id="n" placeholder="Dein Name" maxlength="24">
<input id="q" placeholder="Song oder Künstler suchen …" autocomplete="off">
<div id="m" class="msg"></div><div id="r"></div>
</div>
<script>
var api=<?php echo json_encode($API); ?>,t,n=document.getElementById('n'),q=document.getElementById('q'),r=document.getElementById('r'),m=document.getElementById('m');
try{n.value=localStorage.getItem('midnox_name')||''}catch(e){}
function esc(s){return String(s||'').replace(/[&<>"']/g,function(c){return'&#'+c.charCodeAt(0)+';'})}
function msg(s){m.style.display='block';m.textContent=s;window.scrollTo(0,0)}
function now(){fetch(api.replace('/station/','/nowplaying/')).then(function(x){return x.json()}).then(function(d){var s=d.now_playing.song,e=document.getElementById('now');
e.style.display='flex';e.innerHTML='<img src="'+esc(s.art)+'"><div><small>LÄUFT GERADE</small><b>'+esc(s.title)+'</b><br><span style="color:#8a8fa8">'+esc(s.artist)+'</span></div>'}).catch(function(){})}
function load(){fetch(api+'/requests?rowCount=30&searchPhrase='+encodeURIComponent(q.value)).then(function(x){return x.json()}).then(function(d){
r.innerHTML='';(d.rows||d).forEach(function(it){var s=it.song,e=document.createElement('div');e.className='it';
e.innerHTML='<img loading="lazy" src="'+esc(s.art)+'"><div><b>'+esc(s.title)+'</b><span>'+esc(s.artist)+'</span></div><button>Wünschen</button>';
e.querySelector('button').onclick=function(){wish(it.request_id,s)};r.appendChild(e)})}).catch(function(){msg('Radio gerade nicht erreichbar.')})}
function wish(id,s){if(!n.value.trim()){msg('Bitte zuerst deinen Namen eintragen.');n.focus();return}
try{localStorage.setItem('midnox_name',n.value)}catch(e){}
fetch(api+'/request/'+id,{method:'POST'}).then(function(x){return x.json().then(function(j){return{ok:x.ok,j:j}})}).then(function(o){
if(o.ok&&o.j.success!==false){msg('✓ '+(o.j.formatted_message||o.j.message||'Wunsch angenommen!'));
fetch('?add=1',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:n.value,title:s.title,artist:s.artist})})}
else msg(o.j.formatted_message||o.j.message||'Wunsch gerade nicht möglich.')}).catch(function(){msg('Wunsch gerade nicht möglich.')})}
q.addEventListener('input',function(){clearTimeout(t);t=setTimeout(load,350)});load();now();setInterval(now,20000);
</script></body></html>
