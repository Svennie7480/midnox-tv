// midnox Media – midnox TV: Fernsehsender aus der eigenen Emby-/Jellyfin-Sammlung.
// Jeder Kanal hat einen festen Wochenplan (gleicher Zufall für alle Geräte) – man schaltet ein und es läuft schon etwas.
'use strict';
const MXG = 'midnox TV', MXF = 'midnox Filmreihen', MXS = 'midnox Serien', MXA = 'midnox Stars';
/** Disney-Zeichentrick-Klassiker (deutsche und englische Titel) */
const DISNEY_CLASSIC = new RegExp('^(' + ['schneewittchen', 'snow white', 'pinocchio', 'fantasia', 'dumbo', 'bambi', 'cinderella', 'aschenputtel', 'alice im wunderland', 'alice in wonderland', 'peter pan', 'susi und strolch', 'lady and the tramp',
  'dornröschen', 'sleeping beauty', '101 dalmatiner', '101 dalmatians', 'die hexe und der zauberer', 'the sword in the stone', 'das dschungelbuch', 'the jungle book', 'aristocats', 'robin hood', 'winnie puuh', 'winnie the pooh', 'die vielen abenteuer von winnie',
  'bernard und bianca', 'the rescuers', 'cap und capper', 'the fox and the hound', 'taran und der zauberkessel', 'the black cauldron', 'basil', 'the great mouse detective', 'oliver & co', 'oliver and company', 'arielle', 'the little mermaid',
  'die schöne und das biest', 'beauty and the beast', 'aladdin', 'der könig der löwen', 'the lion king', 'pocahontas', 'der glöckner von notre dame', 'the hunchback of notre dame', 'hercules', 'mulan', 'tarzan', 'ein königreich für ein lama', "the emperor's new groove",
  'atlantis', 'lilo (&|und|and) stitch', 'der schatzplanet', 'treasure planet', 'bärenbrüder', 'brother bear', 'küss den frosch', 'the princess and the frog', 'rapunzel', 'tangled', 'die eiskönigin', 'frozen', 'vaiana', 'moana', 'zoomania', 'zootopia',
  'encanto', 'baymax', 'big hero 6', 'ralph reichts', 'wreck-it ralph', 'chaos im netz', 'bolt', 'triff die robinsons', 'himmel und huhn', 'chicken little', 'die kühe sind los', 'raya', 'wish', 'mary poppins', 'elliot, das schmunzelmonster', "pete's dragon", 'die tollkühne hexe',
  'bernard & bianca', 'cap & capper'].join('|') + ')\\b', 'i');
