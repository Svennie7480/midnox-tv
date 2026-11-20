// midnox Krimi-Abend – Atmosphäre ohne Audiodateien: Regen, Uhrticken, düstere Klänge, Effekte.
// Erzähler: eingesprochene Clips aus /voice/<key>.mp3 (falls vorhanden), sonst Android-Sprachausgabe.
window.SND = (function(){
  let ctx=null, master=null, amb=null, muted=false, voiceOn=true, ambTimer=null, ambKind=null, rain=null, step=0;
  function init(){ if(ctx) return; try{
    ctx=new (window.AudioContext||window.webkitAudioContext)();
    master=ctx.createGain(); master.gain.value=0.6; master.connect(ctx.destination);
    amb=ctx.createGain(); amb.gain.value=0.22; amb.connect(master);
  }catch(e){} }
  function resume(){ init(); try{ if(ctx&&ctx.state==='suspended') ctx.resume(); }catch(e){} }
  const now=()=>ctx?ctx.currentTime:0;
  function tone(f,start,dur,type,vol,target,attack){ if(!ctx)return; const o=ctx.createOscillator(),g=ctx.createGain(); o.type=type||'sine'; const t=now()+start;
    o.frequency.setValueAtTime(f,t); g.gain.setValueAtTime(0.0001,t); g.gain.exponentialRampToValueAtTime(vol||0.3,t+(attack||0.01)); g.gain.exponentialRampToValueAtTime(0.0005,t+dur);
    o.connect(g).connect(target||master); o.start(t); o.stop(t+dur+0.05); }
  function noise(start,dur,vol,lp,target,hp){ if(!ctx)return; const n=ctx.createBufferSource(),buf=ctx.createBuffer(1,Math.max(1,(ctx.sampleRate*dur)|0),ctx.sampleRate),d=buf.getChannelData(0);
    for(let i=0;i<d.length;i++)d[i]=Math.random()*2-1; n.buffer=buf; const g=ctx.createGain(); const t=now()+start; g.gain.setValueAtTime(vol||0.3,t); g.gain.exponentialRampToValueAtTime(0.0005,t+dur);
    let node=n; if(lp){ const f=ctx.createBiquadFilter(); f.type='lowpass'; f.frequency.value=lp; node.connect(f); node=f; } if(hp){ const f=ctx.createBiquadFilter(); f.type='highpass'; f.frequency.value=hp; node.connect(f); node=f; }
    node.connect(g).connect(target||master); n.start(t); n.stop(t+dur+0.02); }
  function piano(f,start,vol){ tone(f,start,2.2,'triangle',vol||0.12,amb,0.005); tone(f*2,start,1.2,'sine',(vol||0.12)*0.3,amb,0.005); }
  function startRain(){ if(!ctx||rain) return; const len=ctx.sampleRate*3, buf=ctx.createBuffer(1,len,ctx.sampleRate), d=buf.getChannelData(0);
    let last=0; for(let i=0;i<len;i++){ const w=Math.random()*2-1; last=(last+0.02*w)/1.02; d[i]=last*3.5+(Math.random()<0.0008?(Math.random()*2-1)*0.6:0); }
    const n=ctx.createBufferSource(); n.buffer=buf; n.loop=true; const f=ctx.createBiquadFilter(); f.type='lowpass'; f.frequency.value=1400; const g=ctx.createGain(); g.gain.value=0.55;
    n.connect(f).connect(g).connect(amb); n.start(); rain={n,g}; }
  function stopRain(){ if(rain){ try{ rain.n.stop(); }catch(e){} rain=null; } }
  const MOTIF=[220,261.63,246.94,220,196,207.65,220,164.81];   // A-Moll
  function startAmb(kind){ stopAmb(); init(); if(!ctx) return; ambKind=kind; step=0; startRain();
    const beat=kind==='tense'?0.5:1.0;
    ambTimer=setInterval(()=>{ if(muted||!ctx) return;
      if(kind!=='quiet') tone(1800,0,0.03,'square',step%2?0.025:0.035,amb);                                   // Uhrticken
      if(kind==='tense'&&step%2===0){ tone(55,0,0.35,'sine',0.35,amb); tone(55,0.18,0.3,'sine',0.25,amb); }   // Herzschlag
      if(step%8===0){ tone(55,0,8*beat,'sawtooth',0.035,amb,1.5); tone(82.4,0,8*beat,'sine',0.06,amb,1.5); }   // Bordun
      if(kind==='mood'&&step%4===2&&Math.random()<0.7) piano(MOTIF[(step/4|0)%MOTIF.length],0,0.1);
      if(Math.random()<0.006) thunder();
      step++;
    }, beat*1000); }
  function stopAmb(){ if(ambTimer){ clearInterval(ambTimer); ambTimer=null; } ambKind=null; stopRain(); }
  function thunder(){ noise(0,2.6,0.5,180,amb); noise(0.05,0.5,0.25,900,amb); }
  const SFX={
    join:()=>{ piano(392,0,0.25); piano(523.25,0.12,0.22); },
    select:()=>tone(660,0,0.06,'triangle',0.12),
    start:()=>{ [220,261.63,329.63,440].forEach((f,i)=>tone(f,i*0.18,1.6,'triangle',0.18)); thunder(); },
    clue:()=>{ tone(1318.5,0,0.5,'sine',0.18); tone(1975.5,0.08,0.7,'sine',0.12); },
    paper:()=>{ noise(0,0.18,0.25,5000,null,900); noise(0.12,0.12,0.18,4000,null,1200); },
    sting:()=>{ [[110,0],[103.83,0.45],[87.31,0.9]].forEach(([f,t])=>{ tone(f,t,t>0.8?2.4:0.4,'sawtooth',0.28); tone(f*2,t,t>0.8?2.2:0.38,'square',0.08); }); noise(0.9,1.8,0.25,400); },
    ask:()=>{ tone(440,0,0.25,'triangle',0.2); tone(349.23,0.2,0.5,'triangle',0.2); },
    tick:()=>tone(1500,0,0.04,'square',0.1),
    ready:()=>tone(880,0,0.12,'triangle',0.16),
    vote:()=>{ tone(587.33,0,0.12,'triangle',0.18); tone(880,0.1,0.2,'triangle',0.16); },
    drum:()=>{ for(let i=0;i<24;i++) noise(i*0.075,0.07,0.08+i*0.012,700); },
    reveal:()=>{ noise(0,0.6,0.5,1200); [110,138.59,164.81,220].forEach((f,i)=>tone(f,0.05+i*0.02,3,'sawtooth',0.12)); },
    win:()=>[392,523.25,659.25,783.99,1046.5].forEach((f,i)=>tone(f,i*0.12,0.9,'triangle',0.2)),
    lose:()=>{ tone(220,0,0.6,'triangle',0.2); tone(207.65,0.4,0.6,'triangle',0.2); tone(164.81,0.8,1.4,'triangle',0.2); },
    thunder
  };
  // ---- Erzähler ----
  let cur=null;
  function hush(){ try{ if(cur){ cur.pause(); cur=null; } }catch(e){} try{ Android.hush(); }catch(e){} try{ speechSynthesis.cancel(); }catch(e){} }
  function speaking(){ try{ if(cur&&!cur.paused&&!cur.ended) return true; }catch(e){} try{ if(Android.speaking()) return true; }catch(e){} try{ if(window.speechSynthesis&&speechSynthesis.speaking) return true; }catch(e){} return false; }
  function tts(text){ try{ if(Android.say(text)) return true; }catch(e){}
    try{ if(window.speechSynthesis&&window.KRIMI_WEBTTS){ const u=new SpeechSynthesisUtterance(text); u.lang='de-DE'; u.rate=0.95; speechSynthesis.cancel(); speechSynthesis.speak(u); return true; } }catch(e){} return false; }
  // spielt /voice/key.mp3 oder spricht den Text; Promise löst nach dem Ende auf (false = nichts gesprochen)
  function narrate(key,text){ hush(); if(muted||!voiceOn||!text) return Promise.resolve(false);
    return new Promise(res=>{ let done=false; const fin=(v)=>{ if(!done){ done=true; res(v); } };
      const viaTts=()=>{ if(!tts(text)) return fin(false); const t0=Date.now(); const iv=setInterval(()=>{ if((Date.now()-t0>1500&&!speaking())||Date.now()-t0>120000){ clearInterval(iv); fin(true); } },300); };
      if(!key) return viaTts();
      try{ const a=new Audio('/voice/'+key+'.mp3'); cur=a; a.onended=()=>fin(true); a.onerror=()=>{ if(cur===a) cur=null; viaTts(); };
        const p=a.play(); if(p&&p.catch) p.catch(()=>{ if(cur===a) cur=null; viaTts(); }); }
      catch(e){ viaTts(); } }); }
  return {
    resume, play:(n)=>{ if(muted)return; init(); if(SFX[n])SFX[n](); },
    amb:(k)=>{ if(k===ambKind) return; if(!k) stopAmb(); else startAmb(k); },
    narrate, hush, speaking,
    toggle:()=>{ muted=!muted; if(master) master.gain.value=muted?0:0.6; if(muted) hush(); return muted; },
    isMuted:()=>muted, voice:(v)=>{ if(v!==undefined){ voiceOn=v; if(!v) hush(); } return voiceOn; }
  };
})();
