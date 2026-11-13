// midnox Kartentisch – Audio: prozedurale Soundeffekte + dezente Lobby-Musik.
// Keine Dateien/Lizenzen, läuft offline im WebView. window.SND
window.SND = (function(){
  let ctx=null, master=null, musicGain=null, muted=false, speechOn=true, musicTimer=null, musicOn=null, step=0;
  function init(){ if(ctx) return; try{
    ctx=new (window.AudioContext||window.webkitAudioContext)();
    master=ctx.createGain(); master.gain.value=0.55; master.connect(ctx.destination);
    musicGain=ctx.createGain(); musicGain.gain.value=0.14; musicGain.connect(master);
  }catch(e){} }
  function resume(){ init(); try{ if(ctx&&ctx.state==='suspended') ctx.resume(); }catch(e){} }
  const now=()=>ctx?ctx.currentTime:0;
  function tone(freq,start,dur,type,vol,target){ if(!ctx)return; const o=ctx.createOscillator(),g=ctx.createGain(); o.type=type||'square'; const t=now()+start; o.frequency.setValueAtTime(freq,t); g.gain.setValueAtTime(0.0001,t); g.gain.exponentialRampToValueAtTime(vol||0.3,t+0.008); g.gain.exponentialRampToValueAtTime(0.0005,t+dur); o.connect(g).connect(target||master); o.start(t); o.stop(t+dur+0.03); }
  function sweep(f1,f2,start,dur,type,vol){ if(!ctx)return; const o=ctx.createOscillator(),g=ctx.createGain(); o.type=type||'sawtooth'; const t=now()+start; o.frequency.setValueAtTime(f1,t); o.frequency.exponentialRampToValueAtTime(Math.max(20,f2),t+dur); g.gain.setValueAtTime(vol||0.3,t); g.gain.exponentialRampToValueAtTime(0.0005,t+dur); o.connect(g).connect(master); o.start(t); o.stop(t+dur+0.03); }
  function noise(start,dur,vol,lp){ if(!ctx)return; const n=ctx.createBufferSource(),buf=ctx.createBuffer(1,Math.max(1,(ctx.sampleRate*dur)|0),ctx.sampleRate),d=buf.getChannelData(0); for(let i=0;i<d.length;i++)d[i]=Math.random()*2-1; n.buffer=buf; const g=ctx.createGain(); const t=now()+start; g.gain.setValueAtTime(vol||0.3,t); g.gain.exponentialRampToValueAtTime(0.0005,t+dur); let node=n; if(lp){ const f=ctx.createBiquadFilter(); f.type='lowpass'; f.frequency.value=lp; n.connect(f); node=f; } node.connect(g).connect(master); n.start(t); n.stop(t+dur+0.02); }
  const SFX={
    card:()=>noise(0,0.07,0.22,3800),
    deal:()=>{noise(0,0.05,0.18,4200);noise(0.09,0.05,0.18,4200);},
    chip:()=>{tone(2100,0,0.03,'triangle',0.16);tone(2600,0.035,0.04,'triangle',0.14);},
    chips:()=>[0,0.05,0.1,0.16,0.21].forEach((t,i)=>tone(2000+i*180,t,0.04,'triangle',0.15)),
    fold:()=>sweep(400,180,0,0.15,'triangle',0.14),
    turn:()=>{tone(880,0,0.09,'triangle',0.2);tone(1175,0.09,0.14,'triangle',0.18);},
    win:()=>[659,784,988,1319].forEach((f,i)=>tone(f,i*0.08,0.28,'triangle',0.24)),
    flip:()=>{noise(0,0.06,0.2,3000);tone(700,0.03,0.05,'triangle',0.1);},
    check:()=>{tone(180,0,0.05,'sine',0.35);tone(180,0.09,0.05,'sine',0.3);},
    allin:()=>{[0,0.04,0.08,0.12,0.16,0.2].forEach((t,i)=>tone(1900+i*150,t,0.04,'triangle',0.16));sweep(300,900,0.1,0.35,'sawtooth',0.12);},
    bust:()=>sweep(420,90,0,0.45,'sawtooth',0.24),
    lose:()=>{tone(330,0,0.18,'triangle',0.2);tone(262,0.16,0.3,'triangle',0.2);},
    plus:()=>{tone(392,0,0.09,'square',0.16);tone(330,0.09,0.14,'square',0.16);},
    mau:()=>{tone(784,0,0.1,'triangle',0.24);tone(1047,0.1,0.2,'triangle',0.24);},
    join:()=>{tone(660,0,0.08,'triangle',0.25);tone(880,0.07,0.12,'triangle',0.25);},
    select:()=>tone(520,0,0.05,'square',0.14),
    start:()=>[523,659,784,1047].forEach((f,i)=>tone(f,i*0.05,0.25,'square',0.2)),
    tick:()=>tone(440,0,0.04,'square',0.14),
    go:()=>{tone(784,0,0.15,'square',0.3);tone(1175,0.05,0.28,'square',0.26);},
    buzz:()=>sweep(300,120,0,0.25,'sawtooth',0.3),
    correct:()=>{tone(659,0,0.1,'square',0.24);tone(988,0.09,0.22,'square',0.24);},
    wrong:()=>{tone(200,0,0.25,'sawtooth',0.24);tone(150,0.12,0.26,'sawtooth',0.2);},
    ding:()=>{tone(1047,0,0.12,'triangle',0.3);tone(1568,0.1,0.26,'triangle',0.2);},
    boom:()=>{noise(0,0.5,0.5,900);sweep(220,40,0,0.5,'sawtooth',0.3);},
    eliminate:()=>sweep(520,110,0,0.5,'sawtooth',0.3),
    bonus:()=>[784,988,1175,1568,2093].forEach((f,i)=>tone(f,i*0.06,0.22,'triangle',0.22)),
    fanfare:()=>[523,659,784,1047,784,1047,1319].forEach((f,i)=>tone(f,i*0.1,0.3,'square',0.24))
  };
  const SCALES={lobby:[0,3,5,7,10],game:[0,2,4,7,9]};
  function startMusic(kind){ stopMusic(); musicOn=kind; step=0; init(); if(!ctx)return;
    const bpm=kind==='game'?130:104, beat=60/bpm/2, root=kind==='game'?130.81:110, scale=SCALES[kind]||SCALES.lobby;
    musicTimer=setInterval(()=>{ if(muted||!ctx)return;
      const bass=(root/2)*Math.pow(2,(step%4<2?0:7)/12); tone(bass,0,beat*0.9,'triangle',0.16,musicGain);
      const n=root*2*Math.pow(2,scale[step%scale.length]/12)*(step%8<4?1:1.5); tone(n,0,beat*0.5,'square',0.08,musicGain);
      if(step%2===0) noise(0,0.025,0.06,5000);
      step++;
    }, beat*1000); }
  function stopMusic(){ if(musicTimer){clearInterval(musicTimer);musicTimer=null;} musicOn=null; }
  function say(text){ if(!speechOn||muted||!text)return; try{ if(!window.speechSynthesis)return; const u=new SpeechSynthesisUtterance(text); u.lang='de-DE'; u.rate=1.05; speechSynthesis.cancel(); speechSynthesis.speak(u); }catch(e){} }
  // Ansager: spielt /voice/<key>.mp3 (vorgerendert mit ElevenLabs), sonst TTS-Notnagel.
  let vCache={}, vMiss={};
  function voice(key,fallback){ if(muted)return; if(vMiss[key]){ say(fallback); return; }
    try{ let a=vCache[key]; if(!a){ a=new Audio('/voice/'+key+'.mp3'); a.volume=0.9; a.onerror=()=>{ vMiss[key]=1; }; vCache[key]=a; } a.currentTime=0; const pr=a.play(); if(pr&&pr.catch) pr.catch(()=>{ vMiss[key]=1; say(fallback); }); }
    catch(e){ say(fallback); } }
  return {
    resume, play:(n)=>{ if(muted)return; init(); if(SFX[n])SFX[n](); },
    music:(k)=>{ if(k===musicOn)return; if(!k)stopMusic(); else startMusic(k); },
    say, voice,
    toggle:()=>{ muted=!muted; if(master)master.gain.value=muted?0:0.55; if(muted){try{speechSynthesis.cancel();}catch(e){}} return muted; },
    setMuted:(m)=>{ muted=m; if(master)master.gain.value=m?0:0.55; },
    isMuted:()=>muted, setSpeech:(s)=>{speechOn=s;}
  };
})();