/** Pixar-Filme */
const PIXAR = /^(toy story|das große krabbeln|a bug's life|die monster ag|monster ag|monsters,? inc|die monster uni|monsters university|findet nemo|finding nemo|findet dorie|finding dory|die unglaublichen|the incredibles|incredibles|cars\b|ratatouille|wall[·•. -]?e|oben\b|^up$|merida|brave|alles steht kopf|inside out|arlo (&|und) spot|the good dinosaur|coco\b|onward|soul\b|luca\b|rot\b|turning red|lightyear|elemental|elio)/i;
/** Bekannte deutsche Krimireihen – zählen auch, wenn Emby kein Land/Genre hat */
const DE_KRIMI = /tatort|polizeiruf|soko\b|der alte|derrick|der kommissar|rosenheim|hubert (und|ohne) staller|großstadtrevier|wilsberg|bulle von tölz|ein fall für zwei|der staatsanwalt|notruf hafenkante|die chefin|usedom-krimi|nord bei nordwest|friesland|kroatien-krimi|barcelona-krimi|kommissar dupin|der bozen-krimi|steirer|kommissar rex|alarm für cobra|der fahnder|schimanski|stralsund|nachtschicht|ein starkes team|helen dorn|unter verdacht|der kriminalist|sokos|die toten vom bodensee|der zürich-krimi|harter brocken|kommissarin heller|der irland-krimi|marie brand|kommissar marthaler|mord mit aussicht|watzmann ermittelt/i;
const RAW = /\.S\d{1,2}E\d{1,3}\.|\b(1080p|720p|2160p|x26[45]|WEB-?DL|BluRay|AC3D?)\b/i;   // Dateiname statt Titel
const MXTV = {
  ch: [], byId: {}, at0: 0, building: false,
  DEFS: [
    { key: 'serien', name: 'Serien-Marathon', emoji: '📺', type: 'Episode', mode: 'series' },
    { key: 'sitcom', name: 'Sitcom-Kanal', emoji: '😂', type: 'Episode', mode: 'series', sgenres: ['Comedy', 'Komödie', 'Sitcom'] },
    { key: 'kino', name: 'Filmabend', emoji: '🎬', type: 'Movie' },
    { key: 'action', name: 'Action Nonstop', emoji: '💥', type: 'Movie', genres: ['Action', 'Abenteuer', 'Adventure'] },
    { key: 'actionklassik', name: 'Action-Klassiker', emoji: '🔫', type: 'Movie', genres: ['Action'], years: [1970, 1999] },
    { key: 'blockbuster', name: 'Blockbuster-Action', emoji: '💣', type: 'Movie', genres: ['Action'], years: [2010, 2100] },
    { key: 'actionthrill', name: 'Action-Thriller', emoji: '🚨', type: 'Movie', allG: [['Action'], ['Thriller', 'Krimi', 'Crime']] },
    { key: 'actionfun', name: 'Action-Komödie', emoji: '😎', type: 'Movie', allG: [['Action'], ['Komödie', 'Comedy']] },
    { key: 'actionscifi', name: 'Sci-Fi-Action', emoji: '🤖', type: 'Movie', allG: [['Action'], ['Science Fiction', 'Sci-Fi']] },
    { key: 'actionserie', name: 'Action-Serien', emoji: '🧨', type: 'Episode', mode: 'series', sgenres: ['Action'] },
    { key: 'lachen', name: 'Lachkanal', emoji: '🤣', type: 'Movie', genres: ['Komödie', 'Comedy'] },
    { key: 'spannung', name: 'Spannung', emoji: '🔪', type: 'Movie', genres: ['Thriller', 'Krimi', 'Crime', 'Mystery'] },
    { key: 'krimikino', name: 'Krimi-Kino', emoji: '🕵️‍♂️', type: 'Movie', genres: ['Krimi', 'Crime'] },
    { key: 'krimiklassik', name: 'Krimi-Klassiker', emoji: '🎩', type: 'Movie', genres: ['Krimi', 'Crime', 'Mystery'], years: [1900, 1999] },
    { key: 'krimineu', name: 'Neue Krimis', emoji: '🔎', type: 'Movie', genres: ['Krimi', 'Crime'], years: [2010, 2100] },
    { key: 'gangster', name: 'Gangster-Kino', emoji: '🎰', type: 'Movie', allG: [['Krimi', 'Crime'], ['Drama']], title: /mafia|pate|godfather|gangster|scarface|goodfellas|casino|mob\b|narcos|heat\b|departed|irishman/i },
    { key: 'raetsel', name: 'Rätsel-Krimi', emoji: '🧩', type: 'Movie', genres: ['Mystery'] },
    { key: 'dekrimi', name: 'Deutsche Krimi-Filme', emoji: '🚓', type: 'Movie', genres: ['Krimi', 'Crime', 'Thriller'], country: /german|deutschland|ddr|bundesrepublik/i },
    { key: 'grusel', name: 'Gänsehaut', emoji: '👻', type: 'Movie', genres: ['Horror'] },
    { key: 'horrorklassik', name: 'Horror-Klassiker', emoji: '🧛', type: 'Movie', genres: ['Horror'], years: [1900, 1999] },
    { key: 'horrorneu', name: 'Neuer Horror', emoji: '🩸', type: 'Movie', genres: ['Horror'], years: [2010, 2100] },
    { key: 'psycho', name: 'Psycho-Horror', emoji: '🧠', type: 'Movie', allG: [['Horror'], ['Thriller', 'Mystery', 'Psycho']] },
    { key: 'horrorlach', name: 'Horror zum Lachen', emoji: '🤡', type: 'Movie', allG: [['Horror'], ['Komödie', 'Comedy']] },
    { key: 'gruselserie', name: 'Grusel-Serien', emoji: '🕯️', type: 'Episode', mode: 'series', sgenres: ['Horror'] },
    { key: 'scifi', name: 'Sci-Fi & Fantasy', emoji: '🚀', type: 'Movie', genres: ['Science Fiction', 'Sci-Fi', 'Fantasy'] },
    { key: 'herz', name: 'Herzkino', emoji: '💕', type: 'Movie', genres: ['Liebesfilm', 'Romance', 'Romanze'] },
    { key: 'familie', name: 'Familienkino', emoji: '🧸', type: 'Movie', genres: ['Familie', 'Family', 'Animation', 'Kinder', 'Kids'] },
    { key: 'doku', name: 'Doku', emoji: '🌍', type: 'Movie,Episode', genres: ['Dokumentation', 'Documentary', 'Doku'] },
    { key: 'retro', name: '80er & 90er Kino', emoji: '📼', type: 'Movie', years: [1980, 1999] },
    // --- Jahreszeit (nur in der passenden Zeit auf Sendung) ---
    { key: 'xmas', name: 'Weihnachtskanal', emoji: '🎄', type: 'Movie,Episode', months: [11, 12, 1], title: /weihnacht|christmas|xmas|x-mas|nikolaus|santa|grinch|kevin allein|home alone|polar ?express|bescherung|love actually|tatsächlich liebe|\belf\b|klaus|advent|schneemann|snowman|rentier|holiday/i, genres: ['Weihnacht', 'Christmas', 'Holiday'] },
    { key: 'halloween', name: 'Halloween-Nacht', emoji: '🎃', type: 'Movie,Episode', months: [10], title: /halloween|hexe|witch|vampir|zombie|geist|ghost|spuk|haunt|kürbis/i, genres: ['Horror'] },
    // --- Serien nach Art ---
    { key: 'krimiserie', name: 'Krimi-Serien', emoji: '🕵️', type: 'Episode', mode: 'series', sgenres: ['Krimi', 'Crime', 'Mystery', 'Thriller'] },
    { key: 'dramaserie', name: 'Serien-Drama', emoji: '🎭', type: 'Episode', mode: 'series', sgenres: ['Drama'] },
    { key: 'animeserie', name: 'Anime-Serien', emoji: '🎌', type: 'Episode', mode: 'series', sgenres: ['Anime'] },
    { key: 'cartoonserie', name: 'Zeichentrick-Serien', emoji: '📺', type: 'Episode', mode: 'series', sgenres: ['Animation', 'Zeichentrick', 'Cartoon'] },
    { key: 'dekrimiserie', name: 'Deutsche Krimis', emoji: '🚔', type: 'Episode', mode: 'series', sgenres: ['Krimi', 'Crime', 'Mystery', 'Thriller'], country: /german|deutschland|ddr|bundesrepublik/i, stitle: DE_KRIMI },
    { key: 'nordicnoir', name: 'Nordic Noir', emoji: '❄️', type: 'Movie,Episode', mode: 'series', genres: ['Krimi', 'Crime', 'Thriller', 'Mystery'], country: /sweden|schweden|denmark|dänemark|norway|norwegen|finland|finnland|iceland|island/i },
    { key: 'ukkrimi', name: 'Britische Krimis', emoji: '🫖', type: 'Movie,Episode', mode: 'series', genres: ['Krimi', 'Crime', 'Mystery'], country: /united kingdom|great britain|großbritannien|england|scotland|schottland|\buk\b/i },
    { key: 'scifiserie', name: 'Serien aus anderen Welten', emoji: '🛸', type: 'Episode', mode: 'series', sgenres: ['Science Fiction', 'Sci-Fi', 'Fantasy'] },
    { key: 'dokuserie', name: 'Doku-Serien', emoji: '🔍', type: 'Episode', mode: 'series', sgenres: ['Dokumentation', 'Documentary', 'Reality'] },
    // --- Filme nach Genre ---
    { key: 'drama', name: 'Großes Drama', emoji: '🎭', type: 'Movie', genres: ['Drama'] },
    { key: 'trick', name: 'Trickfilm-Kanal', emoji: '🖍️', type: 'Movie', genres: ['Animation', 'Zeichentrick', 'Anime'] },
    { key: 'trickklassik', name: 'Zeichentrick-Klassiker', emoji: '🐭', type: 'Movie', genres: ['Animation', 'Zeichentrick'], years: [1900, 2004] },
    { key: 'tricknew', name: 'Neue Animationsfilme', emoji: '🌈', type: 'Movie', genres: ['Animation'], years: [2015, 2100] },
    { key: 'animefilm', name: 'Anime-Filme', emoji: '🗾', type: 'Movie', genres: ['Anime'], studios: /ghibli|toei|madhouse|kyoto animation|production i\.g|bones|mappa|ufotable/i },
    { key: 'trickfam', name: 'Trickfilm für die Kleinen', emoji: '🐣', type: 'Movie', allG: [['Animation'], ['Familie', 'Family', 'Kinder', 'Kids']], maxMin: 100 },
    { key: 'western', name: 'Western', emoji: '🤠', type: 'Movie', genres: ['Western'] },
    { key: 'westernklassik', name: 'Western-Klassiker', emoji: '🐎', type: 'Movie', genres: ['Western'], years: [1900, 1979] },
    { key: 'neowestern', name: 'Neo-Western', emoji: '🌵', type: 'Movie', genres: ['Western'], years: [1990, 2100] },
    { key: 'geschichte', name: 'Krieg & Geschichte', emoji: '⚔️', type: 'Movie', genres: ['Kriegsfilm', 'War', 'Krieg', 'Historie', 'History', 'Geschichte'] },
    { key: 'musik', name: 'Musik & Musical', emoji: '🎵', type: 'Movie', genres: ['Musik', 'Music', 'Musical'] },
    { key: 'helden', name: 'Superhelden', emoji: '🦸', type: 'Movie', studios: /marvel|dc (comics|entertainment|films)|dc studios/i, title: /spider|batman|superman|avengers|x-men|wonder woman|deadpool|iron man|thor\b|captain america|guardians of the galaxy|justice league|aquaman|black panther|doctor strange|ant-man/i },
    { key: 'disneyclassic', name: 'Disney Classics', emoji: '🏰', type: 'Movie',
      // gezeichnete Disney-Meisterwerke: bekannter Titel ODER Disney-Studio – und zwar animiert oder vor 1985 (keine Realverfilmungen von 2016)
      fn: (i, ctx) => (DISNEY_CLASSIC.test(i.Name || '') || ctx.studio(i, /walt disney (animation|productions|feature)|disney animation/i)) && !PIXAR.test(i.Name || '') && !ctx.studio(i, /pixar/i)
        && (ctx.hasG(i.Genres, ['Animation', 'Zeichentrick']) || (i.ProductionYear || 3000) < 1985) && (i.ProductionYear || 0) <= 2009 },   // die gezeichnete Ära bis „Küss den Frosch“
    { key: 'pixar', name: 'Disney Pixar', emoji: '💡', type: 'Movie', fn: (i, ctx) => ctx.studio(i, /pixar/i) || (PIXAR.test(i.Name || '') && ctx.hasG(i.Genres, ['Animation'])) },
    { key: 'disney', name: 'Disney-Kino', emoji: '✨', type: 'Movie', studios: /disney|pixar/i },
    // --- Filme nach Jahr & Länge ---
    { key: 'klassiker', name: 'Filmklassiker', emoji: '🎞️', type: 'Movie', years: [1900, 1979] },
    { key: 'nuller', name: '2000er-Kino', emoji: '💿', type: 'Movie', years: [2000, 2009] },
    { key: 'neu', name: 'Neues Kino', emoji: '✨', type: 'Movie', recentYears: 3 },
    { key: 'frisch', name: 'Frisch eingetroffen', emoji: '🆕', type: 'Movie,Episode', addedDays: 45 },
    { key: 'kurz', name: 'Kurz & knackig', emoji: '⏱️', type: 'Movie', maxMin: 95 },
    { key: 'epos', name: 'Überlänge', emoji: '🏔️', type: 'Movie', minMin: 150 },
    { key: 'zufall', name: 'Zufallskanal', emoji: '🎲', type: 'Movie,Episode' },
  ],
  MIN_ITEMS: 5, MIN_HOURS: 6, MIN_MOVIE: 20 * 60e3, MIN_EP: 5 * 60e3,

  /** Kanäle aus der Bibliothek zusammenstellen (einmal am Tag, im Speicher des Geräts). */
  /** Altersfreigabe → Jahre (FSK-16, DE-16, 16, R, TV-MA …); 0 = unbekannt/frei */
  age(r) {
    if (!r) return 0; const x = String(r).toUpperCase();
    const n = x.match(/(\d{1,2})/); if (n && +n[1] <= 21) return +n[1] >= 18 ? 18 : +n[1];
    if (/NC-?17|^X$|XXX|TV-MA/.test(x)) return 18; if (/^R$|^R\b/.test(x)) return 16; if (/PG-?13|TV-14/.test(x)) return 12; if (/^PG$|TV-PG/.test(x)) return 6;
    return 0;
  },
  PRIME: (20 * 60 + 15) * 60e3, WEEK: 7 * 864e5,
  MAX_SETS: 15, MIN_FR_H: 4,
  /** Bekannte Filmreihen: Filme und Serien gehören zusammen und laufen in Reihenfolge */
  FRANCHISES: [
    { key: 'lotr', name: 'Mittelerde', emoji: '💍', re: /herr der ringe|lord of the rings|hobbit|ringe der macht|rings of power|war of the rohirrim|krieg der rohirrim/i },
    { key: 'hp', name: 'Wizarding World', emoji: '⚡', re: /harry potter|phantastische tierwesen|fantastic beasts|hogwarts/i },
    { key: 'sw', name: 'Star Wars', emoji: '🌌', re: /star wars|krieg der sterne|mandalorian|andor|obi-wan|ahsoka|boba fett|rogue one|solo: a star wars|the clone wars|the bad batch|skeleton crew|acolyte/i },
    { key: 'st', name: 'Star Trek', emoji: '🖖', re: /star trek|raumschiff enterprise/i },
    { key: 'bond', name: 'James Bond 007', emoji: '🍸', re: /james bond|\b007\b|james bond jagt dr\. no|liebesgrüße aus moskau|goldfinger|feuerball|man lebt nur zweimal|im geheimdienst ihrer majestät|diamantenfieber|leben und sterben lassen|der mann mit dem goldenen colt|der spion, der mich liebte|moonraker|in tödlicher mission|octopussy|im angesicht des todes|der hauch des todes|lizenz zum töten|goldeneye|der morgen stirbt nie|die welt ist nicht genug|stirb an einem anderen tag|casino royale|ein quantum trost|skyfall|spectre|keine zeit zu sterben/i },
    { key: 'ff', name: 'Fast & Furious', emoji: '🏎️', re: /fast (&|and) (the )?furious|furious [0-9]|fast five|tokyo drift|hobbs (&|and) shaw|\bfast x\b|2 fast 2 furious/i },
    { key: 'jp', name: 'Jurassic World', emoji: '🦖', re: /jurassic/i },
    { key: 'mi', name: 'Mission: Impossible', emoji: '🎯', re: /mission: ?impossible/i },
    { key: 'matrix', name: 'Matrix', emoji: '💊', re: /\bmatrix\b|animatrix/i },
    { key: 'term', name: 'Terminator', emoji: '🦾', re: /terminator/i },
    { key: 'alien', name: 'Alien & Predator', emoji: '👽', re: /^aliens?\b|alien(s|³|3)? [–-]|prometheus|alien: covenant|predator|romulus/i },
    { key: 'indy', name: 'Indiana Jones', emoji: '🗿', re: /indiana jones|jäger des verlorenen schatzes/i },
    { key: 'potc', name: 'Fluch der Karibik', emoji: '🏴‍☠️', re: /fluch der karibik|pirates of the caribbean/i },
    { key: 'rocky', name: 'Rocky & Creed', emoji: '🥊', re: /\brocky\b|\bcreed\b/i },
    { key: 'diehard', name: 'Stirb langsam', emoji: '🏢', re: /stirb langsam|die hard/i },
    { key: 'bttf', name: 'Zurück in die Zukunft', emoji: '⏰', re: /zurück in die zukunft|back to the future/i },
    { key: 'hg', name: 'Die Tribute von Panem', emoji: '🏹', re: /tribute von panem|hunger games/i },
    { key: 'twi', name: 'Twilight', emoji: '🧛‍♀️', re: /twilight(?! zone)|biss zum|bis\(s\) zum/i },
    { key: 'apes', name: 'Planet der Affen', emoji: '🦍', re: /planet der affen|planet of the apes/i },
    { key: 'tf', name: 'Transformers', emoji: '🤖', re: /transformers|bumblebee/i },
    { key: 'jw', name: 'John Wick', emoji: '🐶', re: /john wick/i },
    { key: 'conj', name: 'Conjuring-Universum', emoji: '🪆', re: /conjuring|annabelle|the nun\b|la llorona/i },
    { key: 'saw', name: 'Saw', emoji: '🪚', re: /^saw\b|jigsaw|spiral/i },
    { key: 'scream', name: 'Scream', emoji: '📞', re: /^scream\b/i },
    { key: 'shrek', name: 'Shrek', emoji: '🧅', re: /shrek|der gestiefelte kater|puss in boots/i },
    { key: 'toy', name: 'Toy Story', emoji: '🤠', re: /toy story|lightyear/i },
    { key: 'ice', name: 'Ice Age', emoji: '🦣', re: /ice age/i },
    { key: 'minions', name: 'Minions', emoji: '🍌', re: /minions|ich - einfach unverbesserlich|ich – einfach unverbesserlich|despicable me/i },
    { key: 'mcu', name: 'Marvel Cinematic Universe', emoji: '🛡️', re: /avengers|iron man|captain america|thor\b|guardians of the galaxy|ant-man|black panther|doctor strange|spider-man: (homecoming|far from home|no way home)|black widow|shang-chi|eternals|captain marvel|wandavision|loki|hawkeye|falcon and the winter soldier|moon knight|ms\. marvel|she-hulk|secret invasion|agatha|thunderbolts|deadpool (&|and) wolverine/i },
    { key: 'tatort', name: 'Tatort-Sonntag', emoji: '🔦', re: /^tatort\b|polizeiruf 110/i },
  ],
  MAX_SERIES: 10, MAX_STARS: 10, MIN_SER_EPS: 12, MIN_STAR: 6,
  /** Hauptdarsteller je Film (die ersten 5 der Besetzung) – höchstens einmal pro Woche von Emby geholt */
  async stars(movies) {
    const ck = 'mxstars_' + EMBY.user.id; let cast = null;
    try { const c = JSON.parse(localStorage.getItem(ck) || 'null'); if (c && Date.now() - c.at < 7 * 864e5) cast = c.cast; } catch (e) {}
    if (!cast) {
      cast = {};
      for (let start = 0; start < 6000; start += 500) {   // in Häppchen, damit Emby und das Gerät nicht ins Schwitzen kommen
        const r = await EMBY.get('/Users/' + EMBY.user.id + '/Items', { IncludeItemTypes: 'Movie', Recursive: true, Fields: 'People', EnableImages: false, EnableUserData: false, StartIndex: start, Limit: 500 });
        for (const i of r.Items || []) cast[i.Id] = (i.People || []).filter(p => p.Type === 'Actor').slice(0, 5).map(p => p.Id + '|' + p.Name);
        if (!r.Items || r.Items.length < 500) break;
      }
      try { localStorage.setItem(ck, JSON.stringify({ at: Date.now(), cast })); } catch (e) {}
    }
    const acc = {}, byId = {}; for (const m of movies) byId[m.Id] = m;
    for (const mid in cast) { const m = byId[mid]; if (!m) continue; for (const pn of cast[mid]) { const k = pn.split('|')[0]; (acc[k] || (acc[k] = { id: k, name: pn.slice(k.length + 1), items: [] })).items.push(m); } }
    return Object.values(acc).filter(a => a.items.length >= MXTV.MIN_STAR && a.items.reduce((s, i) => s + i.RunTimeTicks / 10000, 0) >= MXTV.MIN_HOURS * 3600e3)
      .sort((a, b) => b.items.length - a.items.length).slice(0, MXTV.MAX_STARS);
  },
  /** nacheinander statt gleichzeitig – der Emby-Server bleibt für die Startseite frei */
  async seq(fns) { const out = []; for (const f of fns) out.push(await f()); return out; },
  async init(force) {
    if (!EMBY.user || MXTV.building) return;
    const ck = 'mxtv7_' + EMBY.user.id;   // 7: Altersfreigaben
    if (!force) {
      try {
        const c = JSON.parse(localStorage.getItem(ck) || 'null');
        // vorhandenen Plan sofort nutzen; ist er älter als einen Tag, später in Ruhe neu bauen
        if (c && c.ch) { MXTV.set(c.ch); if (Date.now() - c.at > 24 * 3600e3) setTimeout(() => MXTV.init(true), 3 * 60e3); return; }
      } catch (e) {}
    }
    MXTV.building = true;
    try {
      const f = { Recursive: true, Fields: 'Genres,ProductionYear,RunTimeTicks,SeriesId,SeriesName,ParentIndexNumber,IndexNumber,Studios,DateCreated,ProductionLocations,OfficialRating', EnableImageTypes: 'Primary', ImageTypeLimit: 1, EnableUserData: false, EnableImages: false };   // ohne Beschreibungen: viel weniger Last für den Emby-Server
      const [mv, ep, se] = await MXTV.seq([
        () => EMBY.get('/Users/' + EMBY.user.id + '/Items', Object.assign({ IncludeItemTypes: 'Movie', Limit: 4000 }, f)).catch(() => ({})),
        () => EMBY.get('/Users/' + EMBY.user.id + '/Items', Object.assign({ IncludeItemTypes: 'Episode', Limit: 12000, SortBy: 'SeriesSortName,ParentIndexNumber,IndexNumber' }, f)).catch(() => ({})),
        () => EMBY.get('/Users/' + EMBY.user.id + '/Items', { IncludeItemTypes: 'Series', Recursive: true, Fields: 'Genres,CommunityRating,ProductionLocations', EnableImages: false, Limit: 4000 }).catch(() => ({})),
      ]);
      const sg = {}; for (const s of (se.Items || [])) sg[s.Id] = s.Genres || [];
      const slim = (i) => ({ i: i.Id, n: MXTV.clean(i), s: i.Type === 'Episode' ? (i.SeriesName || '') + ' · S' + (i.ParentIndexNumber || 0) + 'E' + (i.IndexNumber || 0) : (i.ProductionYear ? String(i.ProductionYear) : ''), o: (i.Overview || '').slice(0, 220), d: Math.round((i.RunTimeTicks || 0) / 10000), g: i.Type === 'Episode' ? i.SeriesId : '', t: i.Type === 'Episode' ? 'e' : 'm', r: MXTV.age(i.OfficialRating) });
      const movies = (mv.Items || []).filter(i => i.RunTimeTicks > MXTV.MIN_MOVIE * 1e4), eps = (ep.Items || []).filter(i => i.RunTimeTicks > MXTV.MIN_EP * 1e4);
      const hasG = (gs, want) => (gs || []).some(g => want.some(w => g.toLowerCase().indexOf(w.toLowerCase()) >= 0));
      const out = [];
      const now = new Date(), month = now.getMonth() + 1, year = now.getFullYear();
      const studio = (i, re) => (i.Studios || []).some(x => re.test(x.Name || ''));
      const ctx = { studio, hasG };
      const sname = {}, sloc = {}; for (const s2 of (se.Items || [])) { sname[s2.Id] = s2.Name || ''; sloc[s2.Id] = s2.ProductionLocations || []; }
      for (const d of MXTV.DEFS) {
        if (d.months && !d.months.includes(month)) continue;                                   // Jahreszeit-Kanäle nur in ihrer Zeit
        const movieOk = (i) => {
          const min = i.RunTimeTicks / 6e8;
          if (d.years && !(i.ProductionYear >= d.years[0] && i.ProductionYear <= d.years[1])) return false;
          if (d.recentYears && !(i.ProductionYear >= year - d.recentYears + 1)) return false;
          if (d.maxMin && min > d.maxMin) return false;
          if (d.minMin && min < d.minMin) return false;
          if (d.addedDays && !(i.DateCreated && now - new Date(i.DateCreated) < d.addedDays * 864e5)) return false;
          if (d.fn) return d.fn(i, ctx);
          if (d.allG && !d.allG.every(g => hasG(i.Genres, g))) return false;                  // z. B. Action UND Komödie
          if (d.country && !(i.ProductionLocations || []).some(x => d.country.test(x))) return false;
          // Titel ODER Genre ODER Studio – was angegeben ist, reicht einzeln
          const any = [d.title && d.title.test(i.Name || ''), d.genres && hasG(i.Genres, d.genres), d.studios && studio(i, d.studios)].filter(x => x !== undefined);
          return !any.length || any.some(Boolean);
        };
        const epOk = (i) => {
          if (d.addedDays && !(i.DateCreated && now - new Date(i.DateCreated) < d.addedDays * 864e5)) return false;
          // Genre der Serie zählt (einzelne Folgen haben in Emby oft falsche oder fehlende Genres)
          const sgs = sg[i.SeriesId], gs = sgs && sgs.length ? sgs : (RAW.test(i.Name || '') ? [] : i.Genres);
          if (d.stitle && d.stitle.test(sname[i.SeriesId] || '')) return true;                 // bekannte Reihe (z. B. Tatort) zählt immer
          if (d.country && !(sloc[i.SeriesId] || []).some(x => d.country.test(x))) return false;
          if (d.sgenres && !hasG(sg[i.SeriesId], d.sgenres)) return false;
          if (d.allG && !d.allG.every(g => hasG(gs, g))) return false;
          if (d.title) return d.title.test(i.Name || '') || d.title.test(sname[i.SeriesId] || '') || (d.key !== 'halloween' && !!d.genres && hasG(gs, d.genres));   // z. B. Weihnachtsfolgen
          return !d.genres || hasG(gs, d.genres);
        };
        if (d.studios && !d.title && d.type === 'Movie' && !movies.some(i => i.Studios)) continue;
        let pool = [];
        if (d.type.indexOf('Movie') >= 0) pool = pool.concat(movies.filter(movieOk));
        if (d.type.indexOf('Episode') >= 0) pool = pool.concat(eps.filter(epOk));
        const minItems = d.months ? 3 : MXTV.MIN_ITEMS, minH = d.months || d.addedDays ? 3 : MXTV.MIN_HOURS;
        const total = pool.reduce((a, i) => a + i.RunTimeTicks / 10000, 0);
        if (pool.length < minItems || total < minH * 3600e3) continue;
        // kein Doppel: Kanal weglassen, wenn er fast dasselbe zeigt wie ein schon vorhandener
        const ids = new Set(pool.map(i => i.Id));
        if (out.some(c => { let same = 0; for (const it of c.items) if (ids.has(it.i)) same++; return same >= ids.size * 0.85 && same >= c.items.length * 0.85; })) continue;
        out.push({ key: d.key, name: d.name, emoji: d.emoji, mode: d.mode || '', items: pool.map(slim) });
      }
      // ---- Filmreihen: bekannte Reihen per Titel (Filme + Serien, in Reihenfolge), danach eigene Emby-Sammlungen ----
      const fr = [];
      for (const f of MXTV.FRANCHISES) {
        const mv2 = movies.filter(i => f.re.test(i.Name || '') && !(f.not && f.not.test(i.Name || ''))).sort((a, b) => (a.ProductionYear || 0) - (b.ProductionYear || 0) || (a.PremiereDate || '').localeCompare(b.PremiereDate || ''));
        const ep2 = eps.filter(i => f.re.test(sname[i.SeriesId] || i.SeriesName || ''));
        const pool = mv2.concat(ep2);
        if (pool.length < 3 || pool.reduce((a, i) => a + i.RunTimeTicks / 10000, 0) < MXTV.MIN_FR_H * 3600e3) continue;
        fr.push({ key: 'f-' + f.key, name: f.name, emoji: f.emoji, mode: 'order', grp: MXF, items: pool.map(slim) });
      }
      try {
        const taken = new Set(); for (const c of fr) for (const it of c.items) taken.add(it.i);
        const mById = {}; for (const m of movies) mById[m.Id] = m;
        const bs = await EMBY.get('/Users/' + EMBY.user.id + '/Items', { IncludeItemTypes: 'BoxSet', Recursive: true, Fields: 'ChildCount', EnableImages: false, EnableUserData: false, SortBy: 'SortName', Limit: 500 });
        const sets = (bs.Items || []).filter(b => (b.ChildCount || 0) >= 3).sort((a, b) => (b.ChildCount || 0) - (a.ChildCount || 0)).slice(0, MXTV.MAX_SETS * 2);
        for (const b of sets) {
          if (fr.filter(c => c.boxset).length >= MXTV.MAX_SETS) break;
          const ch = await EMBY.get('/Users/' + EMBY.user.id + '/Items', { ParentId: b.Id, Fields: 'ProductionYear,RunTimeTicks', EnableImages: false, EnableUserData: false, SortBy: 'ProductionYear,PremiereDate,SortName' });
          const items = (ch.Items || []).map(x => mById[x.Id]).filter(Boolean);
          if (items.length < 3 || items.filter(x => taken.has(x.Id)).length >= items.length / 2) continue;   // schon als bekannte Reihe da
          if (items.reduce((a, i) => a + i.RunTimeTicks / 10000, 0) < MXTV.MIN_FR_H * 3600e3) continue;
          items.forEach(x => taken.add(x.Id));
          fr.push({ key: 'b-' + b.Id, name: (b.Name || '').replace(/\s*[-–:]?\s*(Collection|Filmreihe|Sammlung|Reihe|Saga)$/i, '').trim() || b.Name, emoji: '🎞️', mode: 'order', grp: MXF, boxset: true, items: items.map(slim) });
        }
      } catch (e) {}
      out.push(...fr);
      // ---- Beliebteste Serien rund um die Uhr: Favoriten, viel Gesehenes und gut Bewertetes zuerst ----
      const byS = {}; for (const i of eps) (byS[i.SeriesId] || (byS[i.SeriesId] = [])).push(i);
      const sScore = (x) => { const ud = x.UserData || {}, n = (byS[x.Id] || []).length, unpl = ud.UnplayedItemCount == null ? n : ud.UnplayedItemCount, seen = n ? Math.max(0, n - unpl) / n : 0;
        return (ud.IsFavorite ? 4 : 0) + (seen > 0 ? 2 + seen * 2 : 0) + (x.CommunityRating || 6) / 3 + Math.min(1, n / 100); };
      const topS = (se.Items || []).filter(x => (byS[x.Id] || []).length >= MXTV.MIN_SER_EPS && byS[x.Id].reduce((a, i) => a + i.RunTimeTicks / 10000, 0) >= MXTV.MIN_HOURS * 3600e3).sort((a, b) => sScore(b) - sScore(a)).slice(0, MXTV.MAX_SERIES);
      for (const x of topS) out.push({ key: 's-' + x.Id, name: x.Name + ' nonstop', emoji: '🔁', mode: 'series', grp: MXS, items: byS[x.Id].map(slim) });
      // ---- Schauspieler: Kino mit den Stars, die in deiner Sammlung am häufigsten vorkommen ----
      try {
        const stars = await MXTV.stars(movies);
        for (const a of stars) out.push({ key: 'a-' + a.id, name: a.name, emoji: '⭐', grp: MXA, items: a.items.map(slim) });
      } catch (e) {}
      // „Filmabend“ weglassen, wenn es nur einen Filmkanal gäbe, der identisch wäre
      MXTV.set(out);
      try { localStorage.setItem(ck, JSON.stringify({ at: Date.now(), ch: out })); }   // große Sammlungen: Speicher kann voll sein
      catch (e) { try { localStorage.setItem(ck, JSON.stringify({ at: Date.now(), ch: out.map(c => Object.assign({}, c, { items: c.items.map(i => Object.assign({}, i, { o: '' })) })) })); } catch (x) {} }
      if (window.LIVE) { LIVE.load(); LIVE.refresh(); }
    } finally { MXTV.building = false; }
  },
  /** Nicht erkannte Dateien („Die.Simpsons.S33E21.German…1080p“) bekommen einen lesbaren Titel */
  clean(i) {
    const n = i.Name || '';
    if (!RAW.test(n)) return n;
    if (i.Type === 'Episode') {
      const m = n.match(/^(.*?)[. _-]+S(\d{1,2})E(\d{1,3})/i), ser = i.SeriesName || (m ? m[1].replace(/[._]/g, ' ').trim() : '');
      return (ser ? ser + ' · ' : '') + 'Folge ' + (i.IndexNumber || (m ? +m[3] : ''));
    }
    return n.replace(/[._]/g, ' ').replace(/\b(19|20)\d\d\b.*$|\b(German|DL|AC3D?|1080p|720p|2160p|WEB|x26[45]|BluRay).*$/i, '').trim() || n;
  },
  ver: 0,
  set(list) {
    MXTV.ver++;
    MXTV.ch = list.map((c, k) => Object.assign({ id: 'mx:' + c.key, num: '', group: c.grp || MXG, logos: [], src: 'mx' }, c));
    for (const c of MXTV.ch) {   // Filmkanäle bekommen einen 20:15-Spielfilm
      const mv = c.items.filter(i => i.t !== 'e' && i.d >= 60 * 60e3).length;
      c.prime = c.mode !== 'series' && c.items.length >= 3 && mv >= c.items.length * 0.6;
    }
    // Vorabendprogramm zum Auffüllen bis 20:15: kurze Sitcom-Folgen (sonst Serienfolgen bis 50 Min.)
    const sit = list.find(c => c.key === 'sitcom') || list.find(c => c.key === 'serien');
    MXTV.short = sit ? sit.items.filter(i => i.d >= 12 * 60e3 && i.d <= 50 * 60e3 && (i.r || 0) < 16) : [];
    MXTV.byId = {}; for (const c of MXTV.ch) { MXTV.byId[c.id] = c; c._plan = null; }
  },
  // ---- Fester Wochenplan ----
  week(t) { const d = new Date(t); d.setHours(0, 0, 0, 0); const wd = (d.getDay() + 6) % 7; d.setDate(d.getDate() - wd); return d.getTime(); },
  rng(seed) { let s = seed >>> 0 || 1; return () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; }; },
  hash(str) { let x = 2166136261; for (let i = 0; i < str.length; i++) { x ^= str.charCodeAt(i); x = Math.imul(x, 16777619); } return x >>> 0; },
  plan(c, wk) {
    if (c._plan && c._plan.wk === wk) return c._plan;
    const r = MXTV.rng(MXTV.hash(c.key + ':' + wk));
    let seq;
    if (c.mode === 'series') {   // Serienblöcke: Serien gemischt, Folgen in Reihenfolge, je Block 3 Folgen
      const by = {}; const order = [];
      for (const it of c.items) { if (!by[it.g]) { by[it.g] = []; order.push(it.g); } by[it.g].push(it); }
      for (let i = order.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [order[i], order[j]] = [order[j], order[i]]; }
      const pos = {}; for (const g of order) pos[g] = Math.floor(r() * by[g].length);
      seq = []; let guard = 0;
      while (seq.length < c.items.length && guard++ < 100000) for (const g of order) { for (let k = 0; k < 3; k++) { seq.push(by[g][pos[g] % by[g].length]); pos[g]++; } if (seq.length >= c.items.length) break; }
    } else if (c.mode === 'order') {   // Filmreihe: immer in Reihenfolge, jede Woche ab einer anderen Stelle
      const st = Math.floor(r() * c.items.length);
      seq = c.items.slice(st).concat(c.items.slice(0, st));
    } else {
      seq = c.items.slice();
      for (let i = seq.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [seq[i], seq[j]] = [seq[j], seq[i]]; }
    }
    if (!c.prime) { const starts = []; let t = 0; for (const it of seq) { starts.push(t); t += it.d; } c._plan = { wk, seq, starts, total: t, prime: {} }; return c._plan; }
    // ---- Filmkanal: Tag für Tag, der Hauptfilm beginnt pünktlich um 20:15 ----
    const out = [], starts = [], prime = {}; let t = 0, idx = 0, guard = 0;
    const sh = MXTV.short.slice(); for (let i = sh.length - 1; i > 0; i--) { const j = Math.floor(r() * (i + 1)); [sh[i], sh[j]] = [sh[j], sh[i]]; }
    let si = 0;
    const put = (it) => { starts.push(t); out.push(it); t += it.d; };
    for (let day = 0; day < 7; day++) {
      const A = day * 864e5 + MXTV.PRIME;
      while (t < A && guard++ < 5000) {
        const it = seq[idx % seq.length];
        if (t + it.d <= A + 2 * 60e3) { put(it); idx++; continue; }
        let gap = A - t;
        if (gap > 20 * 60e3 && !sh.length) { put(it); idx++; break; }   // nichts Kurzes da: 20:15 heute auslassen
        // Lücke mit kurzen Folgen füllen, Rest = Programmvorschau
        let tries = 0;
        while (gap >= 12 * 60e3 && sh.length && tries < sh.length) { const f = sh[si++ % sh.length]; tries++; if (f.d <= gap) { put(Object.assign({ fill: true }, f)); gap = A - t; tries = 0; } }
        if (gap > 0) put({ i: null, pause: true, n: 'midnox Vorschau', s: '', o: '', d: gap });
        break;
      }
      if (t >= A - 1 && t <= A + 2 * 60e3) prime[out.length] = true;   // nächster Eintrag ist der 20:15-Film
    }
    while (t < MXTV.WEEK && guard++ < 8000) { put(seq[idx % seq.length]); idx++; }
    c._plan = { wk, seq: out, starts, total: MXTV.WEEK, prime };
    return c._plan;
  },
  /** Was läuft auf Kanal c zur Zeit t? → { it, start, stop } */
  at(c, t) {
    const wk = MXTV.week(t), p = MXTV.plan(c, wk); if (!p.total) return null;
    const off = (t - wk) % p.total;
    let lo = 0, hi = p.starts.length - 1;
    while (lo < hi) { const m = (lo + hi + 1) >> 1; if (p.starts[m] <= off) lo = m; else hi = m - 1; }
    const base = t - off; const it = p.seq[lo];
    return { it, start: base + p.starts[lo], stop: base + p.starts[lo] + it.d, prime: !!p.prime[lo], next: p.seq[lo + 1] || p.seq[0] };
  },
  progs(id, from, to) {
    const c = MXTV.byId[id]; if (!c) return [];
    const out = []; let t = from, guard = 0;
    while (t < to && guard++ < 4000) {
      const a = MXTV.at(c, t); if (!a) break;
      const desc = a.it.pause ? 'Gleich: ' + (a.next && a.next.n || '') : (a.prime ? '20:15-Spielfilm · ' : '') + (a.it.fill ? 'Vorabendserie · ' : '') + (a.it.s || '') + (a.it.s && a.it.o ? ' – ' : '') + (a.it.o || '');
      out.push([a.start, a.stop, a.it.pause ? 'midnox Vorschau' : a.it.n, desc, { id: a.it.i, r: a.it.r || 0, prime: a.prime, pause: !!a.it.pause, fill: !!a.it.fill }]); t = a.stop + 1;
    }
    return out;
  },
  // ---- Jugendschutz: FSK 16 erst ab 22 Uhr, FSK 18 erst ab 23 Uhr (bis 6 Uhr) – sonst PIN ----
  unlockUntil: 0,
  locked(it) {
    const S = EMBY.settings(); if (!S.fskOn || !it || !it.r || it.r < 16 || Date.now() < MXTV.unlockUntil) return false;
    const h = new Date().getHours(); return it.r >= 18 ? (h >= 6 && h < 23) : (h >= 6 && h < 22);
  },
  // ---- Abspielen (über den Live-Player) ----
  async tune(id, t) {
    const c = MXTV.byId[id]; if (!c) throw new Error('Kanal nicht gefunden');
    const now = Math.max(Date.now(), t || 0), a = MXTV.at(c, now); if (!a) throw new Error('Kanal ist leer');
    const off = Math.max(0, now - a.start);
    if (a.it.pause) return { pause: true, off, start: a.start, stop: a.stop, it: a.it, next: a.next };
    if (MXTV.locked(a.it)) return { locked: true, off, start: a.start, stop: a.stop, it: a.it, next: a.next, prime: a.prime };
    const item = { Id: a.it.i, MediaType: 'Video', RunTimeTicks: a.it.d * 10000 };
    const info = await EMBY.playbackInfo(item, { startMs: off });
    return { info, off, start: a.start, stop: a.stop, it: a.it, next: a.next, prime: a.prime };
  },
};
window.MXTV = MXTV;
