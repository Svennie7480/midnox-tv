'use strict';
/* Sprachausgabe: Fragen, Antworten und Erklärungen werden auf midnox.de einmal mit ElevenLabs erzeugt und dort gespeichert.
   Die App schickt nur den Schlüssel (Fragen-ID bzw. ID_e) – den gesprochenen Text kennt der Server aus fahrschule/tts.json. */
const TTS = {
  SERVER: (() => { try { return localStorage.getItem('fs_tts'); } catch (e) { return ''; } })() || 'https://midnox.de/midnoxtv/fahrschule/index.php',
  LETTERS: ['A', 'B', 'C', 'D', 'E'],
  /** Schreibweisen, die vorgelesen anders klingen sollen */
  speak(s) {
    s = String(s || '');
    const R = [
      [/km\/h/g, 'Kilometer pro Stunde'], [/‰/g, ' Promille'], [/§\s*/g, 'Paragraf '], [/\bAbs\.\s*/g, 'Absatz '], [/\bNr\.\s*/g, 'Nummer '],
      [/\bStVO\b/g, 'Straßenverkehrs-Ordnung'], [/\bStVG\b/g, 'Straßenverkehrsgesetz'], [/\bStGB\b/g, 'Strafgesetzbuch'], [/\bStVZO\b/g, 'Straßenverkehrs-Zulassungs-Ordnung'],
      [/\bz\. ?B\./g, 'zum Beispiel'], [/\bbzw\./g, 'beziehungsweise'], [/\bca\./g, 'circa'], [/\bd\. ?h\./g, 'das heißt'], [/\bu\. ?a\./g, 'unter anderem'], [/\bggf\./g, 'gegebenenfalls'], [/\bmind\./g, 'mindestens'], [/\bmax\./g, 'maximal'],
      [/U\/min/g, 'Umdrehungen pro Minute'], [/ng\/ml/g, 'Nanogramm pro Milliliter'], [/cm³/g, 'Kubikzentimeter'],
      [/(\d)\s?kg\b/g, '$1 Kilogramm'], [/(\d)\s?mm\b/g, '$1 Millimeter'], [/(\d)\s?cm\b/g, '$1 Zentimeter'], [/(\d)\s?m\b/g, '$1 Meter'], [/(\d)\s?t\b/g, '$1 Tonnen'], [/(\d)\s?s\b/g, '$1 Sekunden'], [/(\d)\s?h\b/g, '$1 Uhr'],
      [/(\d)\.(\d{3})/g, '$1$2'], [/\s×\s/g, ' mal '], [/\s÷\s/g, ' geteilt durch '], [/\s=\s/g, ' gleich '], [/\s\+\s/g, ' plus '], [/\s→\s/g, ' zu '],
      [/\s[–—]\s/g, ', '], [/[„“"»«]/g, ''], [/\s+/g, ' '],
    ];
    for (const [a, b] of R) s = s.replace(a, b);
    return s.trim();
  },
  unit(u) { return { m: 'Meter', mm: 'Millimeter', 's': 'Sekunden', 'Min.': 'Minuten', 'km/h': 'Kilometer pro Stunde', '‰': 'Promille', kg: 'Kilogramm' }[u] || u || ''; },
  dot(s) { s = String(s).trim(); return /[.!?:]$/.test(s) ? s : s + '.'; },
  /** gesprochener Text zur Frage (mit Antworten) */
  question(q) {
    let t = TTS.dot(q.q);
    if (q.type !== 'num' && q.a) t += ' ' + q.a.map((a, i) => 'Antwort ' + TTS.LETTERS[i] + ': ' + TTS.dot(a.t)).join(' ');
    return TTS.speak(t);
  },
  /** gesprochener Text zur Auflösung */
  explain(q) {
    const right = q.type === 'num' ? String(q.ans).replace('.', ',') + ' ' + TTS.unit(q.unit) : q.a.filter(a => a.ok).map(a => a.t).join(', und: ');
    return TTS.speak('Richtig ist: ' + TTS.dot(right) + ' ' + (q.ex || ''));
  },
  all(list) { const o = {}; for (const q of list) { o[q.id] = TTS.question(q); o[q.id + '_e'] = TTS.explain(q); } return o; },

  // ---------------------------------------------------------------- Wiedergabe (nur in der App)
  el: null, cur: '', pre: {}, failed: 0,
  url(k) { return TTS.SERVER + '?a=tts&k=' + encodeURIComponent(k); },
  on() { return typeof ST !== 'undefined' && !!ST.d && ST.d.voice !== false; },
  stop() { if (TTS.el) { try { TTS.el.pause(); } catch (e) {} TTS.el.onended = null; } TTS.cur = ''; document.body.classList.remove('speaking'); TTS.mark(); },
  /** Schlüssel abspielen; ein zweiter Druck auf denselben Schlüssel stoppt */
  play(k, done) {
    if (TTS.cur === k) { TTS.stop(); return; }
    TTS.stop();
    if (!TTS.el) TTS.el = new Audio();
    const a = TTS.el; a.src = TTS.url(k); TTS.cur = k; document.body.classList.add('speaking'); TTS.mark();
    a.onended = () => { TTS.cur = ''; document.body.classList.remove('speaking'); TTS.mark(); if (done) done(); };
    a.onerror = () => { TTS.cur = ''; document.body.classList.remove('speaking'); TTS.mark(); if (++TTS.failed === 1) toast('Sprachausgabe gerade nicht erreichbar'); };
    const p = a.play(); if (p && p.catch) p.catch(() => {});
  },
  /** nächste Datei vorab laden, damit sie sofort startet */
  prefetch(k) { if (!TTS.on() || TTS.pre[k]) return; const a = new Audio(); a.preload = 'auto'; a.src = TTS.url(k); TTS.pre[k] = a; const keys = Object.keys(TTS.pre); if (keys.length > 6) delete TTS.pre[keys[0]]; },
  mark() { document.querySelectorAll('[data-k=say],[data-k=sayx]').forEach(b => b.classList.toggle('on', !!TTS.cur && b.dataset.say === TTS.cur)); },
};
if (typeof window !== 'undefined') window.TTS = TTS;
if (typeof module !== 'undefined') module.exports = TTS;
