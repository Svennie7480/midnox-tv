<?php
/*
 * midnox Live – Pakete für Freunde und Kollegen
 * Eine Datei: Verwaltung im Browser, Einrichtungscodes, automatische Updates, kurzer Download-Link.
 * Ablage: Unterordner data/ (wird automatisch angelegt, von außen gesperrt, Pakete verschlüsselt).
 */
declare(strict_types=1);
error_reporting(0);

const APK = 'https://raw.githubusercontent.com/Svennie7480/midnox-tv/main/release/midnox-live.apk';
const CODE_DAYS = 30;          // so lange ist ein Einrichtungscode gültig
const MAX_FAILS = 8;           // falsche Codes je Stunde und IP
const MAX_LOGIN_FAILS = 5;     // falsche Passwörter je 30 Minuten und IP

$DATA = __DIR__ . '/data';

// ---------------------------------------------------------------- Ablage
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

function cfg(): array { return load('config', []); }
function key32(): string { return hash('sha256', (string)(cfg()['secret'] ?? ''), true); }
function enc(array $v): string {
    $iv = random_bytes(12); $tag = '';
    $c = openssl_encrypt(json_encode($v, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), 'aes-256-gcm', key32(), OPENSSL_RAW_DATA, $iv, $tag);
    return base64_encode($iv . $tag . $c);
}
function dec(string $s): array {
    $b = base64_decode($s);
    if (strlen($b) < 29) return [];
    $p = openssl_decrypt(substr($b, 28), 'aes-256-gcm', key32(), OPENSSL_RAW_DATA, substr($b, 0, 12), substr($b, 12, 16));
    $j = json_decode((string)$p, true);
    return is_array($j) ? $j : [];
}

