<?php
/* midnox Infoscreen – eine Datei: Server + Editor (PC) + Player (Fernseher)
   Hochladen nach z. B. midnox.de/midnoxtv/infoscreen/index.php – Ordner data/ und media/ legt die Datei selbst an. */
const MXI_EDITOR = <<<'MXI_EDITOR_END'
<!doctype html><html lang="de"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>midnox Infoscreen – Editor</title>
<style>
  :root{--bg:#0B0A14;--card:#16152a;--card2:#1d1c36;--line:#2c2a48;--tx:#f3f2fb;--mut:#9a98bd;--ac:#8B5CF6;--ac2:#F59E0B;--ok:#34d399;--bad:#f87171}
  *{box-sizing:border-box}
  body{margin:0;background:var(--bg);color:var(--tx);font:15px/1.4 system-ui,'Segoe UI',Roboto,sans-serif;min-height:100vh;
    background-image:radial-gradient(900px 500px at 90% -10%,#3b1a7a55,transparent),radial-gradient(700px 500px at -10% 110%,#4a134255,transparent)}
  a{color:var(--ac2)}
  header{display:flex;align-items:center;gap:18px;padding:12px 22px;border-bottom:1px solid var(--line);background:#0b0a14cc;position:sticky;top:0;z-index:20;backdrop-filter:blur(8px)}
  .logo{font-weight:900;font-size:19px;letter-spacing:.3px}.logo b{color:var(--ac2)}
  nav{display:flex;gap:4px}
  nav button,.btn{border:1px solid var(--line);background:var(--card);color:var(--tx);border-radius:10px;padding:8px 14px;font:inherit;cursor:pointer}
  nav button.on{background:var(--ac);border-color:var(--ac)}
  nav button:hover,.btn:hover{border-color:var(--ac)}
  .btn.pri{background:var(--ac);border-color:var(--ac);font-weight:700}
  .btn.warn{border-color:#7f1d1d;color:#fecaca}.btn.warn:hover{background:#7f1d1d}
  .btn.sm{padding:4px 10px;font-size:13px;border-radius:8px}
  .btn:disabled{opacity:.45;cursor:default}
  .sp{flex:1}
  main{padding:22px;max-width:1500px;margin:0 auto}
  h1{font-size:24px;margin:0 0 16px}h2{font-size:17px;margin:0 0 10px}
  .card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:18px}
  .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(310px,1fr));gap:16px}
  .scr h3{margin:0 0 4px;font-size:18px}.scr .m{color:var(--mut);font-size:13px;margin-bottom:12px}
  .dot{display:inline-block;width:9px;height:9px;border-radius:50%;background:#555;margin-right:6px}.dot.on{background:var(--ok);box-shadow:0 0 8px var(--ok)}
  .row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
  label.f{display:block;margin:0 0 12px}label.f>span{display:block;color:var(--mut);font-size:13px;margin-bottom:4px}
  input[type=text],input[type=password],input[type=number],input[type=date],input[type=time],input[type=url],select,textarea{width:100%;background:#0f0e1f;border:1px solid var(--line);color:var(--tx);border-radius:10px;padding:9px 11px;font:inherit}
  input:focus,select:focus,textarea:focus{outline:2px solid var(--ac);border-color:transparent}
  textarea{min-height:92px;resize:vertical}
  input[type=color]{width:54px;height:38px;border:1px solid var(--line);border-radius:8px;background:#0f0e1f;padding:2px}
  .chk{display:flex;align-items:center;gap:9px;margin:0 0 12px;cursor:pointer}.chk input{width:18px;height:18px;accent-color:var(--ac)}
  .login{max-width:420px;margin:9vh auto}.login .logo{font-size:28px;margin-bottom:14px}
  .err{color:var(--bad);margin:8px 0}.hint{color:var(--mut);font-size:13px}
  /* Editor */
  .ed{display:grid;grid-template-columns:300px minmax(380px,1fr) minmax(360px,520px);gap:16px;align-items:start}
  @media(max-width:1200px){.ed{grid-template-columns:260px 1fr}.ed .pvcol{grid-column:1/-1}}
  @media(max-width:800px){.ed{grid-template-columns:1fr}}
  .sl-list{display:flex;flex-direction:column;gap:6px;margin-top:10px}
  .si{display:flex;align-items:center;gap:8px;background:var(--card2);border:2px solid transparent;border-radius:12px;padding:8px 10px;cursor:pointer}
  .si.sel{border-color:var(--ac)}.si.off{opacity:.5}.si.over{border-color:var(--ac2)}
  .si .ic{font-size:20px}.si .tt{flex:1;min-width:0}.si .tt b{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-size:14px}.si .tt small{color:var(--mut)}
  .si .bt{display:flex;gap:2px}.si .bt button{background:none;border:0;color:var(--mut);cursor:pointer;font-size:15px;padding:2px 4px;border-radius:6px}.si .bt button:hover{background:#ffffff18;color:#fff}
  .tabs{display:flex;gap:4px;margin-bottom:14px;border-bottom:1px solid var(--line)}
  .tabs button{background:none;border:0;color:var(--mut);padding:9px 14px;font:inherit;cursor:pointer;border-bottom:3px solid transparent}.tabs button.on{color:#fff;border-color:var(--ac2)}
  .box{background:var(--card2);border:1px solid var(--line);border-radius:12px;padding:14px;margin-bottom:14px}
  .days{display:flex;gap:6px;flex-wrap:wrap}.days button{border:1px solid var(--line);background:#0f0e1f;color:var(--tx);border-radius:8px;padding:6px 10px;cursor:pointer}.days button.on{background:var(--ac);border-color:var(--ac)}
  .pv{position:relative;width:100%;background:#000;border-radius:12px;overflow:hidden;border:1px solid var(--line)}
  .pv iframe{position:absolute;left:0;top:0;border:0;transform-origin:0 0}
  .pvcol{position:sticky;top:76px}
  .mi{display:flex;gap:6px;align-items:center;margin-bottom:6px}.mi input{flex:1}
  .mi .n{flex:1.4}.mi .d{flex:1.6}.mi .p{flex:.6;min-width:70px}
  .thumb{width:100%;aspect-ratio:16/10;object-fit:cover;background:#000;border-radius:10px;display:block}
  .mgrid{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:12px}
  .mc{background:var(--card2);border:2px solid var(--line);border-radius:12px;padding:8px;cursor:pointer;position:relative}.mc:hover{border-color:var(--ac)}
  .mc small{display:block;color:var(--mut);margin-top:4px;font-size:12px;word-break:break-all}
  .bar{height:8px;background:#0f0e1f;border-radius:6px;overflow:hidden;margin:6px 0}.bar i{display:block;height:100%;background:linear-gradient(90deg,var(--ac),var(--ac2))}
  .modal{position:fixed;inset:0;background:#000b;z-index:50;display:flex;align-items:center;justify-content:center;padding:20px}
  .modal .card{width:min(1000px,100%);max-height:88vh;overflow:auto}
  .drop{border:2px dashed var(--line);border-radius:14px;padding:22px;text-align:center;color:var(--mut);margin-bottom:14px}.drop.over{border-color:var(--ac2);color:#fff}
  .tg{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:10px}
  .tp{border:2px solid var(--line);border-radius:12px;padding:10px;cursor:pointer;background:var(--card2);font-size:13px}.tp.on{border-color:var(--ac2)}
  .tp i{display:block;height:36px;border-radius:8px;margin-bottom:6px}
  #toast{position:fixed;left:50%;bottom:26px;transform:translateX(-50%);background:#000e;border:1px solid var(--line);padding:10px 18px;border-radius:12px;z-index:90;display:none}
  .banner{background:#4c1d95;padding:8px 22px;font-size:14px;display:flex;gap:12px;align-items:center}
  table{width:100%;border-collapse:collapse}td,th{padding:8px;border-bottom:1px solid var(--line);text-align:left;font-size:14px}
</style></head><body>
<div id="app"></div><div id="toast"></div>
<script>
(function(){
'use strict';
const $=s=>document.querySelector(s);
const h=(t,a,...c)=>{ const e=document.createElement(t); for(const k in (a||{})){ const v=a[k]; if(v==null||v===false) continue; if(k==='class') e.className=v; else if(k.slice(0,2)==='on') e.addEventListener(k.slice(2),v); else if(k in e&&k!=='list'&&k!=='style') e[k]=v; else e.setAttribute(k,v===true?'':v); } c.flat(9).forEach(x=>{ if(x==null||x===false) return; e.append(x.nodeType?x:String(x)); }); return e; };
const rnd=()=>Math.random().toString(36).slice(2,8);
const clone=o=>JSON.parse(JSON.stringify(o));
let toastT=0; function toast(t,bad){ const e=$('#toast'); e.textContent=t; e.style.color=bad?'#fecaca':'#fff'; e.style.display='block'; clearTimeout(toastT); toastT=setTimeout(()=>e.style.display='none',3200); }
async function A(a,body,q){
  const r=await fetch('?a='+a+(q||''),{method:body===undefined?'GET':'POST',headers:{'X-MX':'1','Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),cache:'no-store'});
  let j={}; try{ j=await r.json(); }catch(e){}
  if(r.status===401&&j.err==='login'){ ME=null; render(); throw new Error('login'); }
  if(!r.ok) throw new Error(j.err||('Fehler '+r.status)); return j;
}
const fail=e=>{ if(e&&e.message!=='login') toast(e.message||'Fehler',true); };
const fmtSize=n=>n>1e9?(n/1e9).toFixed(1)+' GB':n>1e6?(n/1e6).toFixed(1)+' MB':Math.round(n/1e3)+' KB';

let ME=null, view='home', SCR=[], USED=0, QUOTA=0, D=null, DSID='', sel=0, tab='slide', dirty=false, MEDIA=[], pvFrame=null, pvFocus=true;
const TYPES={image:'🖼️ Bild',video:'🎬 Video',text:'📝 Text',menu:'🍽️ Speisekarte / Preisliste',clock:'🕒 Uhr & Datum',weather:'⛅ Wetter',qr:'🔳 QR-Code'};
const ICON={image:'🖼️',video:'🎬',text:'📝',menu:'🍽️',clock:'🕒',weather:'⛅',qr:'🔳'};
const DEF={image:{src:'',fit:'contain',kb:true,cap:'',dur:10},video:{src:'',sound:false,fit:'contain',cap:'',dur:10},text:{head:'',body:'',badge:'',align:'center',bg:'',dur:10},
  menu:{head:'Speisekarte',sub:'',items:[{n:'Tagesgericht',d:'',p:'9,90 €',h:false}],foot:'',bg:'',dur:15},clock:{head:'',dur:10},weather:{city:'',lat:0,lon:0,dur:12},qr:{head:'',url:'https://',cap:'',dur:12}};
const TH=[['auto','Automatisch','Feiertage & Jahreszeiten','linear-gradient(90deg,#8B5CF6,#dc2626,#16a34a,#f59e0b)'],['midnox','midnox','Dunkel, Violett & Gold','linear-gradient(135deg,#3b1a7a,#0B0A14)'],['clean','Hell & schlicht','Weiß, dezent','#f6f5f1'],
 ['weihnachten','Weihnachten','Schnee, Rot & Gold','linear-gradient(135deg,#14391f,#3d0d12)'],['silvester','Silvester','Feuerwerk','linear-gradient(135deg,#04040f,#2a0d3d)'],['ostern','Ostern','Pastell, Blüten','linear-gradient(135deg,#fff9d9,#fbe3f1)'],
 ['halloween','Halloween','Orange & Fledermäuse','linear-gradient(135deg,#3a0f55,#0c0612)'],['valentin','Valentinstag','Herzen','linear-gradient(135deg,#3a0a1e,#7a1237)'],['fruehling','Frühling','Hell, Blüten','linear-gradient(135deg,#eaf8e4,#fdf1f7)'],
 ['sommer','Sommer','Sonne & Himmel','linear-gradient(135deg,#0b7fb8,#f6c445)'],['herbst','Herbst','Laub, Warmtöne','linear-gradient(135deg,#341a08,#7c3a0e)'],['winter','Winter','Schnee, Blau','linear-gradient(135deg,#0b1d33,#1f4068)']];
const WD=[[1,'Mo'],[2,'Di'],[3,'Mi'],[4,'Do'],[5,'Fr'],[6,'Sa'],[0,'So']];
const newSlide=t=>Object.assign({id:rnd(),type:t,on:true,name:'',dur:10,sch:{use:false,from:'',to:'',days:[],t1:'',t2:''}},clone(DEF[t]));
const sLabel=s=>s.name||s.head||(s.type==='image'||s.type==='video'?(s.cap||TYPES[s.type].slice(3)):s.city||TYPES[s.type].slice(3));

/* ---------- Eingabe-Helfer (binden direkt an Objekte) ---------- */
function touch(list){ dirty=true; pvPost(); const b=$('#savebtn'); if(b) b.textContent='Speichern & veröffentlichen ●'; if(list) renderList(); }
function field(label,o,k,opt){ opt=opt||{}; let i;
  if(opt.type==='textarea') i=h('textarea',{value:o[k]||'',maxLength:opt.max||1500,oninput:e=>{o[k]=e.target.value;touch(opt.list);}});
  else if(opt.type==='select') i=h('select',{onchange:e=>{o[k]=opt.num?+e.target.value:e.target.value;touch(opt.list);}},opt.options.map(x=>h('option',{value:x[0],selected:String(o[k])===String(x[0])},x[1])));
  else i=h('input',{type:opt.type||'text',value:o[k]??'',min:opt.min,max:opt.max,maxLength:opt.maxlen,placeholder:opt.ph||'',oninput:e=>{o[k]=opt.type==='number'?(+e.target.value||0):e.target.value;touch(opt.list);}});
  return h('label',{class:'f'},h('span',{},label),i,opt.hint?h('div',{class:'hint'},opt.hint):null);
}
const check=(label,o,k,list)=>h('label',{class:'chk'},h('input',{type:'checkbox',checked:!!o[k],onchange:e=>{o[k]=e.target.checked;touch(list);}}),label);

/* ---------- Ansichten ---------- */
async function render(){
  const app=$('#app'); app.innerHTML='';
  if(!ME){ try{ ME=await A('me'); }catch(e){} }
  if(!ME||!ME.login) return app.append(loginView());
  app.append(headerView());
  if(ME.master&&ME.as&&ME.as!==ME.accounts[0].id) { const a=ME.accounts.find(x=>x.id===ME.as); app.append(h('div',{class:'banner'},'👤 Du bearbeitest das Konto: ',h('b',{},a?a.name:''),h('button',{class:'btn sm',onclick:async()=>{await A('acc_as',{id:''});ME=null;D=null;view='home';render();}},'Zurück zu meinem Konto'))); }
  const m=h('main'); app.append(m);
  if(view==='home') await homeView(m); else if(view==='edit') editView(m); else if(view==='media') await mediaView(m); else if(view==='account') accountView(m);
}
function loginView(){
  const setup=ME&&ME.setup, u=h('input',{type:'text',autocomplete:'username'}), p=h('input',{type:'password',autocomplete:setup?'new-password':'current-password'}), n=h('input',{type:'text',value:'midnox'}), e=h('div',{class:'err'});
  const go=async()=>{ e.textContent=''; try{ await A(setup?'setup':'login',{user:u.value,pw:p.value,name:n.value}); ME=null; view='home'; render(); }catch(x){ e.textContent=x.message; } };
  p.addEventListener('keydown',ev=>{ if(ev.key==='Enter') go(); });
  return h('div',{class:'login card'},h('div',{class:'logo'},'midnox ',h('b',{},'Infoscreen')),
    h('p',{class:'hint'},setup?'Willkommen! Lege jetzt dein Hauptkonto an.':'Bitte anmelden.'),
    setup?h('label',{class:'f'},h('span',{},'Name (Firma / Betrieb)'),n):null,
    h('label',{class:'f'},h('span',{},'Benutzername'),u),h('label',{class:'f'},h('span',{},setup?'Passwort (mind. 8 Zeichen)':'Passwort'),p),e,
    h('button',{class:'btn pri',onclick:go},setup?'Konto anlegen':'Anmelden'));
}
function headerView(){
  const go=v=>async()=>{ if(view==='edit'&&dirty&&!confirm('Ungespeicherte Änderungen verwerfen?')) return; dirty=false; view=v; render(); };
  return h('header',{},h('div',{class:'logo'},'midnox ',h('b',{},'Infoscreen')),
    h('nav',{},h('button',{class:view==='home'||view==='edit'?'on':'',onclick:go('home')},'📺 Bildschirme'),h('button',{class:view==='media'?'on':'',onclick:go('media')},'🖼️ Medien'),h('button',{class:view==='account'?'on':'',onclick:go('account')},ME.master?'⚙️ Konto & Kunden':'⚙️ Konto')),
    h('div',{class:'sp'}),h('span',{class:'hint'},ME.name),h('button',{class:'btn sm',onclick:async()=>{await A('logout',{});ME=null;render();}},'Abmelden'));
}
async function loadScr(){ const r=await A('scr_list'); SCR=r.screens; USED=r.used; QUOTA=r.quota; return r; }
async function homeView(m){
  let r; try{ r=await loadScr(); }catch(e){ return; }
  m.append(h('div',{class:'row',style:'margin-bottom:16px'},h('h1',{style:'margin:0'},'Deine Bildschirme'),h('div',{class:'sp'}),
    h('button',{class:'btn pri',onclick:async()=>{ const n=prompt('Name des Bildschirms (z. B. „Eingang“ oder „Theke“):','Neuer Bildschirm'); if(!n) return; try{ const x=await A('scr_add',{name:n}); await openScreen(x.id); }catch(e){fail(e);} }},'＋ Neuer Bildschirm')));
  if(!SCR.length) m.append(h('div',{class:'card'},h('p',{},'Noch kein Bildschirm angelegt.'),h('p',{class:'hint'},'1. „Neuer Bildschirm“ anlegen · 2. Fire-TV-App „midnox Infoscreen“ starten – dort erscheint ein 6-stelliger Code · 3. Hier beim Bildschirm auf „Fernseher koppeln“ klicken und den Code eingeben.')));
  const g=h('div',{class:'grid'}); m.append(g);
  SCR.forEach(s=>{
    const on=s.devices.some(d=>r.now-d.seen<120), last=Math.max(0,...s.devices.map(d=>d.seen));
    g.append(h('div',{class:'card scr'},h('h3',{},s.name),
      h('div',{class:'m'},h('span',{class:'dot'+(on?' on':'')}),s.devices.length?(on?'Online':'Zuletzt '+(last?new Date(last*1000).toLocaleString('de-DE',{dateStyle:'short',timeStyle:'short'}):'–')):'Kein Fernseher gekoppelt',' · ',s.slides.filter(x=>x.on).length,' aktive Folien · ',(TH.find(t=>t[0]===s.set.theme)||TH[0])[1]),
      h('div',{class:'row'},h('button',{class:'btn pri sm',onclick:()=>openScreen(s.id)},'✏️ Bearbeiten'),h('button',{class:'btn sm',onclick:()=>pair(s.id)},'📡 Fernseher koppeln'),
        h('button',{class:'btn sm',onclick:()=>window.open('?a=player&prev='+s.id,'_blank')},'▶ Vorschau'),
        h('button',{class:'btn sm warn',onclick:async()=>{ if(!confirm('Bildschirm „'+s.name+'“ wirklich löschen? Gekoppelte Fernseher werden getrennt.')) return; try{await A('scr_del',{id:s.id});render();}catch(e){fail(e);} }},'🗑'))));
  });
  m.append(h('p',{class:'hint',style:'margin-top:18px'},'Speicher: '+fmtSize(USED)+' von '+fmtSize(QUOTA)+' belegt'));
}
async function pair(sid){
  const c=prompt('Code vom Fernseher eingeben (6 Ziffern):'); if(!c) return;
  try{ await A('claim',{code:c,sid}); toast('✔ Fernseher gekoppelt – er zeigt gleich die Folien.'); if(view==='home') render(); else if(view==='edit'){ await reloadDevices(); render(); } }catch(e){ fail(e); }
}
async function reloadDevices(){ await loadScr(); }
async function openScreen(id){ await loadScr(); const s=SCR.find(x=>x.id===id); if(!s) return; DSID=id; D=clone(s); sel=0; tab='slide'; dirty=false; view='edit'; render(); }

/* ---------- Bildschirm-Editor ---------- */
function editView(m){
  m.append(h('div',{class:'row',style:'margin-bottom:14px'},h('button',{class:'btn',onclick:async()=>{ if(dirty&&!confirm('Ungespeicherte Änderungen verwerfen?')) return; dirty=false; view='home'; render(); }},'← Bildschirme'),
    h('input',{type:'text',value:D.name,style:'max-width:340px;font-weight:700',oninput:e=>{D.name=e.target.value;touch();}}),h('div',{class:'sp'}),
    h('button',{class:'btn pri',id:'savebtn',onclick:save},dirty?'Speichern & veröffentlichen ●':'Speichern & veröffentlichen')));
  const ed=h('div',{class:'ed'}); m.append(ed);
  const left=h('div',{class:'card'},h('div',{class:'row'},h('h2',{style:'margin:0;flex:1'},'Folien'),addMenu()),h('div',{class:'sl-list',id:'slist'})); ed.append(left);
  const mid=h('div',{class:'card',id:'mid'}); ed.append(mid);
  const pvc=h('div',{class:'card pvcol'},h('div',{class:'row',style:'margin-bottom:8px'},h('b',{},'Live-Vorschau'),h('div',{class:'sp'}),h('button',{class:'btn sm',onclick:()=>{pvFocus=!pvFocus;pvPost();renderMid();}},pvFocus?'▶ Ablauf zeigen':'⏸ Gewählte Folie'),h('button',{class:'btn sm',onclick:()=>window.open('?a=player&prev='+DSID,'_blank')},'↗')),
    h('div',{class:'pv',id:'pv'}),h('p',{class:'hint'},'Das ist die Vorschau. Veröffentlicht wird erst mit „Speichern“ – der Fernseher übernimmt es innerhalb von 30 Sekunden.')); ed.append(pvc);
  renderList(); renderMid(); setupPreview();
}
function addMenu(){
  const sel=h('select',{style:'width:auto;padding:6px 8px',onchange:e=>{ const t=e.target.value; e.target.value=''; if(!t) return; D.slides.push(newSlide(t)); sel_(D.slides.length-1); touch(true); renderMid(); }},
    h('option',{value:''},'＋ Folie'),Object.keys(TYPES).map(t=>h('option',{value:t},TYPES[t])));
  return sel;
}
function sel_(i){ sel=i; }
function renderList(){
  const l=$('#slist'); if(!l) return; l.innerHTML='';
  if(!D.slides.length) l.append(h('p',{class:'hint'},'Noch keine Folien. Oben „＋ Folie“ wählen.'));
  D.slides.forEach((s,i)=>{
    const it=h('div',{class:'si'+(i===sel&&tab==='slide'?' sel':'')+(s.on?'':' off'),draggable:true,
      onclick:()=>{ sel=i; tab='slide'; renderList(); renderMid(); pvPost(); },
      ondragstart:e=>{ e.dataTransfer.setData('text/plain',String(i)); },
      ondragover:e=>{ e.preventDefault(); it.classList.add('over'); }, ondragleave:()=>it.classList.remove('over'),
      ondrop:e=>{ e.preventDefault(); const f=+e.dataTransfer.getData('text/plain'); if(f===i||isNaN(f)) return; const [x]=D.slides.splice(f,1); D.slides.splice(i,0,x); sel=i; touch(true); renderMid(); }},
      h('div',{class:'ic'},ICON[s.type]),h('div',{class:'tt'},h('b',{},sLabel(s)),h('small',{},s.type==='video'?'bis Ende':s.dur+' s',s.sch.use?' · Zeitplan':'',s.on?'':' · aus')),
      h('div',{class:'bt'},h('button',{title:'nach oben',onclick:e=>{e.stopPropagation(); if(i>0){ [D.slides[i-1],D.slides[i]]=[D.slides[i],D.slides[i-1]]; sel=i-1; touch(true); renderMid(); } }},'▲'),
        h('button',{title:'nach unten',onclick:e=>{e.stopPropagation(); if(i<D.slides.length-1){ [D.slides[i+1],D.slides[i]]=[D.slides[i],D.slides[i+1]]; sel=i+1; touch(true); renderMid(); } }},'▼'),
        h('button',{title:'kopieren',onclick:e=>{e.stopPropagation(); const c=clone(s); c.id=rnd(); c.name=(s.name||sLabel(s))+' (Kopie)'; D.slides.splice(i+1,0,c); sel=i+1; touch(true); renderMid(); }},'⧉'),
        h('button',{title:'löschen',onclick:e=>{e.stopPropagation(); if(!confirm('Folie „'+sLabel(s)+'“ löschen?')) return; D.slides.splice(i,1); sel=Math.max(0,Math.min(sel,D.slides.length-1)); touch(true); renderMid(); }},'🗑')));
    l.append(it);
  });
  const st=h('div',{class:'si'+(tab==='set'?' sel':''),style:'margin-top:10px',onclick:()=>{tab='set';renderList();renderMid();pvPost();}},h('div',{class:'ic'},'🎨'),h('div',{class:'tt'},h('b',{},'Design & Einstellungen'),h('small',{},'Theme, Laufband, Logo, Drehung')));
  const dv=h('div',{class:'si'+(tab==='dev'?' sel':''),onclick:()=>{tab='dev';renderList();renderMid();pvPost();}},h('div',{class:'ic'},'📡'),h('div',{class:'tt'},h('b',{},'Geräte'),h('small',{},(SCR.find(x=>x.id===DSID)||{devices:[]}).devices.length+' gekoppelt')));
  l.append(st,dv);
}
function renderMid(){
  const m=$('#mid'); if(!m) return; m.innerHTML='';
  if(tab==='set') return m.append(settingsForm());
  if(tab==='dev') return m.append(devicesForm());
  const s=D.slides[sel]; if(!s) return m.append(h('p',{class:'hint'},'Wähle links eine Folie oder lege eine neue an.'));
  m.append(h('div',{class:'row',style:'margin-bottom:10px'},h('h2',{style:'margin:0'},TYPES[s.type]),h('div',{class:'sp'}),check('Aktiv',s,'on',true)));
  m.append(field('Interner Name (nur im Editor sichtbar)',s,'name',{list:true,ph:sLabel(s)}));
  const F=FORMS[s.type]; if(F) m.append(F(s));
  if(s.type!=='video') m.append(field('Anzeigedauer (Sekunden)',s,'dur',{type:'number',min:3,max:900,list:true}));
  else m.append(h('p',{class:'hint'},'Videos laufen bis zum Ende. Empfohlen: MP4 (H.264).'));
  m.append(scheduleBox(s));
}
function scheduleBox(s){
  const c=s.sch, b=h('div',{class:'box'});
  b.append(check('Nur zu bestimmten Zeiten zeigen (Zeitplan)',c,'use',true));
  const re=()=>{ const x=$('#mid'); const keep=x.scrollTop; renderMid(); };
  b.firstChild.firstChild.addEventListener('change',()=>{ renderMid(); });
  if(!c.use) return b;
  b.append(h('div',{class:'row'},h('div',{style:'flex:1'},field('Von Datum',c,'from',{type:'date',list:true})),h('div',{style:'flex:1'},field('Bis Datum',c,'to',{type:'date',list:true}))));
  b.append(h('div',{class:'hint',style:'margin:-6px 0 10px'},'Leer lassen = ohne Datumsgrenze (z. B. Angebot nur 1.–15. Dezember).'));
  const days=h('div',{class:'days',style:'margin-bottom:12px'}); WD.forEach(([v,n])=>days.append(h('button',{class:c.days.includes(v)?'on':'',onclick:e=>{ const i=c.days.indexOf(v); if(i<0)c.days.push(v); else c.days.splice(i,1); e.target.classList.toggle('on'); touch(true); }},n)));
  b.append(h('span',{class:'hint'},'Wochentage (keine Auswahl = jeden Tag)'),days);
  b.append(h('div',{class:'row'},h('div',{style:'flex:1'},field('Von Uhrzeit',c,'t1',{type:'time',list:true})),h('div',{style:'flex:1'},field('Bis Uhrzeit',c,'t2',{type:'time',list:true}))));
  b.append(h('div',{class:'hint'},'Beispiel: Frühstückskarte 07:00 – 11:00. Über Mitternacht geht auch (z. B. 22:00 – 02:00).'));
  return b;
}
function picker(label,o,k,kind,list){
  const w=h('div',{class:'box'}); const draw=()=>{ w.innerHTML=''; w.append(h('div',{class:'hint',style:'margin-bottom:6px'},label));
    if(o[k]){ w.append(kind==='video'?h('video',{class:'thumb',src:o[k],muted:true,preload:'metadata',style:'max-width:260px'}):h('img',{class:'thumb',src:o[k],style:'max-width:260px'})); }
    w.append(h('div',{class:'row',style:'margin-top:8px'},h('button',{class:'btn sm pri',onclick:()=>mediaModal(kind,u=>{o[k]=u; touch(list); draw();})},o[k]?'Ändern …':(kind==='video'?'Video wählen / hochladen …':'Bild wählen / hochladen …')),o[k]?h('button',{class:'btn sm',onclick:()=>{o[k]='';touch(list);draw();}},'Entfernen'):null)); };
  draw(); return w;
}
const FORMS={
  image:s=>h('div',{},picker('Bild',s,'src','image',true),field('Darstellung',s,'fit',{type:'select',options:[['contain','Ganz zeigen (mit Rand)'],['cover','Bildschirm füllen (zuschneiden)']]}),check('Langsamer Zoom (Ken-Burns-Effekt)',s,'kb'),field('Beschriftung (optional)',s,'cap',{list:false})),
  video:s=>h('div',{},picker('Video',s,'src','video',true),field('Darstellung',s,'fit',{type:'select',options:[['contain','Ganz zeigen (mit Rand)'],['cover','Bildschirm füllen (zuschneiden)']]}),check('Ton abspielen',s,'sound'),field('Beschriftung (optional)',s,'cap')),
  text:s=>h('div',{},field('Auffälliges Etikett (z. B. „Angebot“, „Neu“)',s,'badge',{maxlen:40}),field('Überschrift',s,'head',{list:true,maxlen:200}),field('Text',s,'body',{type:'textarea'}),field('Ausrichtung',s,'align',{type:'select',options:[['center','Mittig'],['left','Linksbündig']]}),picker('Hintergrundbild (optional)',s,'bg','image')),
  menu:s=>menuForm(s),
  clock:s=>h('div',{},field('Beschriftung über der Uhr (optional)',s,'head',{ph:'z. B. Willkommen'})),
  weather:s=>weatherForm(s),
  qr:s=>qrForm(s)
};
function menuForm(s){
  const w=h('div',{}); w.append(field('Überschrift',s,'head',{list:true}),field('Unterzeile (optional)',s,'sub'),picker('Hintergrundbild (optional)',s,'bg','image'));
  const box=h('div',{class:'box'}); const draw=()=>{ box.innerHTML=''; box.append(h('b',{},'Einträge'));
    s.items.forEach((it,i)=>{ box.append(h('div',{class:'mi'},
      h('input',{type:'text',class:'n',value:it.n,placeholder:it.h?'Gruppe, z. B. Getränke':'Name',oninput:e=>{it.n=e.target.value;touch();}}),
      it.h?h('div',{class:'d hint'},'— Überschrift —'):h('input',{type:'text',class:'d',value:it.d,placeholder:'Beschreibung (optional)',oninput:e=>{it.d=e.target.value;touch();}}),
      it.h?null:h('input',{type:'text',class:'p',value:it.p,placeholder:'Preis',oninput:e=>{it.p=e.target.value;touch();}}),
      h('button',{class:'btn sm',onclick:()=>{ if(i>0){[s.items[i-1],s.items[i]]=[s.items[i],s.items[i-1]];touch();draw();} }},'▲'),h('button',{class:'btn sm',onclick:()=>{ if(i<s.items.length-1){[s.items[i+1],s.items[i]]=[s.items[i],s.items[i+1]];touch();draw();} }},'▼'),
      h('button',{class:'btn sm warn',onclick:()=>{s.items.splice(i,1);touch();draw();}},'✕'))); });
    box.append(h('div',{class:'row',style:'margin-top:10px'},h('button',{class:'btn sm',onclick:()=>{s.items.push({n:'',d:'',p:'',h:false});touch();draw();}},'＋ Eintrag'),h('button',{class:'btn sm',onclick:()=>{s.items.push({n:'',d:'',p:'',h:true});touch();draw();}},'＋ Gruppe'),
      h('button',{class:'btn sm',onclick:()=>{ const t=prompt('Zeilen einfügen – je Zeile: Name;Preis;Beschreibung\nZeile mit # am Anfang = Gruppenüberschrift\n\nBeispiel:\n#Getränke\nCola 0,3 l;2,90 €\nPizza Margherita;8,50 €;mit Mozzarella'); if(!t) return; t.split('\n').forEach(l=>{ l=l.trim(); if(!l) return; if(l[0]==='#') s.items.push({n:l.slice(1).trim(),d:'',p:'',h:true}); else { const p=l.split(';'); s.items.push({n:(p[0]||'').trim(),p:(p[1]||'').trim(),d:(p[2]||'').trim(),h:false}); } }); touch(); draw(); }},'📋 Aus Text einfügen'))); };
  draw(); w.append(box,field('Fußzeile (optional)',s,'foot',{ph:'z. B. Alle Preise inkl. MwSt.'})); return w;
}
function weatherForm(s){
  const w=h('div',{}); const res=h('div',{}); const q=h('input',{type:'text',placeholder:'Ort suchen, z. B. Lübeck',value:s.city||''});
  const go=async()=>{ res.innerHTML=''; try{ const r=await (await fetch('https://geocoding-api.open-meteo.com/v1/search?name='+encodeURIComponent(q.value)+'&count=6&language=de&format=json')).json(); (r.results||[]).forEach(x=>res.append(h('div',{class:'si',style:'margin-top:6px',onclick:()=>{ s.city=x.name; s.lat=x.latitude; s.lon=x.longitude; touch(true); renderMid(); }},h('div',{class:'tt'},h('b',{},x.name),h('small',{},[x.admin1,x.country].filter(Boolean).join(', ')))))); if(!(r.results||[]).length) res.append(h('p',{class:'hint'},'Nichts gefunden.')); }catch(e){ res.append(h('p',{class:'err'},'Ortssuche nicht erreichbar.')); } };
  q.addEventListener('keydown',e=>{ if(e.key==='Enter') go(); });
  w.append(h('label',{class:'f'},h('span',{},'Ort'),h('div',{class:'row'},h('div',{style:'flex:1'},q),h('button',{class:'btn',onclick:go},'Suchen'))),res,h('p',{class:'hint'},s.lat?('Gewählt: '+s.city+' ('+s.lat+', '+s.lon+')'):'Noch kein Ort gewählt.')); return w;
}
function qrForm(s){
  const w=h('div',{}); w.append(field('Überschrift (optional)',s,'head'),field('Link oder Text im QR-Code',s,'url',{ph:'https://…'}),field('Beschriftung unter dem Code',s,'cap',{ph:'z. B. Scannen & Speisekarte öffnen'}));
  const ss=h('input',{type:'text',placeholder:'WLAN-Name'}), pw=h('input',{type:'text',placeholder:'WLAN-Passwort'});
  w.append(h('div',{class:'box'},h('b',{},'WLAN-Zugang für Gäste'),h('div',{class:'row',style:'margin:8px 0'},h('div',{style:'flex:1'},ss),h('div',{style:'flex:1'},pw)),h('button',{class:'btn sm',onclick:()=>{ if(!ss.value) return; const e=x=>x.replace(/([\\;,:"])/g,'\\$1'); s.url='WIFI:T:WPA;S:'+e(ss.value)+';P:'+e(pw.value)+';;'; s.head=s.head||'Gratis WLAN'; touch(true); renderMid(); }},'QR-Code für WLAN erzeugen'))); return w;
}
function settingsForm(){
  const S=D.set, w=h('div',{}); w.append(h('h2',{},'Design'));
  const g=h('div',{class:'tg'}); TH.forEach(t=>g.append(h('div',{class:'tp'+(S.theme===t[0]?' on':''),onclick:()=>{S.theme=t[0];touch();renderMid();}},h('i',{style:'background:'+t[3]}),h('b',{},t[1]),h('div',{class:'hint'},t[2]))));
  w.append(g,h('p',{class:'hint'},'„Automatisch“ wechselt selbst: Weihnachten (1.–26.12.), Silvester, Valentinstag, Ostern, Halloween und die vier Jahreszeiten.'));
  w.append(h('div',{class:'row',style:'margin:12px 0'},h('label',{class:'f',style:'margin:0'},h('span',{},'Akzentfarbe (optional)'),h('div',{class:'row'},h('input',{type:'color',value:S.accent||'#8B5CF6',oninput:e=>{S.accent=e.target.value;touch();}}),h('button',{class:'btn sm',onclick:()=>{S.accent='';touch();renderMid();}},'Zurücksetzen'))),
    h('div',{style:'flex:1'},field('Schrift',S,'font',{type:'select',options:[['sans','Modern (Sans)'],['serif','Klassisch (Serif)'],['round','Rund & freundlich']]}))));
  w.append(check('Effekte (Schnee, Blüten, Feuerwerk …)',S,'effects'));
  w.append(h('h2',{style:'margin-top:18px'},'Anzeige'),
    field('Übergang zwischen Folien',S,'trans',{type:'select',options:[['fade','Überblenden'],['slide','Schieben'],['zoom','Zoom'],['none','Ohne']]}),
    field('Bildschirm-Drehung',S,'orient',{type:'select',num:true,options:[[0,'Querformat (normal)'],[90,'Hochformat – Fernseher nach rechts gedreht (90°)'],[270,'Hochformat – Fernseher nach links gedreht (270°)'],[180,'Auf den Kopf (180°)']],hint:'Für Fernseher, die hochkant aufgehängt sind. Die Vorschau zeigt das Ergebnis aufrecht.'}),
    check('Uhrzeit oben rechts einblenden',S,'clock'),
    check('Laufband unten einblenden',S,'tickerOn'),field('Text im Laufband',S,'ticker',{ph:'z. B. Heute Ruhetag · Montag ab 17 Uhr geöffnet · Gutscheine erhältlich'}),
    picker('Logo (oben links, am besten PNG mit transparentem Hintergrund)',S,'logo','image'),
    field('Kiosk-PIN am Fernseher (4–8 Ziffern)',S,'pin',{maxlen:8,hint:'Mit der Fernbedienung 5× „Zurück“ drücken, dann PIN eingeben, um die App zu verlassen.'}));
  return w;
}
function devicesForm(){
  const sc=SCR.find(x=>x.id===DSID)||{devices:[]}, w=h('div',{}); w.append(h('h2',{},'Gekoppelte Fernseher'));
  if(!sc.devices.length) w.append(h('p',{class:'hint'},'Noch kein Fernseher gekoppelt.'));
  sc.devices.forEach(d=>w.append(h('div',{class:'si',style:'margin-bottom:6px'},h('div',{class:'ic'},'📺'),h('div',{class:'tt'},h('b',{},'Fernseher …'+d.dev.slice(-4)),h('small',{},d.seen?'zuletzt gesehen '+new Date(d.seen*1000).toLocaleString('de-DE'):'noch nicht gemeldet')),
    h('button',{class:'btn sm warn',onclick:async()=>{ if(!confirm('Fernseher trennen? Er zeigt dann wieder einen Kopplungscode.')) return; try{ await A('unpair',{dev:d.dev}); await loadScr(); renderList(); renderMid(); }catch(e){fail(e);} }},'Trennen'))));
  w.append(h('div',{class:'box'},h('b',{},'Neuen Fernseher koppeln'),h('p',{class:'hint'},'App „midnox Infoscreen“ am Fire TV starten – der Code steht groß auf dem Bildschirm.'),
    h('button',{class:'btn pri',onclick:()=>{ if(dirty){ toast('Bitte zuerst speichern.',true); return; } pair(DSID); }},'📡 Code eingeben …')));
  return w;
}
async function save(){
  const b=$('#savebtn'); b.disabled=true;
  try{ const r=await A('scr_save',{id:DSID,screen:D}); D.slides=r.slides; D.set=r.set; D.ver=r.ver; dirty=false; sel=Math.min(sel,Math.max(0,D.slides.length-1)); toast('✔ Veröffentlicht – der Fernseher aktualisiert sich in Kürze.'); await loadScr(); renderList(); renderMid(); b.textContent='Speichern & veröffentlichen'; }
  catch(e){ fail(e); } b.disabled=false;
}
addEventListener('keydown',e=>{ if((e.ctrlKey||e.metaKey)&&e.key==='s'&&view==='edit'){ e.preventDefault(); save(); } });
addEventListener('beforeunload',e=>{ if(dirty){ e.preventDefault(); e.returnValue=''; } });

/* ---------- Live-Vorschau ---------- */
function setupPreview(){
  const box=$('#pv'); const port=D.set.orient===90||D.set.orient===270;
  const iw=port?720:1280, ih=port?1280:720;
  pvFrame=h('iframe',{src:'?a=player&prev='+DSID,width:iw,height:ih,onload:()=>setTimeout(pvPost,400)}); box.innerHTML=''; box.append(pvFrame);
  const fit=()=>{ const w=box.clientWidth; const maxH=Math.max(260,innerHeight-330); let k=Math.min(w/iw,maxH/ih); box.style.height=Math.round(ih*k)+'px'; box.style.width=Math.round(iw*k)+'px'; pvFrame.style.transform='scale('+k+')'; };
  fit(); addEventListener('resize',fit); window._pvfit=fit; window._pvport=port;
}
let pvT=0;
function pvPost(){
  clearTimeout(pvT); pvT=setTimeout(()=>{
    if(!pvFrame||!D) return; const port=D.set.orient===90||D.set.orient===270; if(port!==window._pvport){ setupPreview(); return; }
    const s=D.slides[sel], focus=(tab==='slide'&&pvFocus&&s)?s.id:'';
    const slides=D.slides.filter(x=>x.on||x.id===focus);
    try{ pvFrame.contentWindow.postMessage({t:'mxp',content:{ver:0,name:D.name,set:D.set,slides},focus},location.origin); }catch(e){}
  },120);
}

/* ---------- Medien ---------- */
function resizeImg(file){
  return new Promise(res=>{
    if(!/^image\/(jpeg|png|webp)$/.test(file.type)) return res(file);
    const img=new Image(), u=URL.createObjectURL(file);
    img.onload=()=>{ URL.revokeObjectURL(u); const L=2200, m=Math.max(img.width,img.height);
      if(m<=L&&!(file.size>3e6&&file.type!=='image/png')) return res(file);
      const k=Math.min(1,L/m), c=document.createElement('canvas'); c.width=Math.round(img.width*k); c.height=Math.round(img.height*k); c.getContext('2d').drawImage(img,0,0,c.width,c.height);
      const png=file.type==='image/png'; c.toBlob(b=>{ if(!b||b.size>=file.size&&m<=L) return res(file); const nm=file.name.replace(/\.\w+$/,png?'.png':'.jpg'); res(new File([b],nm,{type:png?'image/png':'image/jpeg'})); },png?'image/png':'image/jpeg',.86); };
    img.onerror=()=>{ URL.revokeObjectURL(u); res(file); }; img.src=u;
  });
}
async function uploadFile(f,onp){
  f=await resizeImg(f); const CH=1024*1024, n=Math.max(1,Math.ceil(f.size/CH)), uid=rnd()+rnd(); let last=null;
  for(let i=0;i<n;i++){
    const r=await fetch('?a=up&uid='+uid+'&i='+i+'&n='+n+'&name='+encodeURIComponent(f.name),{method:'POST',headers:{'X-MX':'1'},body:f.slice(i*CH,(i+1)*CH)});
    const j=await r.json().catch(()=>({})); if(!r.ok) throw new Error(j.err||'Upload fehlgeschlagen'); last=j; onp&&onp((i+1)/n);
  } return last;
}
function mediaUI(kind,onPick){
  const wrap=h('div',{}); const grid=h('div',{class:'mgrid'}); const bar=h('div',{class:'bar'},h('i',{style:'width:0'})); const info=h('div',{class:'hint'});
  const load=async()=>{ try{ const r=await A('media_list'); MEDIA=r.media; USED=r.used; QUOTA=r.quota; draw(); }catch(e){fail(e);} };
  const draw=()=>{ grid.innerHTML=''; const l=MEDIA.filter(m=>!kind||m.type===kind||kind==='any'); if(!l.length) grid.append(h('p',{class:'hint'},'Noch keine Dateien.'));
    l.forEach(m=>{ const used=(SCR||[]).reduce((a,s)=>a+s.slides.filter(x=>x.src===m.url||x.bg===m.url).length+(s.set.logo===m.url?1:0),0);
      grid.append(h('div',{class:'mc',onclick:()=>onPick&&onPick(m.url)},m.type==='video'?h('video',{class:'thumb',src:m.url,muted:true,preload:'metadata'}):h('img',{class:'thumb',src:m.url,loading:'lazy'}),h('small',{},m.name,' · ',fmtSize(m.size),used?' · in Benutzung':''),
        h('button',{class:'btn sm warn',style:'position:absolute;right:6px;top:6px',onclick:async e=>{ e.stopPropagation(); if(!confirm((used?'Diese Datei wird noch verwendet! ':'')+'Wirklich löschen?')) return; try{ await A('media_del',{url:m.url}); load(); }catch(x){fail(x);} }},'🗑'))); });
    info.textContent='Speicher: '+fmtSize(USED)+' von '+fmtSize(QUOTA); };
  const inp=h('input',{type:'file',multiple:true,accept:kind==='video'?'video/*':kind==='image'?'image/*':'image/*,video/*',style:'display:none',onchange:()=>up(inp.files)});
  const up=async files=>{ for(const f of files){ try{ info.textContent='Lade hoch: '+f.name+' …'; const r=await uploadFile(f,p=>bar.firstChild.style.width=Math.round(p*100)+'%'); bar.firstChild.style.width='0'; if(files.length===1&&onPick&&(!kind||kind==='any'||r.type===kind)){ onPick(r.url); return; } }catch(e){ fail(e); } } load(); };
  const drop=h('div',{class:'drop',ondragover:e=>{e.preventDefault();drop.classList.add('over');},ondragleave:()=>drop.classList.remove('over'),ondrop:e=>{e.preventDefault();drop.classList.remove('over');up(e.dataTransfer.files);}},
    h('div',{},'Dateien hierher ziehen oder '),h('button',{class:'btn pri',style:'margin-top:8px',onclick:()=>inp.click()},'📁 Dateien auswählen'),inp,h('div',{class:'hint',style:'margin-top:8px'},kind==='video'?'MP4 (H.264) empfohlen – große Videos werden in Stücken hochgeladen.':'Fotos werden automatisch auf Bildschirmgröße verkleinert. Auch Videos (MP4) möglich.'));
  wrap.append(drop,bar,info,grid); load(); return wrap;
}
function mediaModal(kind,onPick){
  const m=h('div',{class:'modal',onclick:e=>{ if(e.target===m) m.remove(); }}); const c=h('div',{class:'card'},h('div',{class:'row',style:'margin-bottom:10px'},h('h2',{style:'margin:0;flex:1'},kind==='video'?'Video wählen':'Bild wählen'),h('button',{class:'btn',onclick:()=>m.remove()},'Schließen')),mediaUI(kind,u=>{ m.remove(); onPick(u); }));
  m.append(c); document.body.append(m);
}
async function mediaView(m){ try{ await loadScr(); }catch(e){} m.append(h('h1',{},'Medien'),h('div',{class:'card'},mediaUI('any',null))); }

/* ---------- Konto ---------- */
function accountView(m){
  const o=h('input',{type:'password'}), n=h('input',{type:'password'});
  m.append(h('h1',{},'Konto'),h('div',{class:'card',style:'max-width:520px;margin-bottom:16px'},h('h2',{},'Passwort ändern'),h('label',{class:'f'},h('span',{},'Altes Passwort'),o),h('label',{class:'f'},h('span',{},'Neues Passwort (mind. 8 Zeichen)'),n),
    h('button',{class:'btn pri',onclick:async()=>{ try{ await A('pw_change',{old:o.value,new:n.value}); o.value=n.value=''; toast('✔ Passwort geändert'); }catch(e){fail(e);} }},'Ändern')));
  if(ME.master){
    const nm=h('input',{type:'text'}), us=h('input',{type:'text'}), pw=h('input',{type:'text'});
    const rows=ME.accounts.map(a=>h('tr',{},h('td',{},h('b',{},a.name),a.master?' (Hauptkonto)':''),h('td',{},a.user),h('td',{},h('div',{class:'row'},
      h('button',{class:'btn sm pri',onclick:async()=>{ await A('acc_as',{id:a.id}).catch(fail); ME=null; D=null; view='home'; render(); }},'Als Kunde bearbeiten'),
      a.master?null:h('button',{class:'btn sm',onclick:async()=>{ const p=prompt('Neues Passwort für '+a.name+' (mind. 8 Zeichen):'); if(!p) return; try{ await A('acc_pw',{id:a.id,pw:p}); toast('✔ Passwort geändert'); }catch(e){fail(e);} }},'Passwort'),
      a.master?null:h('button',{class:'btn sm warn',onclick:async()=>{ if(!confirm('Konto „'+a.name+'“ mit allen Bildschirmen und Dateien unwiderruflich löschen?')) return; try{ await A('acc_del',{id:a.id}); ME=null; render(); }catch(e){fail(e);} }},'Löschen')))));
    m.append(h('div',{class:'card',style:'margin-bottom:16px'},h('h2',{},'Kunden-Konten'),h('p',{class:'hint'},'Jeder Kunde bekommt eigene Bildschirme und Medien. Du kannst jederzeit „als Kunde“ für ihn einrichten.'),h('table',{},h('tr',{},h('th',{},'Name'),h('th',{},'Login'),h('th',{})),rows)),
      h('div',{class:'card',style:'max-width:520px'},h('h2',{},'Neues Kunden-Konto'),h('label',{class:'f'},h('span',{},'Name (Betrieb)'),nm),h('label',{class:'f'},h('span',{},'Benutzername'),us),h('label',{class:'f'},h('span',{},'Passwort (mind. 8 Zeichen)'),pw),
        h('button',{class:'btn pri',onclick:async()=>{ try{ await A('acc_add',{name:nm.value,user:us.value,pw:pw.value}); ME=null; render(); }catch(e){fail(e);} }},'Konto anlegen')));
  }
  m.append(h('p',{class:'hint',style:'margin-top:18px'},'midnox Infoscreen '+(ME.ver||'')));
}
render();
})();
</script>
</body></html>

MXI_EDITOR_END;
const MXI_PLAYER = <<<'MXI_PLAYER_END'
<!doctype html><html lang="de"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>midnox Infoscreen</title>
<style>
  html,body{margin:0;height:100%;background:#000;overflow:hidden;cursor:none}
  *{box-sizing:border-box}
  #root{position:absolute;left:0;top:0;overflow:hidden;color:var(--tx);font-family:var(--ff);transform-origin:0 0;--u:10px;font-size:calc(var(--u)*3);--tx:#fff;--mut:#b8b4d8;--mut2:#b8b4d8;--ac:#8B5CF6;--ac2:#F59E0B;--pan:rgba(255,255,255,.07);--pb:rgba(255,255,255,.14);--bg:#0B0A14;--ff:system-ui,'Segoe UI',Roboto,Helvetica,Arial,sans-serif}
  #bg{position:absolute;inset:0;background:var(--bg);transition:opacity 1.5s}
  #fx{position:absolute;inset:0;pointer-events:none;width:100%;height:100%}
  #stage{position:absolute;left:0;right:0;top:0;bottom:0;overflow:hidden}
  .sl{position:absolute;inset:0;opacity:0;transition:opacity .9s ease,transform .9s ease}
  .sl.in{opacity:1;transform:none!important}
  [data-tr=slide] .sl:not(.in):not(.out){transform:translateX(7%)}
  [data-tr=slide] .sl.out{transform:translateX(-7%)}
  [data-tr=zoom] .sl:not(.in):not(.out){transform:scale(1.08)}
  [data-tr=zoom] .sl.out{transform:scale(.94)}
  [data-tr=none] .sl{transition:none}
  /* Folien */
  .pad{padding:calc(var(--u)*6) calc(var(--u)*7) calc(var(--u)*6)}
  .bgimg{position:absolute;inset:0;background-size:cover;background-position:center}
  .shade{position:absolute;inset:0;background:linear-gradient(180deg,rgba(0,0,0,.55),rgba(0,0,0,.7))}
  .im{position:absolute;inset:0;width:100%;height:100%}
  .im.contain{object-fit:contain}.im.cover{object-fit:cover}
  .blur{position:absolute;inset:-5%;background-size:cover;background-position:center;filter:blur(calc(var(--u)*3)) brightness(.55);}
  @keyframes kb{from{transform:scale(1)}to{transform:scale(1.12)}}
  .cap{position:absolute;left:calc(var(--u)*4);bottom:calc(var(--u)*4);background:rgba(0,0,0,.6);color:#fff;padding:calc(var(--u)*.8) calc(var(--u)*2);border-radius:calc(var(--u)*1.2);font-size:calc(var(--u)*2.4);max-width:80%}
  .tx{position:absolute;inset:0;display:flex;flex-direction:column;justify-content:center;gap:calc(var(--u)*2);z-index:2}
  .tx.center{align-items:center;text-align:center}
  .tx.dark{color:#fff}
  .badge{align-self:inherit;display:inline-block;background:var(--ac2);color:#111;font-weight:900;letter-spacing:.15em;text-transform:uppercase;padding:calc(var(--u)*.7) calc(var(--u)*2.2);border-radius:calc(var(--u)*4);font-size:calc(var(--u)*2.2)}
  .hd{margin:0;font-weight:900;line-height:1.05;letter-spacing:-.01em}
  .bar{width:calc(var(--u)*14);height:calc(var(--u)*.8);border-radius:9px;background:linear-gradient(90deg,var(--ac),var(--ac2))}
  .bd{margin:0;line-height:1.3;color:var(--mut2)}
  .tx.dark .bd{color:#ececf5}
  .menu{position:absolute;inset:calc(var(--u)*5) calc(var(--u)*6);display:flex;flex-direction:column;z-index:2}
  .menu .panel{flex:1;min-height:0;background:var(--pan);border:2px solid var(--pb);border-radius:calc(var(--u)*2.4);padding:calc(var(--u)*3) calc(var(--u)*4);overflow:hidden;display:flex;flex-direction:column;backdrop-filter:blur(6px)}
  .menu .cols{flex:1;min-height:0;column-gap:calc(var(--u)*5)}
  .it{break-inside:avoid;margin-bottom:.55em}
  .it .r{display:flex;align-items:baseline;gap:.5em}
  .it .n{font-weight:800}
  .it .dots{flex:1;border-bottom:.12em dotted var(--mut);transform:translateY(-.25em);opacity:.6}
  .it .p{font-weight:900;color:var(--ac2);white-space:nowrap}
  .it .d{color:var(--mut);font-size:.72em;margin-top:.05em;line-height:1.25}
  .it.h{margin:.9em 0 .45em;break-after:avoid}
  .it.h .n{color:var(--ac);text-transform:uppercase;letter-spacing:.12em;font-size:.82em;border-bottom:.1em solid var(--pb);display:block;padding-bottom:.25em}
  .foot{margin-top:calc(var(--u)*1.5);color:var(--mut);font-size:calc(var(--u)*2.1);text-align:center}
  .clk{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:calc(var(--u)*1.5);z-index:2}
  .clk .t{font-size:calc(var(--u)*24);font-weight:900;line-height:1;letter-spacing:-.02em;font-variant-numeric:tabular-nums}
  .clk .t i{font-style:normal;animation:bl 1s steps(2) infinite}
  @keyframes bl{50%{opacity:.25}}
  .clk .dt{font-size:calc(var(--u)*4.6);color:var(--mut2)}
  .clk .lb{font-size:calc(var(--u)*3.4);color:var(--ac);font-weight:800;text-transform:uppercase;letter-spacing:.2em}
  .wx{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:calc(var(--u)*2.5);z-index:2}
  .wx .cur{display:flex;align-items:center;gap:calc(var(--u)*3)}
  .wx .ic{font-size:calc(var(--u)*18)}
  .wx .tp{font-size:calc(var(--u)*17);font-weight:900;line-height:1}
  .wx .ci{font-size:calc(var(--u)*4.2);color:var(--mut2);font-weight:700}
  .wx .fc{display:flex;gap:calc(var(--u)*3);margin-top:calc(var(--u)*1.5)}
  .wx .fc div{background:var(--pan);border:2px solid var(--pb);border-radius:calc(var(--u)*2);padding:calc(var(--u)*1.6) calc(var(--u)*3);text-align:center;font-size:calc(var(--u)*2.6);min-width:calc(var(--u)*17)}
  .wx .fc b{display:block;color:var(--ac)}.wx .fc .e{font-size:calc(var(--u)*7);line-height:1.2}
  .qr{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:calc(var(--u)*2.2);z-index:2;text-align:center}
  .qr .box{background:#fff;padding:calc(var(--u)*2);border-radius:calc(var(--u)*2)}
  .qr .box svg{display:block;width:calc(var(--u)*28);height:calc(var(--u)*28)}
  /* Overlays */
  #logo{position:absolute;left:calc(var(--u)*2.5);top:calc(var(--u)*2.2);height:calc(var(--u)*6.5);max-width:calc(var(--u)*24);z-index:6;display:none;object-fit:contain;filter:drop-shadow(0 2px 6px rgba(0,0,0,.4))}
  #clk{position:absolute;right:calc(var(--u)*2.5);top:calc(var(--u)*2.2);z-index:6;display:none;background:rgba(0,0,0,.55);color:#fff;font-weight:800;font-size:calc(var(--u)*3);padding:calc(var(--u)*.6) calc(var(--u)*2);border-radius:calc(var(--u)*3);font-variant-numeric:tabular-nums}
  #tick{position:absolute;left:0;right:0;bottom:0;height:calc(var(--u)*6.4);background:var(--ac);color:#fff;z-index:6;display:none;overflow:hidden;white-space:nowrap;font-weight:800;font-size:calc(var(--u)*3);line-height:calc(var(--u)*6.4)}
  #tick span{display:inline-block;will-change:transform;padding-left:2em}
  #pair{color:#fff;font-family:system-ui,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;position:absolute;inset:0;z-index:20;display:none;flex-direction:column;align-items:center;justify-content:center;gap:calc(var(--u)*2.4);text-align:center;background:#0B0A14}
  #pair h1{margin:0;font-size:calc(var(--u)*7);font-weight:900}#pair h1 b{color:#F59E0B}
  #pair .code{font-size:calc(var(--u)*17);font-weight:900;letter-spacing:.12em;background:linear-gradient(90deg,#8B5CF6,#F59E0B);-webkit-background-clip:text;background-clip:text;color:transparent;font-variant-numeric:tabular-nums}
  #pair p{margin:0;font-size:calc(var(--u)*2.8);color:#b8b4d8;max-width:calc(var(--u)*80);line-height:1.35}
  #pair p b{color:#fff}
  #toast{position:absolute;left:50%;bottom:calc(var(--u)*9);transform:translateX(-50%);background:rgba(0,0,0,.75);color:#fff;padding:calc(var(--u)*1) calc(var(--u)*3);border-radius:calc(var(--u)*2);font-size:calc(var(--u)*2.6);z-index:30;display:none}
</style></head><body>
<div id="root" data-tr="fade">
  <div id="bg"></div><canvas id="fx"></canvas>
  <div id="stage"></div>
  <img id="logo" alt=""><div id="clk"></div><div id="tick"><span></span></div>
  <div id="pair"><h1>midnox <b>Infoscreen</b></h1><div class="code" id="pcode">------</div>
    <p>Am PC öffnen: <b id="purl"></b><br>Anmelden → Bildschirm wählen → <b>„Fernseher koppeln“</b> → diesen Code eingeben.</p></div>
  <div id="toast"></div>
</div>
<script src="?a=qr.js"></script>
<script>
(function(){
'use strict';
const $=s=>document.querySelector(s), root=$('#root'), stage=$('#stage'), qs=new URLSearchParams(location.search);
const PREV=qs.get('prev')||'', FOCUS0=qs.get('focus')||'';
const BASE=location.href.split('?')[0];
const api=(q,o)=>fetch(BASE+'?'+q,Object.assign({cache:'no-store'},o||{})).then(r=>r.json());
const el=(t,c,h)=>{const e=document.createElement(t); if(c)e.className=c; if(h!=null)e.textContent=h; return e;};
const media=u=>u?new URL(u,BASE).href:'';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
let C=null, offset=0, dev=localStorage.getItem('mxi_dev')||'', hold='', lastVer=0, cur=null, curId='', timer=0, W=0, H=0;
const nowD=()=>new Date(Date.now()+offset);

/* ---------- Themes ---------- */
const TH={
 midnox:{bg:'radial-gradient(120% 90% at 85% 0%,#3b1a7a 0,transparent 55%),radial-gradient(100% 80% at 0% 100%,#4a1342 0,transparent 55%),#0B0A14',tx:'#fff',mut:'#b8b4d8',ac:'#8B5CF6',ac2:'#F59E0B',pan:'rgba(255,255,255,.07)',pb:'rgba(255,255,255,.14)',fx:'glow'},
 clean:{bg:'#f6f5f1',tx:'#1d1b2e',mut:'#6a6880',ac:'#7C3AED',ac2:'#d97706',pan:'#ffffff',pb:'#e5e2da',fx:''},
 weihnachten:{bg:'linear-gradient(160deg,#0d2418,#14391f 55%,#3d0d12)',tx:'#fff',mut:'#d6e9d9',ac:'#dc2626',ac2:'#fbbf24',pan:'rgba(255,255,255,.08)',pb:'rgba(251,191,36,.35)',fx:'snow'},
 silvester:{bg:'linear-gradient(160deg,#04040f,#1a1040 70%,#2a0d3d)',tx:'#fff',mut:'#cfc9f0',ac:'#fbbf24',ac2:'#f472b6',pan:'rgba(255,255,255,.07)',pb:'rgba(251,191,36,.35)',fx:'fireworks'},
 ostern:{bg:'linear-gradient(160deg,#fff9d9,#e6f6df 55%,#fbe3f1)',tx:'#2b3a2b',mut:'#5f7a5f',ac:'#d946a8',ac2:'#ca8a04',pan:'rgba(255,255,255,.75)',pb:'#e7d9ee',fx:'petals'},
 halloween:{bg:'radial-gradient(100% 80% at 80% 0%,#3a0f55 0,transparent 60%),#0c0612',tx:'#fff',mut:'#d9c3ea',ac:'#f97316',ac2:'#a3e635',pan:'rgba(255,255,255,.07)',pb:'rgba(249,115,22,.4)',fx:'bats'},
 valentin:{bg:'linear-gradient(160deg,#3a0a1e,#7a1237)',tx:'#fff',mut:'#fbcfe8',ac:'#fb7185',ac2:'#fde68a',pan:'rgba(255,255,255,.09)',pb:'rgba(251,113,133,.4)',fx:'hearts'},
 fruehling:{bg:'linear-gradient(160deg,#eaf8e4,#fdf1f7)',tx:'#25382a',mut:'#5f7a63',ac:'#16a34a',ac2:'#db2777',pan:'rgba(255,255,255,.78)',pb:'#d5e8d0',fx:'petals'},
 sommer:{bg:'linear-gradient(160deg,#0b7fb8,#2aa6d6 55%,#f6c445)',tx:'#fff',mut:'#e9f6ff',ac:'#fde047',ac2:'#fb923c',pan:'rgba(0,40,70,.28)',pb:'rgba(255,255,255,.35)',fx:'sun'},
 herbst:{bg:'linear-gradient(160deg,#341a08,#7c3a0e)',tx:'#fff',mut:'#f3dcc0',ac:'#f59e0b',ac2:'#fb7185',pan:'rgba(255,255,255,.08)',pb:'rgba(245,158,11,.4)',fx:'leaves'},
 winter:{bg:'linear-gradient(160deg,#0b1d33,#1f4068)',tx:'#fff',mut:'#c7def5',ac:'#7dd3fc',ac2:'#fde68a',pan:'rgba(255,255,255,.08)',pb:'rgba(125,211,252,.35)',fx:'snow'}
};
const FONTS={sans:"system-ui,'Segoe UI',Roboto,Helvetica,Arial,sans-serif",serif:"Georgia,'Times New Roman',serif",round:"'Trebuchet MS','Varela Round',system-ui,sans-serif"};
function easter(y){const a=y%19,b=Math.floor(y/100),c=y%100,d=Math.floor(b/4),e=b%4,f=Math.floor((b+8)/25),g=Math.floor((b-f+1)/3),h=(19*a+b-d-g+15)%30,i=Math.floor(c/4),k=c%4,l=(32+2*e+2*i-h-k)%7,m=Math.floor((a+11*h+22*l)/451),mo=Math.floor((h+l-7*m+114)/31),da=((h+l-7*m+114)%31)+1;return new Date(y,mo-1,da);}
function autoTheme(d){
  const md=(d.getMonth()+1)*100+d.getDate(), y=d.getFullYear();
  if(md>=1227||md<=102) return 'silvester';
  if(md>=1201&&md<=1226) return 'weihnachten';
  if(md>=1024&&md<=1101) return 'halloween';
  if(md>=207&&md<=214) return 'valentin';
  const E=easter(y), diff=Math.round((new Date(y,d.getMonth(),d.getDate())-E)/864e5);
  if(diff>=-10&&diff<=1) return 'ostern';
  if(md>=301&&md<=531) return 'fruehling';
  if(md>=601&&md<=831) return 'sommer';
  if(md>=901&&md<=1130) return 'herbst';
  return 'winter';
}
let themeName='';
function applyTheme(){
  const s=(C&&C.set)||{}; let n=s.theme||'auto'; if(n==='auto') n=autoTheme(nowD()); if(!TH[n]) n='midnox';
  const t=Object.assign({},TH[n]); if(s.accent) t.ac=s.accent;
  const key=n+'|'+(s.accent||'')+'|'+(s.font||'')+'|'+(s.effects!==false);
  if(key===themeName) return; themeName=key;
  const st=root.style; st.setProperty('--bg',t.bg); st.setProperty('--tx',t.tx); st.setProperty('--mut',t.mut); st.setProperty('--mut2',t.mut);
  st.setProperty('--ac',t.ac); st.setProperty('--ac2',t.ac2); st.setProperty('--pan',t.pan); st.setProperty('--pb',t.pb); st.setProperty('--ff',FONTS[s.font]||FONTS.sans);
  root.dataset.theme=n; FX.set(s.effects===false?'':t.fx);
}

/* ---------- Effekte (leichtgewichtige Partikel) ---------- */
const FX=(function(){
  const cv=$('#fx'), cx=cv.getContext('2d'); let kind='', P=[], raf=0, last=0, boom=[], t0=0;
  const R=(a,b)=>a+Math.random()*(b-a);
  function mk(){
    P=[]; const n=kind==='snow'?55:kind==='glow'?6:kind==='sun'?10:kind==='fireworks'?0:24;
    for(let i=0;i<n;i++) P.push(spawn(true));
  }
  function spawn(init){
    const p={x:R(0,W),y:init?R(0,H):-40,vx:R(-12,12),vy:R(25,60),s:R(.6,1.4),r:R(0,6),vr:R(-1,1),a:R(.5,1)};
    if(kind==='snow'){p.g='❄';p.vy=R(30,80);p.s=R(.35,1.1);p.a=R(.35,.9);}
    if(kind==='petals'){p.g=['🌸','🌼','🌷','🥚'][(Math.random()*4)|0];p.s=R(.8,1.4);p.vy=R(25,55);}
    if(kind==='hearts'){p.g=['❤','💗','💖'][(Math.random()*3)|0];p.vy=-R(25,60);p.y=init?R(0,H):H+40;p.s=R(.7,1.5);}
    if(kind==='leaves'){p.g=['🍂','🍁','🍂'][(Math.random()*3)|0];p.vy=R(30,65);p.vx=R(-30,30);}
    if(kind==='bats'){p.g='🦇';p.vy=R(-10,10);p.vx=R(40,90);p.x=init?R(0,W):-60;p.y=R(0,H*.6);p.s=R(.7,1.5);}
    if(kind==='glow'){p.x=R(0,W);p.y=R(0,H);p.vx=R(-14,14);p.vy=R(-10,10);p.s=R(160,380);p.hue=['139,92,246','245,158,11','236,72,153'][(Math.random()*3)|0];p.a=R(.08,.18);}
    if(kind==='sun'){p.g='✨';p.vy=-R(8,22);p.y=init?R(0,H):H+30;p.s=R(.6,1.3);}
    return p;
  }
  function frame(t){
    raf=requestAnimationFrame(frame); if(t-last<33) return; const dt=Math.min(.1,(t-last)/1000); last=t;
    cx.clearRect(0,0,W,H); const u=W/100;
    if(kind==='sun'){ const g=cx.createRadialGradient(W*.92,-H*.05,0,W*.92,-H*.05,H*(.9+.05*Math.sin(t/1500))); g.addColorStop(0,'rgba(255,240,150,.55)'); g.addColorStop(1,'rgba(255,240,150,0)'); cx.fillStyle=g; cx.fillRect(0,0,W,H); }
    if(kind==='fireworks'){
      if(Math.random()<dt*.9) { const x=R(W*.12,W*.88),y=R(H*.12,H*.55),c=['#fbbf24','#f472b6','#60a5fa','#34d399','#fff'][(Math.random()*5)|0]; for(let i=0;i<46;i++){const a=R(0,6.283),v=R(60,260)*u/10; boom.push({x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v,l:1,c});} }
      for(let i=boom.length-1;i>=0;i--){const b=boom[i]; b.x+=b.vx*dt; b.y+=b.vy*dt; b.vy+=90*dt; b.vx*=.985; b.l-=dt*.55; if(b.l<=0){boom.splice(i,1);continue;} cx.globalAlpha=Math.max(0,b.l); cx.fillStyle=b.c; cx.beginPath(); cx.arc(b.x,b.y,u*.28,0,6.283); cx.fill();}
      cx.globalAlpha=1; return;
    }
    for(const p of P){
      p.x+=p.vx*dt; p.y+=p.vy*dt; p.r+=p.vr*dt;
      if(kind==='snow'||kind==='petals'||kind==='leaves') p.x+=Math.sin(p.y/60+p.r)*14*dt;
      if(kind==='glow'){ if(p.x<-p.s||p.x>W+p.s) p.vx*=-1; if(p.y<-p.s||p.y>H+p.s) p.vy*=-1;
        const g=cx.createRadialGradient(p.x,p.y,0,p.x,p.y,p.s); g.addColorStop(0,'rgba('+p.hue+','+p.a+')'); g.addColorStop(1,'rgba('+p.hue+',0)'); cx.fillStyle=g; cx.fillRect(p.x-p.s,p.y-p.s,p.s*2,p.s*2); continue; }
      if(p.y>H+50||p.y<-80||p.x>W+80){ Object.assign(p,spawn(false)); }
      cx.save(); cx.globalAlpha=p.a; cx.translate(p.x,p.y); cx.rotate(kind==='bats'||kind==='hearts'?0:p.r); cx.font=Math.round(u*3*p.s)+'px sans-serif'; cx.textAlign='center'; cx.textBaseline='middle';
      if(kind==='snow'){cx.fillStyle='#fff';} cx.fillText(p.g,0,0); cx.restore();
    }
  }
  return {set(k){kind=k; cancelAnimationFrame(raf); cx.clearRect(0,0,cv.width,cv.height); boom=[]; if(!k) return; mk(); raf=requestAnimationFrame(frame);}, resize(){cv.width=W;cv.height=H; if(kind) mk();}};
})();

/* ---------- Größe / Drehung ---------- */
function layout(){
  const iw=innerWidth, ih=innerHeight, r=PREV?0:((C&&C.set&&C.set.orient)||0);
  const sw=(r===90||r===270); W=sw?ih:iw; H=sw?iw:ih;
  root.style.width=W+'px'; root.style.height=H+'px';
  root.style.setProperty('--u',Math.min(W/100,H/56.25)+'px');
  root.style.transform=r===90?`translate(${iw}px,0) rotate(90deg)`:r===270?`translate(0,${ih}px) rotate(-90deg)`:r===180?`translate(${iw}px,${ih}px) rotate(180deg)`:'none';
  FX.resize(); stage.style.bottom=(C&&C.set&&C.set.tickerOn&&C.set.ticker)?'calc(var(--u)*6.4)':'0';
}
addEventListener('resize',layout);

/* ---------- Zeitplan ---------- */
function active(s){
  const sc=s.sch; if(!sc||!sc.use) return true;
  const d=nowD(), ds=d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');
  if(sc.from&&ds<sc.from) return false; if(sc.to&&ds>sc.to) return false;
  if(sc.days&&sc.days.length&&sc.days.indexOf(d.getDay())<0) return false;
  const hm=String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');
  if(sc.t1&&sc.t2){ if(sc.t1<=sc.t2){ if(hm<sc.t1||hm>=sc.t2) return false; } else if(hm<sc.t1&&hm>=sc.t2) return false; }
  else if(sc.t1&&hm<sc.t1) return false; else if(sc.t2&&hm>=sc.t2) return false;
  return true;
}

/* ---------- Folien bauen: liefern {el, stop(), dur(ms)|0, video} ---------- */
const WD=['Sonntag','Montag','Dienstag','Mittwoch','Donnerstag','Freitag','Samstag'], MO=['Januar','Februar','März','April','Mai','Juni','Juli','August','September','Oktober','November','Dezember'];
const hm=d=>String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');
const WMO=c=>c===0?'☀️':c<=1?'🌤️':c===2?'⛅':c===3?'☁️':(c===45||c===48)?'🌫️':c<=57?'🌦️':c<=67?'🌧️':c<=77?'🌨️':c<=82?'🌦️':c<=86?'🌨️':'⛈️';
const B={};
B.image=s=>{
  const d=el('div','sl'), u=media(s.src); const o={el:d,stop(){},dur:0};
  if(s.fit!=='cover'){ const b=el('div','blur'); b.style.backgroundImage='url("'+u+'")'; d.appendChild(b); }
  const im=el('img','im '+(s.fit==='cover'?'cover':'contain')); im.src=u; im.onerror=()=>{o.fail=true;}; if(s.kb) im.style.animation='kb '+(s.dur+2)+'s ease-out forwards'; d.appendChild(im);
  if(s.cap) d.appendChild(el('div','cap',s.cap)); return o;
};
B.video=s=>{
  const d=el('div','sl'); d.style.background='#000'; const v=document.createElement('video'); v.className='im '+(s.fit==='cover'?'cover':'contain');
  v.src=media(s.src); v.muted=!s.sound; v.autoplay=true; v.setAttribute('playsinline',''); v.preload='auto'; d.appendChild(v);
  const o={el:d,video:v,dur:0,stop(){try{v.pause();v.removeAttribute('src');v.load();}catch(e){}}};
  v.addEventListener('error',()=>{o.fail=true;}); if(s.cap) d.appendChild(el('div','cap',s.cap));
  o.start=()=>{ const p=v.play(); if(p&&p.catch) p.catch(()=>{ v.muted=true; v.play().catch(()=>{}); }); };
  return o;
};
B.text=s=>{
  const d=el('div','sl'); const o={el:d,stop(){},dur:0};
  if(s.bg){ const b=el('div','bgimg'); b.style.backgroundImage='url("'+media(s.bg)+'")'; d.appendChild(b); d.appendChild(el('div','shade')); }
  const w=el('div','tx pad '+(s.align==='left'?'':'center')+(s.bg?' dark':'')); d.appendChild(w);
  if(s.badge) w.appendChild(el('span','badge',s.badge));
  const hl=(s.head||'').length, bl=(s.body||'').length;
  if(s.head){ const h=el('h1','hd',s.head); h.style.fontSize='calc(var(--u)*'+(hl>50?5:hl>28?7:9.5)+'*var(--k,1))'; w.appendChild(h); w.appendChild(el('div','bar')); if(s.align==='center'){w.lastChild.style.alignSelf='center';} }
  if(s.body){ const sz=bl>700?2.6:bl>420?3.1:bl>200?3.8:4.8; (s.body.split(/\n+/)).forEach(t=>{ const p=el('p','bd',t); p.style.fontSize='calc(var(--u)*'+sz+'*var(--k,1))'; w.appendChild(p); }); }
  o.after=()=>{ let k=1; w.style.setProperty('--k',1); while(w.scrollHeight>w.clientHeight+2&&k>.35){ k*=.92; w.style.setProperty('--k',k); } };
  return o;
};
B.menu=s=>{
  const d=el('div','sl'); const o={el:d,stop(){},dur:0};
  if(s.bg){ const b=el('div','bgimg'); b.style.backgroundImage='url("'+media(s.bg)+'")'; d.appendChild(b); d.appendChild(el('div','shade')); }
  const m=el('div','menu'); d.appendChild(m); const pn=el('div','panel'); m.appendChild(pn);
  if(s.head){ const h=el('h1','hd',s.head); h.style.fontSize='calc(var(--u)*6)'; pn.appendChild(h); }
  if(s.sub){ const p=el('div','bd',s.sub); p.style.cssText='font-size:calc(var(--u)*2.6);margin:.2em 0 .5em;color:var(--ac)'; pn.appendChild(p); }
  const items=s.items||[], rows=items.reduce((a,i)=>a+(i.h?1.4:1)+(i.d&&!i.h?.6:0),0), cols=rows>22?3:rows>9?2:1;
  let fs=Math.min(3.7,Math.max(1.6,30/((rows/cols)*1.9)));
  const c=el('div','cols'); c.style.columnCount=cols; c.style.fontSize='calc(var(--u)*'+fs+')'; pn.appendChild(c);
  items.forEach(i=>{ const e=el('div','it'+(i.h?' h':'')); const r=el('div','r'); r.appendChild(el('span','n',i.n)); if(!i.h&&i.p){ r.appendChild(el('span','dots')); r.appendChild(el('span','p',i.p)); } e.appendChild(r); if(i.d&&!i.h) e.appendChild(el('div','d',i.d)); c.appendChild(e); });
  o.after=()=>{ let g=0; while(c.scrollWidth>c.clientWidth+4&&fs>1.1&&g++<40){ fs*=.94; c.style.fontSize='calc(var(--u)*'+fs+')'; } };
  if(s.foot) pn.appendChild(el('div','foot',s.foot)); return o;
};
B.clock=s=>{
  const d=el('div','sl'); const w=el('div','clk'); d.appendChild(w); const lb=el('div','lb',s.head||''); const t=el('div','t'); const dt=el('div','dt'); if(s.head) w.appendChild(lb); w.appendChild(t); w.appendChild(dt);
  const upd=()=>{ const n=nowD(); t.innerHTML=String(n.getHours()).padStart(2,'0')+'<i>:</i>'+String(n.getMinutes()).padStart(2,'0'); dt.textContent=WD[n.getDay()]+', '+n.getDate()+'. '+MO[n.getMonth()]+' '+n.getFullYear(); };
  upd(); const iv=setInterval(upd,1000); return {el:d,dur:0,stop(){clearInterval(iv);}};
};
const WXC={};
B.weather=s=>{
  const d=el('div','sl'); const w=el('div','wx'); d.appendChild(w); const o={el:d,dur:0,stop(){}};
  const draw=j=>{ w.innerHTML=''; const cur=el('div','cur'); cur.appendChild(el('div','ic',WMO(j.current.weather_code))); cur.appendChild(el('div','tp',Math.round(j.current.temperature_2m)+'°')); w.appendChild(cur); w.appendChild(el('div','ci',s.city||'')); const fc=el('div','fc');
    for(let i=1;i<=3;i++){ const dd=new Date(j.daily.time[i]+'T12:00:00'); const x=el('div'); x.appendChild(el('b','',WD[dd.getDay()].slice(0,2))); x.appendChild(el('div','e',WMO(j.daily.weather_code[i]))); x.appendChild(el('div','',Math.round(j.daily.temperature_2m_max[i])+'° / '+Math.round(j.daily.temperature_2m_min[i])+'°')); fc.appendChild(x); } w.appendChild(fc); };
  const key='wx_'+s.lat+'_'+s.lon, hit=WXC[key]; if(hit&&Date.now()-hit.t<20*60e3){ draw(hit.j); return o; }
  w.appendChild(el('div','ci','Wetter wird geladen …'));
  fetch('https://api.open-meteo.com/v1/forecast?latitude='+s.lat+'&longitude='+s.lon+'&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=4').then(r=>r.json()).then(j=>{ if(!j.current) throw 0; WXC[key]={t:Date.now(),j}; try{localStorage.setItem(key,JSON.stringify(WXC[key]));}catch(e){} draw(j); }).catch(()=>{ let c; try{c=JSON.parse(localStorage.getItem(key));}catch(e){} if(c&&c.j) draw(c.j); else { w.innerHTML=''; w.appendChild(el('div','ci','Wetter momentan nicht verfügbar')); o.fail=true; } });
  return o;
};
B.qr=s=>{
  const d=el('div','sl'); const w=el('div','qr'); d.appendChild(w);
  if(s.head){ const h=el('h1','hd',s.head); h.style.fontSize='calc(var(--u)*6)'; w.appendChild(h); }
  const b=el('div','box'); try{ const q=qrcode(0,'M'); q.addData(s.url||'https://midnox.de'); q.make(); b.innerHTML=q.createSvgTag({cellSize:4,margin:0,scalable:true}); }catch(e){ b.textContent='QR-Fehler'; } w.appendChild(b);
  if(s.cap){ const p=el('div','bd',s.cap); p.style.fontSize='calc(var(--u)*3)'; w.appendChild(p); }
  return {el:d,dur:0,stop(){}};
};

/* ---------- Ablauf ---------- */
function build(s){ const f=B[s.type]; try{ return f?f(s):null; }catch(e){ return null; } }
function show(s){
  const o=build(s)||{el:el('div','sl'),stop(){},dur:0,fail:true};
  stage.appendChild(o.el); if(o.after) try{o.after();}catch(e){} void o.el.offsetWidth; o.el.classList.add('in');
  const old=cur; cur=o; curId=s.id||'';
  if(old){ old.el.classList.remove('in'); old.el.classList.add('out'); setTimeout(()=>{ try{old.stop();}catch(e){} old.el.remove(); },1000); }
  if(o.start) o.start();
  clearTimeout(timer);
  if(hold) return;                                   // Editor-Vorschau: Folie festhalten
  if(o.video){ const v=o.video;
    v.addEventListener('ended',()=>{ if(cur!==o) return; if(single()){ v.currentTime=0; v.play().catch(()=>{}); } else next(); });
    const chk=()=>{ if(cur!==o) return; if(o.fail){ next(); return; } timer=setTimeout(chk,2000); };
    timer=setTimeout(chk,3000);
    o._guard=setTimeout(()=>{ if(cur===o&&!single()) next(); },15*60e3);   // Sicherung gegen hängende Videos
    const os=o.stop; o.stop=()=>{ clearTimeout(o._guard); os(); };
  } else {
    timer=setTimeout(next,o.fail?2500:Math.max(3,(s.dur||10))*1000);
  }
}
function list(){ return ((C&&C.slides)||[]).filter(active); }
function single(){ return list().length<=1; }
function idleSlide(){ return {id:'idle',type:'clock',head:(C&&C.name)||'',dur:30}; }
function next(){
  clearTimeout(timer);
  const l=list(); if(!l.length){ if(curId!=='idle') show(idleSlide()); else timer=setTimeout(next,5000); return; }
  const all=C.slides, ci=all.findIndex(x=>x.id===curId); let pick=null;
  for(let k=1;k<=all.length;k++){ const s=all[(ci+k+all.length)%all.length]; if(active(s)){ pick=s; break; } }
  if(!pick) pick=l[0];
  if(pick.id===curId && cur){ // nur eine Folie aktiv: nicht neu aufbauen
    if(cur.video) { cur.video.currentTime=0; cur.video.play().catch(()=>{}); }
    timer=setTimeout(next,Math.max(5,pick.dur||10)*1000); return; }
  show(pick);
}
function overlays(){
  const s=(C&&C.set)||{}; const lg=$('#logo'), ck=$('#clk'), tk=$('#tick');
  if(s.logo){ lg.src=media(s.logo); lg.style.display='block'; } else lg.style.display='none';
  ck.style.display=s.clock?'block':'none'; if(s.clock) ck.textContent=hm(nowD());
  const on=s.tickerOn&&s.ticker; tk.style.display=on?'block':'none';
  { const ac=getComputedStyle(root).getPropertyValue('--ac').trim(); const m=/^#([0-9a-f]{6})$/i.exec(ac); if(m){ const v=parseInt(m[1],16), L=(.299*(v>>16)+.587*((v>>8)&255)+.114*(v&255))/255; tk.style.color=L>.62?'#161616':'#fff'; } }
  if(on){ const sp=tk.firstChild; if(sp.textContent!==s.ticker){ sp.textContent=s.ticker; if(sp._a) sp._a.cancel(); requestAnimationFrame(()=>{ const tw=sp.offsetWidth, u=W/100; sp._a=sp.animate([{transform:'translateX('+W+'px)'},{transform:'translateX(-'+tw+'px)'}],{duration:(W+tw)/(u*9)*1000,iterations:Infinity}); }); } }
  layout();
}
function setContent(c,focus){
  const first=!C; C=c; if(c.now) offset=c.now-Date.now(); lastVer=c.ver;
  root.dataset.tr=(c.set&&c.set.trans)||'fade'; themeName=''; applyTheme(); overlays();
  try{ if(!PREV) localStorage.setItem('mxi_c',JSON.stringify(c)); }catch(e){}
  if(window.Android&&Android.setPin&&c.set) try{ Android.setPin(c.set.pin||'1234'); }catch(e){}
  hold=focus||'';
  if(hold){ const s=(c.slides||[]).find(x=>x.id===hold)||(c.slides||[])[0]; if(s){ curId=''; show(s); } return; }
  if(first||!cur) next(); else if(curId==='idle'||!((c.slides||[]).some(x=>x.id===curId))) next();
}

/* ---------- Kopplung & Aktualisierung ---------- */
async function pairing(){
  $('#pair').style.display='flex'; $('#purl').textContent=BASE.replace(/^https?:\/\//,'');
  let code='';
  for(;;){
    try{
      if(!code){ const r=await api('a=reg&dev='+dev); dev=r.dev; localStorage.setItem('mxi_dev',dev); if(r.paired) break; code=r.code; $('#pcode').textContent=code.slice(0,3)+' '+code.slice(3); }
      const p=await api('a=poll&dev='+dev); if(p.paired) break;
      const r2=await api('a=reg&dev='+dev); if(r2.code!==code){ code=r2.code; $('#pcode').textContent=code.slice(0,3)+' '+code.slice(3); }
    }catch(e){}
    await sleep(3000);
  }
  $('#pair').style.display='none'; return true;
}
async function fetchContent(full){
  const c=await api('a=content&dev='+dev+'&v='+(full?0:lastVer)+(full?'&full=1':''));
  if(c.unpaired){ localStorage.removeItem('mxi_c'); C=null; lastVer=0; if(cur){cur.stop();cur.el.remove();cur=null;curId='';} await pairing(); return fetchContent(true); }
  if(c.same){ if(c.now) offset=c.now-Date.now(); return; }
  setContent(c);
}
async function main(){
  layout(); FX.set('');
  if(PREV){
    root.style.setProperty('--ff',FONTS.sans);
    const c=await api('a=content&prev='+encodeURIComponent(PREV)); if(c.err){ document.body.textContent='Vorschau nicht verfügbar'; return; }
    setContent(c,FOCUS0);
    addEventListener('message',e=>{ if(e.origin!==location.origin||!e.data||e.data.t!=='mxp') return; setContent(e.data.content,e.data.focus||''); });
    setInterval(()=>{ if(C&&C.set&&C.set.clock) $('#clk').textContent=hm(nowD()); },10000); return;
  }
  try{ const cc=JSON.parse(localStorage.getItem('mxi_c')||'null'); if(cc&&dev) setContent(cc); }catch(e){}
  if(!dev){ await pairing(); }
  for(;;){
    try{ await fetchContent(!C); }catch(e){ /* offline: weiter mit Zwischenspeicher */ }
    await sleep(30000);
  }
}
setInterval(()=>{ applyTheme(); if(C&&C.set&&C.set.clock) $('#clk').textContent=hm(nowD()); },20000);
// Folien mit Zeitplan: alle 30 s prüfen, ob die laufende Folie noch gelten darf
setInterval(()=>{ if(!hold&&C&&cur&&curId&&curId!=='idle'){ const s=C.slides.find(x=>x.id===curId); if(s&&!active(s)) next(); } },30000);
main();
})();
</script>
</body></html>

MXI_PLAYER_END;
const MXI_QR = <<<'MXI_QR_END'
//---------------------------------------------------------------------
//
// QR Code Generator for JavaScript
//
// Copyright (c) 2009 Kazuhiko Arase
//
// URL: http://www.d-project.com/
//
// Licensed under the MIT license:
//  http://www.opensource.org/licenses/mit-license.php
//
// The word 'QR Code' is registered trademark of
// DENSO WAVE INCORPORATED
//  http://www.denso-wave.com/qrcode/faqpatent-e.html
//
//---------------------------------------------------------------------

var qrcode = function() {

  //---------------------------------------------------------------------
  // qrcode
  //---------------------------------------------------------------------

  /**
   * qrcode
   * @param typeNumber 1 to 40
   * @param errorCorrectionLevel 'L','M','Q','H'
   */
  var qrcode = function(typeNumber, errorCorrectionLevel) {

    var PAD0 = 0xEC;
    var PAD1 = 0x11;

    var _typeNumber = typeNumber;
    var _errorCorrectionLevel = QRErrorCorrectionLevel[errorCorrectionLevel];
    var _modules = null;
    var _moduleCount = 0;
    var _dataCache = null;
    var _dataList = [];

    var _this = {};

    var makeImpl = function(test, maskPattern) {

      _moduleCount = _typeNumber * 4 + 17;
      _modules = function(moduleCount) {
        var modules = new Array(moduleCount);
        for (var row = 0; row < moduleCount; row += 1) {
          modules[row] = new Array(moduleCount);
          for (var col = 0; col < moduleCount; col += 1) {
            modules[row][col] = null;
          }
        }
        return modules;
      }(_moduleCount);

      setupPositionProbePattern(0, 0);
      setupPositionProbePattern(_moduleCount - 7, 0);
      setupPositionProbePattern(0, _moduleCount - 7);
      setupPositionAdjustPattern();
      setupTimingPattern();
      setupTypeInfo(test, maskPattern);

      if (_typeNumber >= 7) {
        setupTypeNumber(test);
      }

      if (_dataCache == null) {
        _dataCache = createData(_typeNumber, _errorCorrectionLevel, _dataList);
      }

      mapData(_dataCache, maskPattern);
    };

    var setupPositionProbePattern = function(row, col) {

      for (var r = -1; r <= 7; r += 1) {

        if (row + r <= -1 || _moduleCount <= row + r) continue;

        for (var c = -1; c <= 7; c += 1) {

          if (col + c <= -1 || _moduleCount <= col + c) continue;

          if ( (0 <= r && r <= 6 && (c == 0 || c == 6) )
              || (0 <= c && c <= 6 && (r == 0 || r == 6) )
              || (2 <= r && r <= 4 && 2 <= c && c <= 4) ) {
            _modules[row + r][col + c] = true;
          } else {
            _modules[row + r][col + c] = false;
          }
        }
      }
    };

    var getBestMaskPattern = function() {

      var minLostPoint = 0;
      var pattern = 0;

      for (var i = 0; i < 8; i += 1) {

        makeImpl(true, i);

        var lostPoint = QRUtil.getLostPoint(_this);

        if (i == 0 || minLostPoint > lostPoint) {
          minLostPoint = lostPoint;
          pattern = i;
        }
      }

      return pattern;
    };

    var setupTimingPattern = function() {

      for (var r = 8; r < _moduleCount - 8; r += 1) {
        if (_modules[r][6] != null) {
          continue;
        }
        _modules[r][6] = (r % 2 == 0);
      }

      for (var c = 8; c < _moduleCount - 8; c += 1) {
        if (_modules[6][c] != null) {
          continue;
        }
        _modules[6][c] = (c % 2 == 0);
      }
    };

    var setupPositionAdjustPattern = function() {

      var pos = QRUtil.getPatternPosition(_typeNumber);

      for (var i = 0; i < pos.length; i += 1) {

        for (var j = 0; j < pos.length; j += 1) {

          var row = pos[i];
          var col = pos[j];

          if (_modules[row][col] != null) {
            continue;
          }

          for (var r = -2; r <= 2; r += 1) {

            for (var c = -2; c <= 2; c += 1) {

              if (r == -2 || r == 2 || c == -2 || c == 2
                  || (r == 0 && c == 0) ) {
                _modules[row + r][col + c] = true;
              } else {
                _modules[row + r][col + c] = false;
              }
            }
          }
        }
      }
    };

    var setupTypeNumber = function(test) {

      var bits = QRUtil.getBCHTypeNumber(_typeNumber);

      for (var i = 0; i < 18; i += 1) {
        var mod = (!test && ( (bits >> i) & 1) == 1);
        _modules[Math.floor(i / 3)][i % 3 + _moduleCount - 8 - 3] = mod;
      }

      for (var i = 0; i < 18; i += 1) {
        var mod = (!test && ( (bits >> i) & 1) == 1);
        _modules[i % 3 + _moduleCount - 8 - 3][Math.floor(i / 3)] = mod;
      }
    };

    var setupTypeInfo = function(test, maskPattern) {

      var data = (_errorCorrectionLevel << 3) | maskPattern;
      var bits = QRUtil.getBCHTypeInfo(data);

      // vertical
      for (var i = 0; i < 15; i += 1) {

        var mod = (!test && ( (bits >> i) & 1) == 1);

        if (i < 6) {
          _modules[i][8] = mod;
        } else if (i < 8) {
          _modules[i + 1][8] = mod;
        } else {
          _modules[_moduleCount - 15 + i][8] = mod;
        }
      }

      // horizontal
      for (var i = 0; i < 15; i += 1) {

        var mod = (!test && ( (bits >> i) & 1) == 1);

        if (i < 8) {
          _modules[8][_moduleCount - i - 1] = mod;
        } else if (i < 9) {
          _modules[8][15 - i - 1 + 1] = mod;
        } else {
          _modules[8][15 - i - 1] = mod;
        }
      }

      // fixed module
      _modules[_moduleCount - 8][8] = (!test);
    };

    var mapData = function(data, maskPattern) {

      var inc = -1;
      var row = _moduleCount - 1;
      var bitIndex = 7;
      var byteIndex = 0;
      var maskFunc = QRUtil.getMaskFunction(maskPattern);

      for (var col = _moduleCount - 1; col > 0; col -= 2) {

        if (col == 6) col -= 1;

        while (true) {

          for (var c = 0; c < 2; c += 1) {

            if (_modules[row][col - c] == null) {

              var dark = false;

              if (byteIndex < data.length) {
                dark = ( ( (data[byteIndex] >>> bitIndex) & 1) == 1);
              }

              var mask = maskFunc(row, col - c);

              if (mask) {
                dark = !dark;
              }

              _modules[row][col - c] = dark;
              bitIndex -= 1;

              if (bitIndex == -1) {
                byteIndex += 1;
                bitIndex = 7;
              }
            }
          }

          row += inc;

          if (row < 0 || _moduleCount <= row) {
            row -= inc;
            inc = -inc;
            break;
          }
        }
      }
    };

    var createBytes = function(buffer, rsBlocks) {

      var offset = 0;

      var maxDcCount = 0;
      var maxEcCount = 0;

      var dcdata = new Array(rsBlocks.length);
      var ecdata = new Array(rsBlocks.length);

      for (var r = 0; r < rsBlocks.length; r += 1) {

        var dcCount = rsBlocks[r].dataCount;
        var ecCount = rsBlocks[r].totalCount - dcCount;

        maxDcCount = Math.max(maxDcCount, dcCount);
        maxEcCount = Math.max(maxEcCount, ecCount);

        dcdata[r] = new Array(dcCount);

        for (var i = 0; i < dcdata[r].length; i += 1) {
          dcdata[r][i] = 0xff & buffer.getBuffer()[i + offset];
        }
        offset += dcCount;

        var rsPoly = QRUtil.getErrorCorrectPolynomial(ecCount);
        var rawPoly = qrPolynomial(dcdata[r], rsPoly.getLength() - 1);

        var modPoly = rawPoly.mod(rsPoly);
        ecdata[r] = new Array(rsPoly.getLength() - 1);
        for (var i = 0; i < ecdata[r].length; i += 1) {
          var modIndex = i + modPoly.getLength() - ecdata[r].length;
          ecdata[r][i] = (modIndex >= 0)? modPoly.getAt(modIndex) : 0;
        }
      }

      var totalCodeCount = 0;
      for (var i = 0; i < rsBlocks.length; i += 1) {
        totalCodeCount += rsBlocks[i].totalCount;
      }

      var data = new Array(totalCodeCount);
      var index = 0;

      for (var i = 0; i < maxDcCount; i += 1) {
        for (var r = 0; r < rsBlocks.length; r += 1) {
          if (i < dcdata[r].length) {
            data[index] = dcdata[r][i];
            index += 1;
          }
        }
      }

      for (var i = 0; i < maxEcCount; i += 1) {
        for (var r = 0; r < rsBlocks.length; r += 1) {
          if (i < ecdata[r].length) {
            data[index] = ecdata[r][i];
            index += 1;
          }
        }
      }

      return data;
    };

    var createData = function(typeNumber, errorCorrectionLevel, dataList) {

      var rsBlocks = QRRSBlock.getRSBlocks(typeNumber, errorCorrectionLevel);

      var buffer = qrBitBuffer();

      for (var i = 0; i < dataList.length; i += 1) {
        var data = dataList[i];
        buffer.put(data.getMode(), 4);
        buffer.put(data.getLength(), QRUtil.getLengthInBits(data.getMode(), typeNumber) );
        data.write(buffer);
      }

      // calc num max data.
      var totalDataCount = 0;
      for (var i = 0; i < rsBlocks.length; i += 1) {
        totalDataCount += rsBlocks[i].dataCount;
      }

      if (buffer.getLengthInBits() > totalDataCount * 8) {
        throw 'code length overflow. ('
          + buffer.getLengthInBits()
          + '>'
          + totalDataCount * 8
          + ')';
      }

      // end code
      if (buffer.getLengthInBits() + 4 <= totalDataCount * 8) {
        buffer.put(0, 4);
      }

      // padding
      while (buffer.getLengthInBits() % 8 != 0) {
        buffer.putBit(false);
      }

      // padding
      while (true) {

        if (buffer.getLengthInBits() >= totalDataCount * 8) {
          break;
        }
        buffer.put(PAD0, 8);

        if (buffer.getLengthInBits() >= totalDataCount * 8) {
          break;
        }
        buffer.put(PAD1, 8);
      }

      return createBytes(buffer, rsBlocks);
    };

    _this.addData = function(data, mode) {

      mode = mode || 'Byte';

      var newData = null;

      switch(mode) {
      case 'Numeric' :
        newData = qrNumber(data);
        break;
      case 'Alphanumeric' :
        newData = qrAlphaNum(data);
        break;
      case 'Byte' :
        newData = qr8BitByte(data);
        break;
      case 'Kanji' :
        newData = qrKanji(data);
        break;
      default :
        throw 'mode:' + mode;
      }

      _dataList.push(newData);
      _dataCache = null;
    };

    _this.isDark = function(row, col) {
      if (row < 0 || _moduleCount <= row || col < 0 || _moduleCount <= col) {
        throw row + ',' + col;
      }
      return _modules[row][col];
    };

    _this.getModuleCount = function() {
      return _moduleCount;
    };

    _this.make = function() {
      if (_typeNumber < 1) {
        var typeNumber = 1;

        for (; typeNumber < 40; typeNumber++) {
          var rsBlocks = QRRSBlock.getRSBlocks(typeNumber, _errorCorrectionLevel);
          var buffer = qrBitBuffer();

          for (var i = 0; i < _dataList.length; i++) {
            var data = _dataList[i];
            buffer.put(data.getMode(), 4);
            buffer.put(data.getLength(), QRUtil.getLengthInBits(data.getMode(), typeNumber) );
            data.write(buffer);
          }

          var totalDataCount = 0;
          for (var i = 0; i < rsBlocks.length; i++) {
            totalDataCount += rsBlocks[i].dataCount;
          }

          if (buffer.getLengthInBits() <= totalDataCount * 8) {
            break;
          }
        }

        _typeNumber = typeNumber;
      }

      makeImpl(false, getBestMaskPattern() );
    };

    _this.createTableTag = function(cellSize, margin) {

      cellSize = cellSize || 2;
      margin = (typeof margin == 'undefined')? cellSize * 4 : margin;

      var qrHtml = '';

      qrHtml += '<table style="';
      qrHtml += ' border-width: 0px; border-style: none;';
      qrHtml += ' border-collapse: collapse;';
      qrHtml += ' padding: 0px; margin: ' + margin + 'px;';
      qrHtml += '">';
      qrHtml += '<tbody>';

      for (var r = 0; r < _this.getModuleCount(); r += 1) {

        qrHtml += '<tr>';

        for (var c = 0; c < _this.getModuleCount(); c += 1) {
          qrHtml += '<td style="';
          qrHtml += ' border-width: 0px; border-style: none;';
          qrHtml += ' border-collapse: collapse;';
          qrHtml += ' padding: 0px; margin: 0px;';
          qrHtml += ' width: ' + cellSize + 'px;';
          qrHtml += ' height: ' + cellSize + 'px;';
          qrHtml += ' background-color: ';
          qrHtml += _this.isDark(r, c)? '#000000' : '#ffffff';
          qrHtml += ';';
          qrHtml += '"/>';
        }

        qrHtml += '</tr>';
      }

      qrHtml += '</tbody>';
      qrHtml += '</table>';

      return qrHtml;
    };

    _this.createSvgTag = function(cellSize, margin, alt, title) {

      var opts = {};
      if (typeof arguments[0] == 'object') {
        // Called by options.
        opts = arguments[0];
        // overwrite cellSize and margin.
        cellSize = opts.cellSize;
        margin = opts.margin;
        alt = opts.alt;
        title = opts.title;
      }

      cellSize = cellSize || 2;
      margin = (typeof margin == 'undefined')? cellSize * 4 : margin;

      // Compose alt property surrogate
      alt = (typeof alt === 'string') ? {text: alt} : alt || {};
      alt.text = alt.text || null;
      alt.id = (alt.text) ? alt.id || 'qrcode-description' : null;

      // Compose title property surrogate
      title = (typeof title === 'string') ? {text: title} : title || {};
      title.text = title.text || null;
      title.id = (title.text) ? title.id || 'qrcode-title' : null;

      var size = _this.getModuleCount() * cellSize + margin * 2;
      var c, mc, r, mr, qrSvg='', rect;

      rect = 'l' + cellSize + ',0 0,' + cellSize +
        ' -' + cellSize + ',0 0,-' + cellSize + 'z ';

      qrSvg += '<svg version="1.1" xmlns="http://www.w3.org/2000/svg"';
      qrSvg += !opts.scalable ? ' width="' + size + 'px" height="' + size + 'px"' : '';
      qrSvg += ' viewBox="0 0 ' + size + ' ' + size + '" ';
      qrSvg += ' preserveAspectRatio="xMinYMin meet"';
      qrSvg += (title.text || alt.text) ? ' role="img" aria-labelledby="' +
          escapeXml([title.id, alt.id].join(' ').trim() ) + '"' : '';
      qrSvg += '>';
      qrSvg += (title.text) ? '<title id="' + escapeXml(title.id) + '">' +
          escapeXml(title.text) + '</title>' : '';
      qrSvg += (alt.text) ? '<description id="' + escapeXml(alt.id) + '">' +
          escapeXml(alt.text) + '</description>' : '';
      qrSvg += '<rect width="100%" height="100%" fill="white" cx="0" cy="0"/>';
      qrSvg += '<path d="';

      for (r = 0; r < _this.getModuleCount(); r += 1) {
        mr = r * cellSize + margin;
        for (c = 0; c < _this.getModuleCount(); c += 1) {
          if (_this.isDark(r, c) ) {
            mc = c*cellSize+margin;
            qrSvg += 'M' + mc + ',' + mr + rect;
          }
        }
      }

      qrSvg += '" stroke="transparent" fill="black"/>';
      qrSvg += '</svg>';

      return qrSvg;
    };

    _this.createDataURL = function(cellSize, margin) {

      cellSize = cellSize || 2;
      margin = (typeof margin == 'undefined')? cellSize * 4 : margin;

      var size = _this.getModuleCount() * cellSize + margin * 2;
      var min = margin;
      var max = size - margin;

      return createDataURL(size, size, function(x, y) {
        if (min <= x && x < max && min <= y && y < max) {
          var c = Math.floor( (x - min) / cellSize);
          var r = Math.floor( (y - min) / cellSize);
          return _this.isDark(r, c)? 0 : 1;
        } else {
          return 1;
        }
      } );
    };

    _this.createImgTag = function(cellSize, margin, alt) {

      cellSize = cellSize || 2;
      margin = (typeof margin == 'undefined')? cellSize * 4 : margin;

      var size = _this.getModuleCount() * cellSize + margin * 2;

      var img = '';
      img += '<img';
      img += '\u0020src="';
      img += _this.createDataURL(cellSize, margin);
      img += '"';
      img += '\u0020width="';
      img += size;
      img += '"';
      img += '\u0020height="';
      img += size;
      img += '"';
      if (alt) {
        img += '\u0020alt="';
        img += escapeXml(alt);
        img += '"';
      }
      img += '/>';

      return img;
    };

    var escapeXml = function(s) {
      var escaped = '';
      for (var i = 0; i < s.length; i += 1) {
        var c = s.charAt(i);
        switch(c) {
        case '<': escaped += '&lt;'; break;
        case '>': escaped += '&gt;'; break;
        case '&': escaped += '&amp;'; break;
        case '"': escaped += '&quot;'; break;
        default : escaped += c; break;
        }
      }
      return escaped;
    };

    var _createHalfASCII = function(margin) {
      var cellSize = 1;
      margin = (typeof margin == 'undefined')? cellSize * 2 : margin;

      var size = _this.getModuleCount() * cellSize + margin * 2;
      var min = margin;
      var max = size - margin;

      var y, x, r1, r2, p;

      var blocks = {
        '██': '█',
        '█ ': '▀',
        ' █': '▄',
        '  ': ' '
      };

      var blocksLastLineNoMargin = {
        '██': '▀',
        '█ ': '▀',
        ' █': ' ',
        '  ': ' '
      };

      var ascii = '';
      for (y = 0; y < size; y += 2) {
        r1 = Math.floor((y - min) / cellSize);
        r2 = Math.floor((y + 1 - min) / cellSize);
        for (x = 0; x < size; x += 1) {
          p = '█';

          if (min <= x && x < max && min <= y && y < max && _this.isDark(r1, Math.floor((x - min) / cellSize))) {
            p = ' ';
          }

          if (min <= x && x < max && min <= y+1 && y+1 < max && _this.isDark(r2, Math.floor((x - min) / cellSize))) {
            p += ' ';
          }
          else {
            p += '█';
          }

          // Output 2 characters per pixel, to create full square. 1 character per pixels gives only half width of square.
          ascii += (margin < 1 && y+1 >= max) ? blocksLastLineNoMargin[p] : blocks[p];
        }

        ascii += '\n';
      }

      if (size % 2 && margin > 0) {
        return ascii.substring(0, ascii.length - size - 1) + Array(size+1).join('▀');
      }

      return ascii.substring(0, ascii.length-1);
    };

    _this.createASCII = function(cellSize, margin) {
      cellSize = cellSize || 1;

      if (cellSize < 2) {
        return _createHalfASCII(margin);
      }

      cellSize -= 1;
      margin = (typeof margin == 'undefined')? cellSize * 2 : margin;

      var size = _this.getModuleCount() * cellSize + margin * 2;
      var min = margin;
      var max = size - margin;

      var y, x, r, p;

      var white = Array(cellSize+1).join('██');
      var black = Array(cellSize+1).join('  ');

      var ascii = '';
      var line = '';
      for (y = 0; y < size; y += 1) {
        r = Math.floor( (y - min) / cellSize);
        line = '';
        for (x = 0; x < size; x += 1) {
          p = 1;

          if (min <= x && x < max && min <= y && y < max && _this.isDark(r, Math.floor((x - min) / cellSize))) {
            p = 0;
          }

          // Output 2 characters per pixel, to create full square. 1 character per pixels gives only half width of square.
          line += p ? white : black;
        }

        for (r = 0; r < cellSize; r += 1) {
          ascii += line + '\n';
        }
      }

      return ascii.substring(0, ascii.length-1);
    };

    _this.renderTo2dContext = function(context, cellSize) {
      cellSize = cellSize || 2;
      var length = _this.getModuleCount();
      for (var row = 0; row < length; row++) {
        for (var col = 0; col < length; col++) {
          context.fillStyle = _this.isDark(row, col) ? 'black' : 'white';
          context.fillRect(col * cellSize, row * cellSize, cellSize, cellSize);
        }
      }
    }

    return _this;
  };

  //---------------------------------------------------------------------
  // qrcode.stringToBytes
  //---------------------------------------------------------------------

  qrcode.stringToBytesFuncs = {
    'default' : function(s) {
      var bytes = [];
      for (var i = 0; i < s.length; i += 1) {
        var c = s.charCodeAt(i);
        bytes.push(c & 0xff);
      }
      return bytes;
    }
  };

  qrcode.stringToBytes = qrcode.stringToBytesFuncs['default'];

  //---------------------------------------------------------------------
  // qrcode.createStringToBytes
  //---------------------------------------------------------------------

  /**
   * @param unicodeData base64 string of byte array.
   * [16bit Unicode],[16bit Bytes], ...
   * @param numChars
   */
  qrcode.createStringToBytes = function(unicodeData, numChars) {

    // create conversion map.

    var unicodeMap = function() {

      var bin = base64DecodeInputStream(unicodeData);
      var read = function() {
        var b = bin.read();
        if (b == -1) throw 'eof';
        return b;
      };

      var count = 0;
      var unicodeMap = {};
      while (true) {
        var b0 = bin.read();
        if (b0 == -1) break;
        var b1 = read();
        var b2 = read();
        var b3 = read();
        var k = String.fromCharCode( (b0 << 8) | b1);
        var v = (b2 << 8) | b3;
        unicodeMap[k] = v;
        count += 1;
      }
      if (count != numChars) {
        throw count + ' != ' + numChars;
      }

      return unicodeMap;
    }();

    var unknownChar = '?'.charCodeAt(0);

    return function(s) {
      var bytes = [];
      for (var i = 0; i < s.length; i += 1) {
        var c = s.charCodeAt(i);
        if (c < 128) {
          bytes.push(c);
        } else {
          var b = unicodeMap[s.charAt(i)];
          if (typeof b == 'number') {
            if ( (b & 0xff) == b) {
              // 1byte
              bytes.push(b);
            } else {
              // 2bytes
              bytes.push(b >>> 8);
              bytes.push(b & 0xff);
            }
          } else {
            bytes.push(unknownChar);
          }
        }
      }
      return bytes;
    };
  };

  //---------------------------------------------------------------------
  // QRMode
  //---------------------------------------------------------------------

  var QRMode = {
    MODE_NUMBER :    1 << 0,
    MODE_ALPHA_NUM : 1 << 1,
    MODE_8BIT_BYTE : 1 << 2,
    MODE_KANJI :     1 << 3
  };

  //---------------------------------------------------------------------
  // QRErrorCorrectionLevel
  //---------------------------------------------------------------------

  var QRErrorCorrectionLevel = {
    L : 1,
    M : 0,
    Q : 3,
    H : 2
  };

  //---------------------------------------------------------------------
  // QRMaskPattern
  //---------------------------------------------------------------------

  var QRMaskPattern = {
    PATTERN000 : 0,
    PATTERN001 : 1,
    PATTERN010 : 2,
    PATTERN011 : 3,
    PATTERN100 : 4,
    PATTERN101 : 5,
    PATTERN110 : 6,
    PATTERN111 : 7
  };

  //---------------------------------------------------------------------
  // QRUtil
  //---------------------------------------------------------------------

  var QRUtil = function() {

    var PATTERN_POSITION_TABLE = [
      [],
      [6, 18],
      [6, 22],
      [6, 26],
      [6, 30],
      [6, 34],
      [6, 22, 38],
      [6, 24, 42],
      [6, 26, 46],
      [6, 28, 50],
      [6, 30, 54],
      [6, 32, 58],
      [6, 34, 62],
      [6, 26, 46, 66],
      [6, 26, 48, 70],
      [6, 26, 50, 74],
      [6, 30, 54, 78],
      [6, 30, 56, 82],
      [6, 30, 58, 86],
      [6, 34, 62, 90],
      [6, 28, 50, 72, 94],
      [6, 26, 50, 74, 98],
      [6, 30, 54, 78, 102],
      [6, 28, 54, 80, 106],
      [6, 32, 58, 84, 110],
      [6, 30, 58, 86, 114],
      [6, 34, 62, 90, 118],
      [6, 26, 50, 74, 98, 122],
      [6, 30, 54, 78, 102, 126],
      [6, 26, 52, 78, 104, 130],
      [6, 30, 56, 82, 108, 134],
      [6, 34, 60, 86, 112, 138],
      [6, 30, 58, 86, 114, 142],
      [6, 34, 62, 90, 118, 146],
      [6, 30, 54, 78, 102, 126, 150],
      [6, 24, 50, 76, 102, 128, 154],
      [6, 28, 54, 80, 106, 132, 158],
      [6, 32, 58, 84, 110, 136, 162],
      [6, 26, 54, 82, 110, 138, 166],
      [6, 30, 58, 86, 114, 142, 170]
    ];
    var G15 = (1 << 10) | (1 << 8) | (1 << 5) | (1 << 4) | (1 << 2) | (1 << 1) | (1 << 0);
    var G18 = (1 << 12) | (1 << 11) | (1 << 10) | (1 << 9) | (1 << 8) | (1 << 5) | (1 << 2) | (1 << 0);
    var G15_MASK = (1 << 14) | (1 << 12) | (1 << 10) | (1 << 4) | (1 << 1);

    var _this = {};

    var getBCHDigit = function(data) {
      var digit = 0;
      while (data != 0) {
        digit += 1;
        data >>>= 1;
      }
      return digit;
    };

    _this.getBCHTypeInfo = function(data) {
      var d = data << 10;
      while (getBCHDigit(d) - getBCHDigit(G15) >= 0) {
        d ^= (G15 << (getBCHDigit(d) - getBCHDigit(G15) ) );
      }
      return ( (data << 10) | d) ^ G15_MASK;
    };

    _this.getBCHTypeNumber = function(data) {
      var d = data << 12;
      while (getBCHDigit(d) - getBCHDigit(G18) >= 0) {
        d ^= (G18 << (getBCHDigit(d) - getBCHDigit(G18) ) );
      }
      return (data << 12) | d;
    };

    _this.getPatternPosition = function(typeNumber) {
      return PATTERN_POSITION_TABLE[typeNumber - 1];
    };

    _this.getMaskFunction = function(maskPattern) {

      switch (maskPattern) {

      case QRMaskPattern.PATTERN000 :
        return function(i, j) { return (i + j) % 2 == 0; };
      case QRMaskPattern.PATTERN001 :
        return function(i, j) { return i % 2 == 0; };
      case QRMaskPattern.PATTERN010 :
        return function(i, j) { return j % 3 == 0; };
      case QRMaskPattern.PATTERN011 :
        return function(i, j) { return (i + j) % 3 == 0; };
      case QRMaskPattern.PATTERN100 :
        return function(i, j) { return (Math.floor(i / 2) + Math.floor(j / 3) ) % 2 == 0; };
      case QRMaskPattern.PATTERN101 :
        return function(i, j) { return (i * j) % 2 + (i * j) % 3 == 0; };
      case QRMaskPattern.PATTERN110 :
        return function(i, j) { return ( (i * j) % 2 + (i * j) % 3) % 2 == 0; };
      case QRMaskPattern.PATTERN111 :
        return function(i, j) { return ( (i * j) % 3 + (i + j) % 2) % 2 == 0; };

      default :
        throw 'bad maskPattern:' + maskPattern;
      }
    };

    _this.getErrorCorrectPolynomial = function(errorCorrectLength) {
      var a = qrPolynomial([1], 0);
      for (var i = 0; i < errorCorrectLength; i += 1) {
        a = a.multiply(qrPolynomial([1, QRMath.gexp(i)], 0) );
      }
      return a;
    };

    _this.getLengthInBits = function(mode, type) {

      if (1 <= type && type < 10) {

        // 1 - 9

        switch(mode) {
        case QRMode.MODE_NUMBER    : return 10;
        case QRMode.MODE_ALPHA_NUM : return 9;
        case QRMode.MODE_8BIT_BYTE : return 8;
        case QRMode.MODE_KANJI     : return 8;
        default :
          throw 'mode:' + mode;
        }

      } else if (type < 27) {

        // 10 - 26

        switch(mode) {
        case QRMode.MODE_NUMBER    : return 12;
        case QRMode.MODE_ALPHA_NUM : return 11;
        case QRMode.MODE_8BIT_BYTE : return 16;
        case QRMode.MODE_KANJI     : return 10;
        default :
          throw 'mode:' + mode;
        }

      } else if (type < 41) {

        // 27 - 40

        switch(mode) {
        case QRMode.MODE_NUMBER    : return 14;
        case QRMode.MODE_ALPHA_NUM : return 13;
        case QRMode.MODE_8BIT_BYTE : return 16;
        case QRMode.MODE_KANJI     : return 12;
        default :
          throw 'mode:' + mode;
        }

      } else {
        throw 'type:' + type;
      }
    };

    _this.getLostPoint = function(qrcode) {

      var moduleCount = qrcode.getModuleCount();

      var lostPoint = 0;

      // LEVEL1

      for (var row = 0; row < moduleCount; row += 1) {
        for (var col = 0; col < moduleCount; col += 1) {

          var sameCount = 0;
          var dark = qrcode.isDark(row, col);

          for (var r = -1; r <= 1; r += 1) {

            if (row + r < 0 || moduleCount <= row + r) {
              continue;
            }

            for (var c = -1; c <= 1; c += 1) {

              if (col + c < 0 || moduleCount <= col + c) {
                continue;
              }

              if (r == 0 && c == 0) {
                continue;
              }

              if (dark == qrcode.isDark(row + r, col + c) ) {
                sameCount += 1;
              }
            }
          }

          if (sameCount > 5) {
            lostPoint += (3 + sameCount - 5);
          }
        }
      };

      // LEVEL2

      for (var row = 0; row < moduleCount - 1; row += 1) {
        for (var col = 0; col < moduleCount - 1; col += 1) {
          var count = 0;
          if (qrcode.isDark(row, col) ) count += 1;
          if (qrcode.isDark(row + 1, col) ) count += 1;
          if (qrcode.isDark(row, col + 1) ) count += 1;
          if (qrcode.isDark(row + 1, col + 1) ) count += 1;
          if (count == 0 || count == 4) {
            lostPoint += 3;
          }
        }
      }

      // LEVEL3

      for (var row = 0; row < moduleCount; row += 1) {
        for (var col = 0; col < moduleCount - 6; col += 1) {
          if (qrcode.isDark(row, col)
              && !qrcode.isDark(row, col + 1)
              &&  qrcode.isDark(row, col + 2)
              &&  qrcode.isDark(row, col + 3)
              &&  qrcode.isDark(row, col + 4)
              && !qrcode.isDark(row, col + 5)
              &&  qrcode.isDark(row, col + 6) ) {
            lostPoint += 40;
          }
        }
      }

      for (var col = 0; col < moduleCount; col += 1) {
        for (var row = 0; row < moduleCount - 6; row += 1) {
          if (qrcode.isDark(row, col)
              && !qrcode.isDark(row + 1, col)
              &&  qrcode.isDark(row + 2, col)
              &&  qrcode.isDark(row + 3, col)
              &&  qrcode.isDark(row + 4, col)
              && !qrcode.isDark(row + 5, col)
              &&  qrcode.isDark(row + 6, col) ) {
            lostPoint += 40;
          }
        }
      }

      // LEVEL4

      var darkCount = 0;

      for (var col = 0; col < moduleCount; col += 1) {
        for (var row = 0; row < moduleCount; row += 1) {
          if (qrcode.isDark(row, col) ) {
            darkCount += 1;
          }
        }
      }

      var ratio = Math.abs(100 * darkCount / moduleCount / moduleCount - 50) / 5;
      lostPoint += ratio * 10;

      return lostPoint;
    };

    return _this;
  }();

  //---------------------------------------------------------------------
  // QRMath
  //---------------------------------------------------------------------

  var QRMath = function() {

    var EXP_TABLE = new Array(256);
    var LOG_TABLE = new Array(256);

    // initialize tables
    for (var i = 0; i < 8; i += 1) {
      EXP_TABLE[i] = 1 << i;
    }
    for (var i = 8; i < 256; i += 1) {
      EXP_TABLE[i] = EXP_TABLE[i - 4]
        ^ EXP_TABLE[i - 5]
        ^ EXP_TABLE[i - 6]
        ^ EXP_TABLE[i - 8];
    }
    for (var i = 0; i < 255; i += 1) {
      LOG_TABLE[EXP_TABLE[i] ] = i;
    }

    var _this = {};

    _this.glog = function(n) {

      if (n < 1) {
        throw 'glog(' + n + ')';
      }

      return LOG_TABLE[n];
    };

    _this.gexp = function(n) {

      while (n < 0) {
        n += 255;
      }

      while (n >= 256) {
        n -= 255;
      }

      return EXP_TABLE[n];
    };

    return _this;
  }();

  //---------------------------------------------------------------------
  // qrPolynomial
  //---------------------------------------------------------------------

  function qrPolynomial(num, shift) {

    if (typeof num.length == 'undefined') {
      throw num.length + '/' + shift;
    }

    var _num = function() {
      var offset = 0;
      while (offset < num.length && num[offset] == 0) {
        offset += 1;
      }
      var _num = new Array(num.length - offset + shift);
      for (var i = 0; i < num.length - offset; i += 1) {
        _num[i] = num[i + offset];
      }
      return _num;
    }();

    var _this = {};

    _this.getAt = function(index) {
      return _num[index];
    };

    _this.getLength = function() {
      return _num.length;
    };

    _this.multiply = function(e) {

      var num = new Array(_this.getLength() + e.getLength() - 1);

      for (var i = 0; i < _this.getLength(); i += 1) {
        for (var j = 0; j < e.getLength(); j += 1) {
          num[i + j] ^= QRMath.gexp(QRMath.glog(_this.getAt(i) ) + QRMath.glog(e.getAt(j) ) );
        }
      }

      return qrPolynomial(num, 0);
    };

    _this.mod = function(e) {

      if (_this.getLength() - e.getLength() < 0) {
        return _this;
      }

      var ratio = QRMath.glog(_this.getAt(0) ) - QRMath.glog(e.getAt(0) );

      var num = new Array(_this.getLength() );
      for (var i = 0; i < _this.getLength(); i += 1) {
        num[i] = _this.getAt(i);
      }

      for (var i = 0; i < e.getLength(); i += 1) {
        num[i] ^= QRMath.gexp(QRMath.glog(e.getAt(i) ) + ratio);
      }

      // recursive call
      return qrPolynomial(num, 0).mod(e);
    };

    return _this;
  };

  //---------------------------------------------------------------------
  // QRRSBlock
  //---------------------------------------------------------------------

  var QRRSBlock = function() {

    var RS_BLOCK_TABLE = [

      // L
      // M
      // Q
      // H

      // 1
      [1, 26, 19],
      [1, 26, 16],
      [1, 26, 13],
      [1, 26, 9],

      // 2
      [1, 44, 34],
      [1, 44, 28],
      [1, 44, 22],
      [1, 44, 16],

      // 3
      [1, 70, 55],
      [1, 70, 44],
      [2, 35, 17],
      [2, 35, 13],

      // 4
      [1, 100, 80],
      [2, 50, 32],
      [2, 50, 24],
      [4, 25, 9],

      // 5
      [1, 134, 108],
      [2, 67, 43],
      [2, 33, 15, 2, 34, 16],
      [2, 33, 11, 2, 34, 12],

      // 6
      [2, 86, 68],
      [4, 43, 27],
      [4, 43, 19],
      [4, 43, 15],

      // 7
      [2, 98, 78],
      [4, 49, 31],
      [2, 32, 14, 4, 33, 15],
      [4, 39, 13, 1, 40, 14],

      // 8
      [2, 121, 97],
      [2, 60, 38, 2, 61, 39],
      [4, 40, 18, 2, 41, 19],
      [4, 40, 14, 2, 41, 15],

      // 9
      [2, 146, 116],
      [3, 58, 36, 2, 59, 37],
      [4, 36, 16, 4, 37, 17],
      [4, 36, 12, 4, 37, 13],

      // 10
      [2, 86, 68, 2, 87, 69],
      [4, 69, 43, 1, 70, 44],
      [6, 43, 19, 2, 44, 20],
      [6, 43, 15, 2, 44, 16],

      // 11
      [4, 101, 81],
      [1, 80, 50, 4, 81, 51],
      [4, 50, 22, 4, 51, 23],
      [3, 36, 12, 8, 37, 13],

      // 12
      [2, 116, 92, 2, 117, 93],
      [6, 58, 36, 2, 59, 37],
      [4, 46, 20, 6, 47, 21],
      [7, 42, 14, 4, 43, 15],

      // 13
      [4, 133, 107],
      [8, 59, 37, 1, 60, 38],
      [8, 44, 20, 4, 45, 21],
      [12, 33, 11, 4, 34, 12],

      // 14
      [3, 145, 115, 1, 146, 116],
      [4, 64, 40, 5, 65, 41],
      [11, 36, 16, 5, 37, 17],
      [11, 36, 12, 5, 37, 13],

      // 15
      [5, 109, 87, 1, 110, 88],
      [5, 65, 41, 5, 66, 42],
      [5, 54, 24, 7, 55, 25],
      [11, 36, 12, 7, 37, 13],

      // 16
      [5, 122, 98, 1, 123, 99],
      [7, 73, 45, 3, 74, 46],
      [15, 43, 19, 2, 44, 20],
      [3, 45, 15, 13, 46, 16],

      // 17
      [1, 135, 107, 5, 136, 108],
      [10, 74, 46, 1, 75, 47],
      [1, 50, 22, 15, 51, 23],
      [2, 42, 14, 17, 43, 15],

      // 18
      [5, 150, 120, 1, 151, 121],
      [9, 69, 43, 4, 70, 44],
      [17, 50, 22, 1, 51, 23],
      [2, 42, 14, 19, 43, 15],

      // 19
      [3, 141, 113, 4, 142, 114],
      [3, 70, 44, 11, 71, 45],
      [17, 47, 21, 4, 48, 22],
      [9, 39, 13, 16, 40, 14],

      // 20
      [3, 135, 107, 5, 136, 108],
      [3, 67, 41, 13, 68, 42],
      [15, 54, 24, 5, 55, 25],
      [15, 43, 15, 10, 44, 16],

      // 21
      [4, 144, 116, 4, 145, 117],
      [17, 68, 42],
      [17, 50, 22, 6, 51, 23],
      [19, 46, 16, 6, 47, 17],

      // 22
      [2, 139, 111, 7, 140, 112],
      [17, 74, 46],
      [7, 54, 24, 16, 55, 25],
      [34, 37, 13],

      // 23
      [4, 151, 121, 5, 152, 122],
      [4, 75, 47, 14, 76, 48],
      [11, 54, 24, 14, 55, 25],
      [16, 45, 15, 14, 46, 16],

      // 24
      [6, 147, 117, 4, 148, 118],
      [6, 73, 45, 14, 74, 46],
      [11, 54, 24, 16, 55, 25],
      [30, 46, 16, 2, 47, 17],

      // 25
      [8, 132, 106, 4, 133, 107],
      [8, 75, 47, 13, 76, 48],
      [7, 54, 24, 22, 55, 25],
      [22, 45, 15, 13, 46, 16],

      // 26
      [10, 142, 114, 2, 143, 115],
      [19, 74, 46, 4, 75, 47],
      [28, 50, 22, 6, 51, 23],
      [33, 46, 16, 4, 47, 17],

      // 27
      [8, 152, 122, 4, 153, 123],
      [22, 73, 45, 3, 74, 46],
      [8, 53, 23, 26, 54, 24],
      [12, 45, 15, 28, 46, 16],

      // 28
      [3, 147, 117, 10, 148, 118],
      [3, 73, 45, 23, 74, 46],
      [4, 54, 24, 31, 55, 25],
      [11, 45, 15, 31, 46, 16],

      // 29
      [7, 146, 116, 7, 147, 117],
      [21, 73, 45, 7, 74, 46],
      [1, 53, 23, 37, 54, 24],
      [19, 45, 15, 26, 46, 16],

      // 30
      [5, 145, 115, 10, 146, 116],
      [19, 75, 47, 10, 76, 48],
      [15, 54, 24, 25, 55, 25],
      [23, 45, 15, 25, 46, 16],

      // 31
      [13, 145, 115, 3, 146, 116],
      [2, 74, 46, 29, 75, 47],
      [42, 54, 24, 1, 55, 25],
      [23, 45, 15, 28, 46, 16],

      // 32
      [17, 145, 115],
      [10, 74, 46, 23, 75, 47],
      [10, 54, 24, 35, 55, 25],
      [19, 45, 15, 35, 46, 16],

      // 33
      [17, 145, 115, 1, 146, 116],
      [14, 74, 46, 21, 75, 47],
      [29, 54, 24, 19, 55, 25],
      [11, 45, 15, 46, 46, 16],

      // 34
      [13, 145, 115, 6, 146, 116],
      [14, 74, 46, 23, 75, 47],
      [44, 54, 24, 7, 55, 25],
      [59, 46, 16, 1, 47, 17],

      // 35
      [12, 151, 121, 7, 152, 122],
      [12, 75, 47, 26, 76, 48],
      [39, 54, 24, 14, 55, 25],
      [22, 45, 15, 41, 46, 16],

      // 36
      [6, 151, 121, 14, 152, 122],
      [6, 75, 47, 34, 76, 48],
      [46, 54, 24, 10, 55, 25],
      [2, 45, 15, 64, 46, 16],

      // 37
      [17, 152, 122, 4, 153, 123],
      [29, 74, 46, 14, 75, 47],
      [49, 54, 24, 10, 55, 25],
      [24, 45, 15, 46, 46, 16],

      // 38
      [4, 152, 122, 18, 153, 123],
      [13, 74, 46, 32, 75, 47],
      [48, 54, 24, 14, 55, 25],
      [42, 45, 15, 32, 46, 16],

      // 39
      [20, 147, 117, 4, 148, 118],
      [40, 75, 47, 7, 76, 48],
      [43, 54, 24, 22, 55, 25],
      [10, 45, 15, 67, 46, 16],

      // 40
      [19, 148, 118, 6, 149, 119],
      [18, 75, 47, 31, 76, 48],
      [34, 54, 24, 34, 55, 25],
      [20, 45, 15, 61, 46, 16]
    ];

    var qrRSBlock = function(totalCount, dataCount) {
      var _this = {};
      _this.totalCount = totalCount;
      _this.dataCount = dataCount;
      return _this;
    };

    var _this = {};

    var getRsBlockTable = function(typeNumber, errorCorrectionLevel) {

      switch(errorCorrectionLevel) {
      case QRErrorCorrectionLevel.L :
        return RS_BLOCK_TABLE[(typeNumber - 1) * 4 + 0];
      case QRErrorCorrectionLevel.M :
        return RS_BLOCK_TABLE[(typeNumber - 1) * 4 + 1];
      case QRErrorCorrectionLevel.Q :
        return RS_BLOCK_TABLE[(typeNumber - 1) * 4 + 2];
      case QRErrorCorrectionLevel.H :
        return RS_BLOCK_TABLE[(typeNumber - 1) * 4 + 3];
      default :
        return undefined;
      }
    };

    _this.getRSBlocks = function(typeNumber, errorCorrectionLevel) {

      var rsBlock = getRsBlockTable(typeNumber, errorCorrectionLevel);

      if (typeof rsBlock == 'undefined') {
        throw 'bad rs block @ typeNumber:' + typeNumber +
            '/errorCorrectionLevel:' + errorCorrectionLevel;
      }

      var length = rsBlock.length / 3;

      var list = [];

      for (var i = 0; i < length; i += 1) {

        var count = rsBlock[i * 3 + 0];
        var totalCount = rsBlock[i * 3 + 1];
        var dataCount = rsBlock[i * 3 + 2];

        for (var j = 0; j < count; j += 1) {
          list.push(qrRSBlock(totalCount, dataCount) );
        }
      }

      return list;
    };

    return _this;
  }();

  //---------------------------------------------------------------------
  // qrBitBuffer
  //---------------------------------------------------------------------

  var qrBitBuffer = function() {

    var _buffer = [];
    var _length = 0;

    var _this = {};

    _this.getBuffer = function() {
      return _buffer;
    };

    _this.getAt = function(index) {
      var bufIndex = Math.floor(index / 8);
      return ( (_buffer[bufIndex] >>> (7 - index % 8) ) & 1) == 1;
    };

    _this.put = function(num, length) {
      for (var i = 0; i < length; i += 1) {
        _this.putBit( ( (num >>> (length - i - 1) ) & 1) == 1);
      }
    };

    _this.getLengthInBits = function() {
      return _length;
    };

    _this.putBit = function(bit) {

      var bufIndex = Math.floor(_length / 8);
      if (_buffer.length <= bufIndex) {
        _buffer.push(0);
      }

      if (bit) {
        _buffer[bufIndex] |= (0x80 >>> (_length % 8) );
      }

      _length += 1;
    };

    return _this;
  };

  //---------------------------------------------------------------------
  // qrNumber
  //---------------------------------------------------------------------

  var qrNumber = function(data) {

    var _mode = QRMode.MODE_NUMBER;
    var _data = data;

    var _this = {};

    _this.getMode = function() {
      return _mode;
    };

    _this.getLength = function(buffer) {
      return _data.length;
    };

    _this.write = function(buffer) {

      var data = _data;

      var i = 0;

      while (i + 2 < data.length) {
        buffer.put(strToNum(data.substring(i, i + 3) ), 10);
        i += 3;
      }

      if (i < data.length) {
        if (data.length - i == 1) {
          buffer.put(strToNum(data.substring(i, i + 1) ), 4);
        } else if (data.length - i == 2) {
          buffer.put(strToNum(data.substring(i, i + 2) ), 7);
        }
      }
    };

    var strToNum = function(s) {
      var num = 0;
      for (var i = 0; i < s.length; i += 1) {
        num = num * 10 + chatToNum(s.charAt(i) );
      }
      return num;
    };

    var chatToNum = function(c) {
      if ('0' <= c && c <= '9') {
        return c.charCodeAt(0) - '0'.charCodeAt(0);
      }
      throw 'illegal char :' + c;
    };

    return _this;
  };

  //---------------------------------------------------------------------
  // qrAlphaNum
  //---------------------------------------------------------------------

  var qrAlphaNum = function(data) {

    var _mode = QRMode.MODE_ALPHA_NUM;
    var _data = data;

    var _this = {};

    _this.getMode = function() {
      return _mode;
    };

    _this.getLength = function(buffer) {
      return _data.length;
    };

    _this.write = function(buffer) {

      var s = _data;

      var i = 0;

      while (i + 1 < s.length) {
        buffer.put(
          getCode(s.charAt(i) ) * 45 +
          getCode(s.charAt(i + 1) ), 11);
        i += 2;
      }

      if (i < s.length) {
        buffer.put(getCode(s.charAt(i) ), 6);
      }
    };

    var getCode = function(c) {

      if ('0' <= c && c <= '9') {
        return c.charCodeAt(0) - '0'.charCodeAt(0);
      } else if ('A' <= c && c <= 'Z') {
        return c.charCodeAt(0) - 'A'.charCodeAt(0) + 10;
      } else {
        switch (c) {
        case ' ' : return 36;
        case '$' : return 37;
        case '%' : return 38;
        case '*' : return 39;
        case '+' : return 40;
        case '-' : return 41;
        case '.' : return 42;
        case '/' : return 43;
        case ':' : return 44;
        default :
          throw 'illegal char :' + c;
        }
      }
    };

    return _this;
  };

  //---------------------------------------------------------------------
  // qr8BitByte
  //---------------------------------------------------------------------

  var qr8BitByte = function(data) {

    var _mode = QRMode.MODE_8BIT_BYTE;
    var _data = data;
    var _bytes = qrcode.stringToBytes(data);

    var _this = {};

    _this.getMode = function() {
      return _mode;
    };

    _this.getLength = function(buffer) {
      return _bytes.length;
    };

    _this.write = function(buffer) {
      for (var i = 0; i < _bytes.length; i += 1) {
        buffer.put(_bytes[i], 8);
      }
    };

    return _this;
  };

  //---------------------------------------------------------------------
  // qrKanji
  //---------------------------------------------------------------------

  var qrKanji = function(data) {

    var _mode = QRMode.MODE_KANJI;
    var _data = data;

    var stringToBytes = qrcode.stringToBytesFuncs['SJIS'];
    if (!stringToBytes) {
      throw 'sjis not supported.';
    }
    !function(c, code) {
      // self test for sjis support.
      var test = stringToBytes(c);
      if (test.length != 2 || ( (test[0] << 8) | test[1]) != code) {
        throw 'sjis not supported.';
      }
    }('\u53cb', 0x9746);

    var _bytes = stringToBytes(data);

    var _this = {};

    _this.getMode = function() {
      return _mode;
    };

    _this.getLength = function(buffer) {
      return ~~(_bytes.length / 2);
    };

    _this.write = function(buffer) {

      var data = _bytes;

      var i = 0;

      while (i + 1 < data.length) {

        var c = ( (0xff & data[i]) << 8) | (0xff & data[i + 1]);

        if (0x8140 <= c && c <= 0x9FFC) {
          c -= 0x8140;
        } else if (0xE040 <= c && c <= 0xEBBF) {
          c -= 0xC140;
        } else {
          throw 'illegal char at ' + (i + 1) + '/' + c;
        }

        c = ( (c >>> 8) & 0xff) * 0xC0 + (c & 0xff);

        buffer.put(c, 13);

        i += 2;
      }

      if (i < data.length) {
        throw 'illegal char at ' + (i + 1);
      }
    };

    return _this;
  };

  //=====================================================================
  // GIF Support etc.
  //

  //---------------------------------------------------------------------
  // byteArrayOutputStream
  //---------------------------------------------------------------------

  var byteArrayOutputStream = function() {

    var _bytes = [];

    var _this = {};

    _this.writeByte = function(b) {
      _bytes.push(b & 0xff);
    };

    _this.writeShort = function(i) {
      _this.writeByte(i);
      _this.writeByte(i >>> 8);
    };

    _this.writeBytes = function(b, off, len) {
      off = off || 0;
      len = len || b.length;
      for (var i = 0; i < len; i += 1) {
        _this.writeByte(b[i + off]);
      }
    };

    _this.writeString = function(s) {
      for (var i = 0; i < s.length; i += 1) {
        _this.writeByte(s.charCodeAt(i) );
      }
    };

    _this.toByteArray = function() {
      return _bytes;
    };

    _this.toString = function() {
      var s = '';
      s += '[';
      for (var i = 0; i < _bytes.length; i += 1) {
        if (i > 0) {
          s += ',';
        }
        s += _bytes[i];
      }
      s += ']';
      return s;
    };

    return _this;
  };

  //---------------------------------------------------------------------
  // base64EncodeOutputStream
  //---------------------------------------------------------------------

  var base64EncodeOutputStream = function() {

    var _buffer = 0;
    var _buflen = 0;
    var _length = 0;
    var _base64 = '';

    var _this = {};

    var writeEncoded = function(b) {
      _base64 += String.fromCharCode(encode(b & 0x3f) );
    };

    var encode = function(n) {
      if (n < 0) {
        // error.
      } else if (n < 26) {
        return 0x41 + n;
      } else if (n < 52) {
        return 0x61 + (n - 26);
      } else if (n < 62) {
        return 0x30 + (n - 52);
      } else if (n == 62) {
        return 0x2b;
      } else if (n == 63) {
        return 0x2f;
      }
      throw 'n:' + n;
    };

    _this.writeByte = function(n) {

      _buffer = (_buffer << 8) | (n & 0xff);
      _buflen += 8;
      _length += 1;

      while (_buflen >= 6) {
        writeEncoded(_buffer >>> (_buflen - 6) );
        _buflen -= 6;
      }
    };

    _this.flush = function() {

      if (_buflen > 0) {
        writeEncoded(_buffer << (6 - _buflen) );
        _buffer = 0;
        _buflen = 0;
      }

      if (_length % 3 != 0) {
        // padding
        var padlen = 3 - _length % 3;
        for (var i = 0; i < padlen; i += 1) {
          _base64 += '=';
        }
      }
    };

    _this.toString = function() {
      return _base64;
    };

    return _this;
  };

  //---------------------------------------------------------------------
  // base64DecodeInputStream
  //---------------------------------------------------------------------

  var base64DecodeInputStream = function(str) {

    var _str = str;
    var _pos = 0;
    var _buffer = 0;
    var _buflen = 0;

    var _this = {};

    _this.read = function() {

      while (_buflen < 8) {

        if (_pos >= _str.length) {
          if (_buflen == 0) {
            return -1;
          }
          throw 'unexpected end of file./' + _buflen;
        }

        var c = _str.charAt(_pos);
        _pos += 1;

        if (c == '=') {
          _buflen = 0;
          return -1;
        } else if (c.match(/^\s$/) ) {
          // ignore if whitespace.
          continue;
        }

        _buffer = (_buffer << 6) | decode(c.charCodeAt(0) );
        _buflen += 6;
      }

      var n = (_buffer >>> (_buflen - 8) ) & 0xff;
      _buflen -= 8;
      return n;
    };

    var decode = function(c) {
      if (0x41 <= c && c <= 0x5a) {
        return c - 0x41;
      } else if (0x61 <= c && c <= 0x7a) {
        return c - 0x61 + 26;
      } else if (0x30 <= c && c <= 0x39) {
        return c - 0x30 + 52;
      } else if (c == 0x2b) {
        return 62;
      } else if (c == 0x2f) {
        return 63;
      } else {
        throw 'c:' + c;
      }
    };

    return _this;
  };

  //---------------------------------------------------------------------
  // gifImage (B/W)
  //---------------------------------------------------------------------

  var gifImage = function(width, height) {

    var _width = width;
    var _height = height;
    var _data = new Array(width * height);

    var _this = {};

    _this.setPixel = function(x, y, pixel) {
      _data[y * _width + x] = pixel;
    };

    _this.write = function(out) {

      //---------------------------------
      // GIF Signature

      out.writeString('GIF87a');

      //---------------------------------
      // Screen Descriptor

      out.writeShort(_width);
      out.writeShort(_height);

      out.writeByte(0x80); // 2bit
      out.writeByte(0);
      out.writeByte(0);

      //---------------------------------
      // Global Color Map

      // black
      out.writeByte(0x00);
      out.writeByte(0x00);
      out.writeByte(0x00);

      // white
      out.writeByte(0xff);
      out.writeByte(0xff);
      out.writeByte(0xff);

      //---------------------------------
      // Image Descriptor

      out.writeString(',');
      out.writeShort(0);
      out.writeShort(0);
      out.writeShort(_width);
      out.writeShort(_height);
      out.writeByte(0);

      //---------------------------------
      // Local Color Map

      //---------------------------------
      // Raster Data

      var lzwMinCodeSize = 2;
      var raster = getLZWRaster(lzwMinCodeSize);

      out.writeByte(lzwMinCodeSize);

      var offset = 0;

      while (raster.length - offset > 255) {
        out.writeByte(255);
        out.writeBytes(raster, offset, 255);
        offset += 255;
      }

      out.writeByte(raster.length - offset);
      out.writeBytes(raster, offset, raster.length - offset);
      out.writeByte(0x00);

      //---------------------------------
      // GIF Terminator
      out.writeString(';');
    };

    var bitOutputStream = function(out) {

      var _out = out;
      var _bitLength = 0;
      var _bitBuffer = 0;

      var _this = {};

      _this.write = function(data, length) {

        if ( (data >>> length) != 0) {
          throw 'length over';
        }

        while (_bitLength + length >= 8) {
          _out.writeByte(0xff & ( (data << _bitLength) | _bitBuffer) );
          length -= (8 - _bitLength);
          data >>>= (8 - _bitLength);
          _bitBuffer = 0;
          _bitLength = 0;
        }

        _bitBuffer = (data << _bitLength) | _bitBuffer;
        _bitLength = _bitLength + length;
      };

      _this.flush = function() {
        if (_bitLength > 0) {
          _out.writeByte(_bitBuffer);
        }
      };

      return _this;
    };

    var getLZWRaster = function(lzwMinCodeSize) {

      var clearCode = 1 << lzwMinCodeSize;
      var endCode = (1 << lzwMinCodeSize) + 1;
      var bitLength = lzwMinCodeSize + 1;

      // Setup LZWTable
      var table = lzwTable();

      for (var i = 0; i < clearCode; i += 1) {
        table.add(String.fromCharCode(i) );
      }
      table.add(String.fromCharCode(clearCode) );
      table.add(String.fromCharCode(endCode) );

      var byteOut = byteArrayOutputStream();
      var bitOut = bitOutputStream(byteOut);

      // clear code
      bitOut.write(clearCode, bitLength);

      var dataIndex = 0;

      var s = String.fromCharCode(_data[dataIndex]);
      dataIndex += 1;

      while (dataIndex < _data.length) {

        var c = String.fromCharCode(_data[dataIndex]);
        dataIndex += 1;

        if (table.contains(s + c) ) {

          s = s + c;

        } else {

          bitOut.write(table.indexOf(s), bitLength);

          if (table.size() < 0xfff) {

            if (table.size() == (1 << bitLength) ) {
              bitLength += 1;
            }

            table.add(s + c);
          }

          s = c;
        }
      }

      bitOut.write(table.indexOf(s), bitLength);

      // end code
      bitOut.write(endCode, bitLength);

      bitOut.flush();

      return byteOut.toByteArray();
    };

    var lzwTable = function() {

      var _map = {};
      var _size = 0;

      var _this = {};

      _this.add = function(key) {
        if (_this.contains(key) ) {
          throw 'dup key:' + key;
        }
        _map[key] = _size;
        _size += 1;
      };

      _this.size = function() {
        return _size;
      };

      _this.indexOf = function(key) {
        return _map[key];
      };

      _this.contains = function(key) {
        return typeof _map[key] != 'undefined';
      };

      return _this;
    };

    return _this;
  };

  var createDataURL = function(width, height, getPixel) {
    var gif = gifImage(width, height);
    for (var y = 0; y < height; y += 1) {
      for (var x = 0; x < width; x += 1) {
        gif.setPixel(x, y, getPixel(x, y) );
      }
    }

    var b = byteArrayOutputStream();
    gif.write(b);

    var base64 = base64EncodeOutputStream();
    var bytes = b.toByteArray();
    for (var i = 0; i < bytes.length; i += 1) {
      base64.writeByte(bytes[i]);
    }
    base64.flush();

    return 'data:image/gif;base64,' + base64;
  };

  //---------------------------------------------------------------------
  // returns qrcode function.

  return qrcode;
}();

// multibyte support
!function() {

  qrcode.stringToBytesFuncs['UTF-8'] = function(s) {
    // http://stackoverflow.com/questions/18729405/how-to-convert-utf8-string-to-byte-array
    function toUTF8Array(str) {
      var utf8 = [];
      for (var i=0; i < str.length; i++) {
        var charcode = str.charCodeAt(i);
        if (charcode < 0x80) utf8.push(charcode);
        else if (charcode < 0x800) {
          utf8.push(0xc0 | (charcode >> 6),
              0x80 | (charcode & 0x3f));
        }
        else if (charcode < 0xd800 || charcode >= 0xe000) {
          utf8.push(0xe0 | (charcode >> 12),
              0x80 | ((charcode>>6) & 0x3f),
              0x80 | (charcode & 0x3f));
        }
        // surrogate pair
        else {
          i++;
          // UTF-16 encodes 0x10000-0x10FFFF by
          // subtracting 0x10000 and splitting the
          // 20 bits of 0x0-0xFFFFF into two halves
          charcode = 0x10000 + (((charcode & 0x3ff)<<10)
            | (str.charCodeAt(i) & 0x3ff));
          utf8.push(0xf0 | (charcode >>18),
              0x80 | ((charcode>>12) & 0x3f),
              0x80 | ((charcode>>6) & 0x3f),
              0x80 | (charcode & 0x3f));
        }
      }
      return utf8;
    }
    return toUTF8Array(s);
  };

}();

(function (factory) {
  if (typeof define === 'function' && define.amd) {
      define([], factory);
  } else if (typeof exports === 'object') {
      module.exports = factory();
  }
}(function () {
    return qrcode;
}));

MXI_QR_END;
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
