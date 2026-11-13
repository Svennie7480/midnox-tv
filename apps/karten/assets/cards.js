// midnox Karten – gemeinsame Kartenlogik für Fernseher und Handy (und Node-Tests).
// Karte = Rang + Farbe, z. B. "AS" (Pik-Ass), "TD" (Karo-Zehn). Farben: S Pik, H Herz, D Karo, C Kreuz.
(function (root) {
  var SUITS = ['S', 'H', 'D', 'C'];
  var SYM = { S: '♠', H: '♥', D: '♦', C: '♣' };
  var SNAME = { S: 'Pik', H: 'Herz', D: 'Karo', C: 'Kreuz' };
  var RANKS = '23456789TJQKA';
  var RDISP = { T: '10', J: 'B', Q: 'D', K: 'K', A: 'A' };
  var RNAME = { '2': 'Zweien', '3': 'Dreien', '4': 'Vieren', '5': 'Fünfen', '6': 'Sechsen', '7': 'Siebenen', '8': 'Achten', '9': 'Neunen', T: 'Zehnen', J: 'Buben', Q: 'Damen', K: 'Könige', A: 'Asse' };

  function deck(ranks) {
    var d = [], r = ranks || RANKS;
    for (var i = 0; i < r.length; i++) for (var s = 0; s < 4; s++) d.push(r[i] + SUITS[s]);
    return d;
  }
  function rnd(n) {
    try {
      if (root.crypto && root.crypto.getRandomValues) { var a = new Uint32Array(1); root.crypto.getRandomValues(a); return a[0] % n; }
    } catch (e) {}
    return Math.floor(Math.random() * n);
  }
  function shuffle(a) {
    a = a.slice();
    for (var i = a.length - 1; i > 0; i--) { var j = rnd(i + 1), t = a[i]; a[i] = a[j]; a[j] = t; }
    return a;
  }
  function rv(c) { return RANKS.indexOf(c[0]); }
  function disp(c) { return (RDISP[c[0]] || c[0]) + SYM[c[1]] + '\uFE0E'; }
  function red(c) { return c[1] === 'H' || c[1] === 'D'; }

  // Karte als HTML. Größe folgt der Schriftgröße des Containers (em).
  function html(c, cls) {
    if (!c || c === '?') return '<div class="card back ' + (cls || '') + '"></div>';
    var r = RDISP[c[0]] || c[0], s = SYM[c[1]], face = 'JQK'.indexOf(c[0]) >= 0;
    return '<div class="card ' + (red(c) ? 'red ' : 'blk ') + (cls || '') + '"><span class="cr">' + r + '<br>' + s + '</span>' +
      '<span class="cm">' + (face ? r : s) + '</span></div>';
  }
  var CSS = '.card{position:relative;display:inline-block;flex:none;width:2.9em;height:4.1em;border-radius:.34em;background:linear-gradient(160deg,#fff,#e9e9f2);' +
    'box-shadow:0 .12em .4em #0008;color:#15141f;font-weight:800;font-family:Georgia,serif;user-select:none;-webkit-user-select:none}' +
    '.card.red{color:#d21f3c}.card .cr{position:absolute;left:.22em;top:.14em;font-size:.82em;line-height:.95;text-align:center}' +
    '.card .cm{position:absolute;left:0;right:0;bottom:.12em;text-align:center;font-size:2em;line-height:1.1}' +
    '.card.back{background:linear-gradient(135deg,#7C3AED,#3B5BF0 55%,#06B6D4);border:.14em solid #fff}' +
    '.card.back:after{content:"";position:absolute;inset:.3em;border:.06em solid #fff6;border-radius:.2em;' +
    'background:repeating-linear-gradient(45deg,#fff2 0 .16em,transparent .16em .42em)}' +
    '.card.dim{filter:brightness(.5)}.card.win{box-shadow:0 0 0 .14em #FACC15,0 0 1em #FACC15aa}';
  function injectCss() {
    if (!root.document) return;
    var st = root.document.createElement('style'); st.textContent = CSS; root.document.head.appendChild(st);
  }

  // ---------- Poker: beste Hand aus 5–7 Karten ----------
  // Ergebnis: {score:[Kategorie, Kicker…], name, five:[Karten]} – größerer score gewinnt (lexikografisch).
  function best(cards) {
    var byRank = [], bySuit = { S: [], H: [], D: [], C: [] }, i;
    for (i = 0; i < 13; i++) byRank.push([]);
    for (i = 0; i < cards.length; i++) { byRank[rv(cards[i])].push(cards[i]); bySuit[cards[i][1]].push(cards[i]); }
    function straight(have) {   // have(rank) -> Karte oder null; liefert die fünf Karten der höchsten Straße
      for (var hi = 12; hi >= 3; hi--) {
        var run = [];
        for (var k = 0; k < 5; k++) { var r = hi - k; if (r < 0) r = 12; var c = have(r); if (!c) break; run.push(c); }   // r=-1 -> Ass (Wheel)
        if (run.length === 5) return { hi: hi, five: run };
      }
      return null;
    }
    var flushSuit = null;
    for (i = 0; i < 4; i++) if (bySuit[SUITS[i]].length >= 5) flushSuit = SUITS[i];
    if (flushSuit) {
      var fl = bySuit[flushSuit];
      var sf = straight(function (r) { for (var j = 0; j < fl.length; j++) if (rv(fl[j]) === r) return fl[j]; return null; });
      if (sf) return { score: [8, sf.hi], name: sf.hi === 12 ? 'Royal Flush' : 'Straight Flush', five: sf.five };
    }
    var groups = { 4: [], 3: [], 2: [], 1: [] };
    for (i = 12; i >= 0; i--) if (byRank[i].length) groups[byRank[i].length].push(i);
    function kick(excl, n) {
      var out = [];
      for (var r = 12; r >= 0 && out.length < n; r--) if (excl.indexOf(r) < 0) for (var j = 0; j < byRank[r].length && out.length < n; j++) out.push(byRank[r][j]);
      return out;
    }
    function ranks(cs) { return cs.map(rv); }
    var k;
    if (groups[4].length) {
      var q = groups[4][0]; k = kick([q], 1);
      return { score: [7, q].concat(ranks(k)), name: 'Vierling', five: byRank[q].concat(k) };
    }
    if (groups[3].length && (groups[3].length > 1 || groups[2].length)) {
      var t = groups[3][0], p = groups[3].length > 1 ? Math.max(groups[3][1], groups[2].length ? groups[2][0] : -1) : groups[2][0];
      return { score: [6, t, p], name: 'Full House', five: byRank[t].concat(byRank[p].slice(0, 2)) };
    }
    if (flushSuit) {
      var f5 = bySuit[flushSuit].slice().sort(function (a, b) { return rv(b) - rv(a); }).slice(0, 5);
      return { score: [5].concat(ranks(f5)), name: 'Flush', five: f5 };
    }
    var st = straight(function (r) { return byRank[r].length ? byRank[r][0] : null; });
    if (st) return { score: [4, st.hi], name: 'Straße', five: st.five };
    if (groups[3].length) {
      var t3 = groups[3][0]; k = kick([t3], 2);
      return { score: [3, t3].concat(ranks(k)), name: 'Drilling', five: byRank[t3].concat(k) };
    }
    if (groups[2].length >= 2) {
      var a = groups[2][0], b = groups[2][1]; k = kick([a, b], 1);
      return { score: [2, a, b].concat(ranks(k)), name: 'Zwei Paare', five: byRank[a].concat(byRank[b], k) };
    }
    if (groups[2].length) {
      var pr = groups[2][0]; k = kick([pr], 3);
      return { score: [1, pr].concat(ranks(k)), name: 'Paar ' + RNAME[RANKS[pr]], five: byRank[pr].concat(k) };
    }
    k = kick([], 5);
    return { score: [0].concat(ranks(k)), name: 'Höchste Karte', five: k };
  }
  function cmp(a, b) {
    for (var i = 0; i < Math.max(a.length, b.length); i++) { var d = (a[i] || 0) - (b[i] || 0); if (d) return d; }
    return 0;
  }
  // Haupt- und Nebenpötte. contrib: [{id, total, folded}] -> [{amount, elig:[id…]}]
  function sidePots(contrib) {
    var levels = [], i;
    for (i = 0; i < contrib.length; i++) if (!contrib[i].folded && contrib[i].total > 0 && levels.indexOf(contrib[i].total) < 0) levels.push(contrib[i].total);
    levels.sort(function (a, b) { return a - b; });
    var pots = [], prev = 0;
    for (var l = 0; l < levels.length; l++) {
      var lv = levels[l], amount = 0, elig = [];
      for (i = 0; i < contrib.length; i++) {
        amount += Math.max(0, Math.min(contrib[i].total, lv) - prev);
        if (!contrib[i].folded && contrib[i].total >= lv) elig.push(contrib[i].id);
      }
      if (amount > 0) pots.push({ amount: amount, elig: elig });
      prev = lv;
    }
    // Chips, die über dem höchsten Einsatz eines noch aktiven Spielers liegen (von Ausgestiegenen), in den letzten Pott
    var rest = 0;
    for (i = 0; i < contrib.length; i++) rest += Math.max(0, contrib[i].total - prev);
    if (rest > 0 && pots.length) pots[pots.length - 1].amount += rest;
    return pots;
  }

  // ---------- Blackjack ----------
  function bjTotal(cards) {
    var t = 0, aces = 0;
    for (var i = 0; i < cards.length; i++) { var r = cards[i][0]; if (r === 'A') { aces++; t += 11; } else if ('TJQK'.indexOf(r) >= 0) t += 10; else t += +r; }
    while (t > 21 && aces > 0) { t -= 10; aces--; }
    return { total: t, soft: aces > 0 };
  }

  var CARDS = { SUITS: SUITS, SYM: SYM, SNAME: SNAME, RANKS: RANKS, RDISP: RDISP, deck: deck, shuffle: shuffle, rnd: rnd, rv: rv, disp: disp, red: red,
    html: html, CSS: CSS, injectCss: injectCss, best: best, cmp: cmp, sidePots: sidePots, bjTotal: bjTotal };
  if (typeof module !== 'undefined' && module.exports) module.exports = CARDS; else root.CARDS = CARDS;
})(typeof window !== 'undefined' ? window : this);