// ---------------------------------------------------------------- Helfer
function out(array $v, int $code = 200): void {
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($v, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function ip(): string { return (string)($_SERVER['REMOTE_ADDR'] ?? '?'); }
function limited(string $kind, int $max, int $window): bool {
    $l = load('limits', []); $k = $kind . '|' . ip(); $now = time();
    $e = $l[$k] ?? ['n' => 0, 't' => $now];
    if ($now - $e['t'] > $window) return false;
    return $e['n'] >= $max;
}
function fail(string $kind, int $window): void {
    $l = load('limits', []); $k = $kind . '|' . ip(); $now = time();
    foreach ($l as $kk => $e) if ($now - $e['t'] > 86400) unset($l[$kk]);
    $e = $l[$k] ?? ['n' => 0, 't' => $now];
    if ($now - $e['t'] > $window) $e = ['n' => 0, 't' => $now];
    $e['n']++; $l[$k] = $e; save('limits', $l);
}
function rid(int $n = 8): string { return bin2hex(random_bytes($n)); }

// ---------------------------------------------------------------- Kurzer Download-Link
if (isset($_GET['app'])) { header('Location: ' . APK, true, 302); exit; }
// beim ersten Aufruf Unterordner app/ anlegen -> midnox.de/midnoxtv/live/app
if (!is_dir(__DIR__ . '/app')) { @mkdir(__DIR__ . '/app', 0755); @file_put_contents(__DIR__ . '/app/index.php', "<?php header('Location: " . APK . "', true, 302);\n"); }

$api = $_GET['api'] ?? '';
$in = json_decode((string)file_get_contents('php://input'), true);
if (!is_array($in)) $in = [];

// ---------------------------------------------------------------- Ersteinrichtung (Admin-Passwort)
if ($api === 'setup') {
    $h = lockAll();
    if (!empty(cfg()['hash'])) out(['ok' => false, 'msg' => 'Ist schon eingerichtet'], 403);
    $pw = (string)($in['password'] ?? '');
    if (strlen($pw) < 8) out(['ok' => false, 'msg' => 'Das Passwort braucht mindestens 8 Zeichen']);
    if (!function_exists('openssl_encrypt')) out(['ok' => false, 'msg' => 'Auf dem Server fehlt die PHP-Erweiterung openssl']);
    save('config', ['hash' => password_hash($pw, PASSWORD_DEFAULT), 'secret' => rid(32), 'created' => time()]);
    save('packages', []);
    out(['ok' => true]);
}

// ---------------------------------------------------------------- Stick: Code einlösen
if ($api === 'redeem') {
    if (limited('code', MAX_FAILS, 3600)) out(['ok' => false, 'msg' => 'Zu viele Versuche – bitte in einer Stunde nochmal'], 429);
    $code = preg_replace('/\D/', '', (string)($in['code'] ?? ''));
    $h = lockAll();
    $pk = load('packages', []);
    foreach ($pk as $id => &$p) {
        foreach ($p['codes'] ?? [] as $i => $c) {
            if ($c['code'] !== $code) continue;
            if (time() - $c['created'] > CODE_DAYS * 86400) { unset($p['codes'][$i]); continue; }
            unset($p['codes'][$i]);                       // nur einmal gültig
            $p['codes'] = array_values($p['codes']);
            $tok = rid(24);
            $p['devices'][] = ['token' => hash('sha256', $tok), 'name' => substr((string)($in['device'] ?? 'Gerät'), 0, 60), 'first' => time(), 'last' => time(), 'ip' => ip(), 'ver' => $p['ver']];
            save('packages', $pk);
            out(['ok' => true, 'token' => $tok, 'ver' => $p['ver'], 'name' => $p['name'], 'package' => dec($p['data'])]);
        }
    }
    unset($p);
    save('packages', $pk);
    fail('code', 3600);
    out(['ok' => false, 'msg' => 'Code unbekannt oder abgelaufen'], 404);
}

// ---------------------------------------------------------------- Stick: Aktualisierung holen
if ($api === 'pull') {
    $t = hash('sha256', (string)($in['token'] ?? ''));
    $h = lockAll();
    $pk = load('packages', []);
    foreach ($pk as $id => &$p) foreach ($p['devices'] ?? [] as $i => $d) {
        if (!hash_equals($d['token'], $t)) continue;
        $p['devices'][$i]['last'] = time(); $p['devices'][$i]['ip'] = ip(); $p['devices'][$i]['ver'] = $p['ver'];
        if (!empty($in['device'])) $p['devices'][$i]['name'] = substr((string)$in['device'], 0, 60);
        save('packages', $pk);
        if ((int)($in['ver'] ?? -1) === (int)$p['ver']) out(['ok' => true, 'changed' => false, 'ver' => $p['ver']]);
        out(['ok' => true, 'changed' => true, 'ver' => $p['ver'], 'name' => $p['name'], 'package' => dec($p['data'])]);
    }
    out(['ok' => false, 'revoked' => true, 'msg' => 'Dieses Gerät ist nicht mehr freigeschaltet'], 410);
}

// ---------------------------------------------------------------- Verwaltung
if ($api === 'admin') {
    $c = cfg();
    if (empty($c['hash'])) out(['ok' => false, 'setup' => true]);
    if (limited('login', MAX_LOGIN_FAILS, 1800)) out(['ok' => false, 'msg' => 'Zu viele falsche Passwörter – bitte 30 Minuten warten'], 429);
    if (!password_verify((string)($_SERVER['HTTP_X_ADMIN'] ?? ''), $c['hash'])) { fail('login', 1800); out(['ok' => false, 'login' => true, 'msg' => 'Passwort falsch'], 401); }
    $do = $_GET['do'] ?? 'list';
    $h = lockAll();
    $pk = load('packages', []);
    $now = time();
    switch ($do) {
        case 'list':
            $l = [];
            foreach ($pk as $id => $p) {
                $codes = array_values(array_filter($p['codes'] ?? [], function ($x) use ($now) { return $now - $x['created'] <= CODE_DAYS * 86400; }));
                $devs = array_map(function ($d) { return ['id' => substr($d['token'], 0, 12), 'name' => $d['name'], 'first' => $d['first'], 'last' => $d['last'], 'current' => $d['ver'] ?? 0]; }, $p['devices'] ?? []);
                $l[] = ['id' => $id, 'name' => $p['name'], 'ver' => $p['ver'], 'updated' => $p['updated'], 'data' => dec($p['data']),
                        'codes' => array_map(function ($x) { return ['code' => $x['code'], 'until' => $x['created'] + CODE_DAYS * 86400]; }, $codes), 'devices' => $devs];
            }
            usort($l, function ($a, $b) { return strcasecmp($a['name'], $b['name']); });
            out(['ok' => true, 'packages' => $l]);
        case 'save':
            $id = (string)($in['id'] ?? '');
            if ($id === '' || !isset($pk[$id])) { $id = rid(6); $pk[$id] = ['codes' => [], 'devices' => [], 'ver' => 0]; }
            $name = trim((string)($in['name'] ?? '')) ?: 'Ohne Namen';
            $data = is_array($in['data'] ?? null) ? $in['data'] : [];
            $data['midnox_live'] = 1;
            $pk[$id]['name'] = substr($name, 0, 60);
            $pk[$id]['data'] = enc($data);
            $pk[$id]['ver'] = (int)$pk[$id]['ver'] + 1;
            $pk[$id]['updated'] = $now;
            save('packages', $pk);
            out(['ok' => true, 'id' => $id]);
        case 'delete':
            unset($pk[(string)($in['id'] ?? '')]); save('packages', $pk); out(['ok' => true]);
        case 'code':
            $id = (string)($in['id'] ?? '');
            if (!isset($pk[$id])) out(['ok' => false, 'msg' => 'Paket fehlt']);
            $used = [];
            foreach ($pk as $p) foreach ($p['codes'] ?? [] as $x) $used[$x['code']] = 1;
            do { $code = str_pad((string)random_int(0, 999999), 6, '0', STR_PAD_LEFT); } while (isset($used[$code]));
            $pk[$id]['codes'][] = ['code' => $code, 'created' => $now];
            save('packages', $pk);
            out(['ok' => true, 'code' => $code, 'until' => $now + CODE_DAYS * 86400]);
        case 'uncode':
            $id = (string)($in['id'] ?? '');
            if (isset($pk[$id])) { $pk[$id]['codes'] = array_values(array_filter($pk[$id]['codes'] ?? [], function ($x) use ($in) { return $x['code'] !== (string)($in['code'] ?? ''); })); save('packages', $pk); }
            out(['ok' => true]);
        case 'revoke':
            $id = (string)($in['id'] ?? ''); $dev = (string)($in['device'] ?? '');
            if (isset($pk[$id])) { $pk[$id]['devices'] = array_values(array_filter($pk[$id]['devices'] ?? [], function ($d) use ($dev) { return substr($d['token'], 0, 12) !== $dev; })); save('packages', $pk); }
            out(['ok' => true]);
        case 'password':
            $pw = (string)($in['password'] ?? '');
            if (strlen($pw) < 8) out(['ok' => false, 'msg' => 'Mindestens 8 Zeichen']);
            $c['hash'] = password_hash($pw, PASSWORD_DEFAULT); save('config', $c);
            out(['ok' => true]);
    }
    out(['ok' => false, 'msg' => 'Unbekannter Befehl']);
}
if ($api !== '') out(['ok' => false, 'msg' => 'Unbekannt'], 404);

// ---------------------------------------------------------------- Verwaltungsseite
header('Content-Type: text/html; charset=utf-8');
header('X-Robots-Tag: noindex');
?><!doctype html>
<html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">
<title>midnox Live – Pakete</title>
<style>
:root{--g:linear-gradient(90deg,#7C3AED,#3B5BF0,#06B6D4);--line:#26243a;--muted:#8a8fa8}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0b0a14;color:#e6e8f2;padding:22px 16px 80px}
.w{max-width:900px;margin:0 auto}h1{font-weight:300;font-size:32px;margin:0 0 4px}h1 b{background:var(--g);-webkit-background-clip:text;background-clip:text;color:transparent;font-weight:300}
h1 i{font-style:normal;font-size:13px;background:#ef4444;color:#fff;padding:3px 8px;border-radius:7px;vertical-align:middle;margin-left:6px;font-weight:700}
.sub{color:var(--muted);margin:0 0 20px;font-size:14px}h3{font-size:12px;letter-spacing:.25em;color:var(--muted);font-weight:500;margin:22px 0 10px}
.card{background:#15141f;border:1px solid var(--line);border-radius:16px;padding:16px 18px;margin-bottom:12px}
input,select,textarea{width:100%;padding:11px;border-radius:10px;border:1px solid #2e2c45;background:#0e0d18;color:#fff;font-size:15px;margin-bottom:8px;font-family:inherit}
button{padding:9px 16px;border:0;border-radius:999px;background:var(--g);color:#fff;font-size:14px;cursor:pointer}button.g{background:#26243a}button.r{background:#7f1d1d}
.row{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.row>*{flex:0 0 auto}.grow{flex:1 1 200px}.hint{color:var(--muted);font-size:13px}
.code{font-size:30px;font-weight:700;letter-spacing:.18em;background:var(--g);-webkit-background-clip:text;background-clip:text;color:transparent}
.dev{display:flex;justify-content:space-between;gap:10px;padding:7px 0;border-top:1px solid var(--line);font-size:14px}
.src{border:1px solid var(--line);border-radius:12px;padding:12px;margin-bottom:10px;background:#100f1a}.grid{display:grid;grid-template-columns:1fr 1fr;gap:0 10px}
#msg{position:fixed;left:50%;bottom:20px;transform:translateX(-50%);background:#26243a;padding:12px 18px;border-radius:12px;display:none;z-index:9}
.modal{position:fixed;inset:0;background:rgba(5,5,10,.85);display:none;overflow:auto;padding:20px 12px}.modal .w{background:#0b0a14;border:1px solid var(--line);border-radius:18px;padding:20px}
.pill{display:inline-block;font-size:12px;padding:3px 9px;border-radius:999px;background:#26243a;color:#c9cce0;margin-right:4px}
@media(max-width:600px){.grid{grid-template-columns:1fr}}
</style></head><body><div class="w">
<h1><b>midnox</b> Live<i>LIVE</i></h1><p class="sub">Pakete für Freunde und Kollegen – Portale anlegen, Code weitergeben, fertig.</p>

<div id="setup" class="card" style="display:none"><h3 style="margin-top:0">ERSTEINRICHTUNG</h3><p class="hint">Lege ein Passwort für diese Verwaltung fest (mindestens 8 Zeichen). Nur wer es kennt, kann Pakete sehen und ändern.</p>
<input type="password" id="pw1" placeholder="Neues Passwort"><input type="password" id="pw2" placeholder="Passwort wiederholen"><button onclick="setup()">Festlegen</button></div>

<div id="login" class="card" style="display:none"><h3 style="margin-top:0">ANMELDEN</h3><input type="password" id="pw" placeholder="Passwort" onkeydown="if(event.key=='Enter')login()"><button onclick="login()">Anmelden</button></div>

<div id="main" style="display:none">
 <div class="row" style="margin-bottom:6px"><button onclick="edit(null)">＋ Neues Paket</button><button class="g" onclick="logout()">Abmelden</button>
  <span class="hint grow" style="text-align:right">App-Download für Kollegen: <b id="dl"></b></span></div>
 <div id="list"></div>
 <div class="card hint">So geht's: Paket anlegen → <b>Code erzeugen</b> → dein Kollege installiert midnox Live (Downloader-App, Adresse oben), startet sie und gibt den sechsstelligen Code mit der Fernbedienung ein. Änderungen am Paket kommen automatisch auf seinen Stick. Jeder Code gilt einmal und <?= CODE_DAYS ?> Tage.</div>
</div>
</div>

<div class="modal" id="ed"><div class="w">
 <h3 style="margin-top:0" id="edt">PAKET</h3>
 <input id="e_name" placeholder="Name, z. B. Thomas">
 <div id="e_src"></div>
 <div class="row" style="margin-bottom:14px"><button class="g" onclick="addSrc('stalker')">＋ Stalker-Portal</button><button class="g" onclick="addSrc('xtream')">＋ Xtream Codes</button><button class="g" onclick="addSrc('m3u')">＋ M3U-Adresse</button>
  <label class="hint" style="cursor:pointer">oder <u>Sicherung von einem Stick übernehmen</u><input type="file" id="e_imp" accept=".json" style="display:none" onchange="impFile(this)"></label></div>
 <input id="e_epg" placeholder="Zusätzliche EPG-Adressen (optional, mit Komma)">
 <div class="row"><button onclick="saveEd()">Speichern</button><button class="g" onclick="closeEd()">Abbrechen</button></div>
</div></div>
<div id="msg"></div>

<script>
var PW=sessionStorage.getItem('mlpw')||'',P=[],E=null;
function $(i){return document.getElementById(i)}
function esc(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return'&#'+c.charCodeAt(0)+';'})}
function msg(t){var m=$('msg');m.textContent=t;m.style.display='block';clearTimeout(m.t);m.t=setTimeout(function(){m.style.display='none'},3500)}
function api(q,o){return fetch('?api='+q,{method:'POST',headers:{'X-Admin':PW},body:JSON.stringify(o||{})}).then(function(r){return r.json()})}
function show(id){['setup','login','main'].forEach(function(x){$(x).style.display=x==id?'block':'none'})}
function when(t){if(!t)return'–';var d=new Date(t*1000),m=Math.round((Date.now()-d)/60000);if(m<2)return'gerade eben';if(m<60)return'vor '+m+' Min.';if(m<1440)return'vor '+Math.round(m/60)+' Std.';return d.toLocaleDateString('de-DE')}
function setup(){if($('pw1').value!=$('pw2').value)return msg('Die Passwörter sind verschieden');fetch('?api=setup',{method:'POST',body:JSON.stringify({password:$('pw1').value})}).then(function(r){return r.json()}).then(function(j){if(!j.ok)return msg(j.msg);PW=$('pw1').value;sessionStorage.setItem('mlpw',PW);load()})}
function login(){PW=$('pw').value;sessionStorage.setItem('mlpw',PW);load()}
function logout(){PW='';sessionStorage.removeItem('mlpw');show('login')}
function load(){api('admin&do=list').then(function(j){if(j.setup)return show('setup');if(!j.ok){if(j.msg&&PW)msg(j.msg);return show('login')}P=j.packages;show('main');render()})}
function render(){
 $('dl').textContent=location.host+location.pathname.replace(/index\.php$/,'').replace(/\/?$/,'/')+'app';
 $('list').innerHTML=P.length?P.map(function(p,i){var s=(p.data.sources||[]);
  return '<div class="card"><div class="row"><div class="grow"><b style="font-size:18px">'+esc(p.name)+'</b><div class="hint">'+s.map(function(x){return '<span class="pill">'+esc(x.name||x.type)+(x.type=='stalker'?' · '+(x.mac||'').split(/[\s,;]+/).filter(Boolean).length+' MAC':'')+'</span>'}).join('')+' Stand '+when(p.updated)+'</div></div>'
  +'<button class="g" onclick="edit('+i+')">Bearbeiten</button><button onclick="code('+i+')">Code erzeugen</button><button class="r" onclick="del('+i+')">Löschen</button></div>'
  +(p.codes.length?'<div style="margin-top:10px">'+p.codes.map(function(c){return '<div class="row"><span class="code">'+c.code.slice(0,3)+' '+c.code.slice(3)+'</span><span class="hint grow">gültig bis '+new Date(c.until*1000).toLocaleDateString('de-DE')+' · noch nicht eingelöst</span><button class="g" onclick="uncode('+i+',\''+c.code+'\')">Zurückziehen</button></div>'}).join('')+'</div>':'')
  +(p.devices.length?'<div style="margin-top:10px">'+p.devices.map(function(d){return '<div class="dev"><span>📺 '+esc(d.name)+' <span class="hint">· seit '+when(d.first)+' · zuletzt '+when(d.last)+(d.current==p.ver?' · aktuell':' · Update ausstehend')+'</span></span><button class="g" onclick="revoke('+i+',\''+d.id+'\')">Abklemmen</button></div>'}).join('')+'</div>':'<div class="hint" style="margin-top:8px">Noch kein Gerät eingerichtet.</div>')
  +'</div>'}).join(''):'<div class="card hint">Noch keine Pakete – leg oben das erste an.</div>'}
function code(i){api('admin&do=code',{id:P[i].id}).then(function(j){if(!j.ok)return msg(j.msg);load();msg('Code '+j.code+' – an deinen Kollegen weitergeben')})}
function uncode(i,c){api('admin&do=uncode',{id:P[i].id,code:c}).then(load)}
function revoke(i,d){if(confirm('Dieses Gerät abklemmen? Die Portale verschwinden dort beim nächsten Abgleich.'))api('admin&do=revoke',{id:P[i].id,device:d}).then(load)}
function del(i){if(confirm('Paket „'+P[i].name+'“ löschen? Eingerichtete Geräte verlieren die Portale beim nächsten Abgleich.'))api('admin&do=delete',{id:P[i].id}).then(load)}
// ---------- Editor
function rid(){return 'pk'+Math.random().toString(36).slice(2,10)}
function edit(i){E=i==null?{id:'',name:'',data:{sources:[],favs:[],hidden:[],epg_extra:''}}:JSON.parse(JSON.stringify(P[i]));
 $('edt').textContent=i==null?'NEUES PAKET':'PAKET BEARBEITEN';$('e_name').value=E.name;$('e_epg').value=E.data.epg_extra||'';drawSrc();$('ed').style.display='block'}
function closeEd(){$('ed').style.display='none'}
function addSrc(t){sync();E.data.sources.push({id:rid(),type:t,name:'',url:'',user:'',pass:'',mac:'',epg:'',on:true});drawSrc()}
function rmSrc(i){sync();E.data.sources.splice(i,1);drawSrc()}
function f(i,k,ph,type){var v=E.data.sources[i][k]||'';return type=='ta'?'<textarea rows=3 data-i='+i+' data-k='+k+' placeholder="'+ph+'">'+esc(v)+'</textarea>':'<input data-i='+i+' data-k='+k+' placeholder="'+ph+'" value="'+esc(v)+'"'+(type?' type='+type:'')+'>'}
function drawSrc(){$('e_src').innerHTML=E.data.sources.map(function(s,i){var lab=s.type=='stalker'?'Stalker-Portal':s.type=='xtream'?'Xtream Codes':'M3U';
 var b='<div class="src"><div class="row" style="margin-bottom:8px"><b class="grow">'+lab+'</b><label class="hint"><input type=checkbox data-i='+i+' data-k=on style="width:auto;margin:0 6px 0 0"'+(s.on!==false?' checked':'')+'>aktiv</label><button class="r" onclick="rmSrc('+i+')">Entfernen</button></div><div class="grid">'+f(i,'name','Anzeigename, z. B. Portal 1');
 if(s.type=='stalker')b+=f(i,'url','Portal-Adresse http://…/c/')+'</div>'+f(i,'mac','MAC-Adressen, eine pro Zeile (Reihenfolge = Ausweich-Reihenfolge)','ta')+f(i,'epg','Zusätzliche EPG-Adresse (optional)');
 else if(s.type=='xtream')b+=f(i,'url','Server http://anbieter.tv:8080')+f(i,'user','Benutzername')+f(i,'pass','Passwort')+'</div>';
 else b+=f(i,'url','M3U-Adresse http://…')+'</div>'+f(i,'epg','EPG-Adresse (optional)')+(s.file?'<div class="hint">Enthält eine hochgeladene Playlist-Datei</div>':'');
 return b+'</div>'}).join('')||'<div class="hint" style="margin-bottom:10px">Noch keine Quelle – füge unten ein Portal, einen Xtream-Zugang oder eine M3U-Adresse hinzu.</div>'}
function sync(){document.querySelectorAll('#e_src [data-k]').forEach(function(el){var s=E.data.sources[+el.dataset.i];s[el.dataset.k]=el.type=='checkbox'?el.checked:el.value.trim()})}
function impFile(inp){var fl=inp.files[0];if(!fl)return;var r=new FileReader();r.onload=function(){try{var j=JSON.parse(r.result);if(!j.midnox_live)throw 0;sync();var map={};
 (j.sources||[]).forEach(function(s){var n=rid();map[s.id]=n;s.id=n;E.data.sources.push(s)});
 (j.favs||[]).forEach(function(fv){var p=fv.indexOf(':');var src=fv.slice(0,p);if(map[src])E.data.favs.push(map[src]+fv.slice(p))});
 (j.hidden||[]).forEach(function(h){if(E.data.hidden.indexOf(h)<0)E.data.hidden.push(h)});
 if(j.epg_extra&&!$('e_epg').value)$('e_epg').value=j.epg_extra;drawSrc();msg((j.sources||[]).length+' Quelle(n) übernommen – jetzt speichern')}catch(e){msg('Das ist keine midnox-Live-Sicherung')}inp.value=''};r.readAsText(fl)}
function saveEd(){sync();E.name=$('e_name').value.trim();E.data.epg_extra=$('e_epg').value.trim();if(!E.name)return msg('Bitte einen Namen eingeben');
 for(var i=0;i<E.data.sources.length;i++){var s=E.data.sources[i];if(!s.url&&!s.file)return msg('Quelle '+(i+1)+': Adresse fehlt');if(!s.name)s.name=s.type=='m3u'?'M3U-Liste':s.url.replace(/^https?:\/\//,'').replace(/[:\/].*/,'')}
 api('admin&do=save',{id:E.id,name:E.name,data:E.data}).then(function(j){if(!j.ok)return msg(j.msg||'Fehler');closeEd();load();msg('Gespeichert – eingerichtete Geräte holen sich die Änderung automatisch')})}
load();
</script></body></html>
