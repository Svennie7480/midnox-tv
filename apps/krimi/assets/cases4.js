// midnox Krimi-Abend – Fälle aus der Heimat: Bad Schwartau, Stockelsdorf, Lübeck, Hannover (2×).
// Alle Personen und Firmen sind frei erfunden.
(function(){
const add = (c, d) => { c.solo = true; window.CASES.push(c); window.DETECTIVE[c.id] = d; };

// ===================================================================================
add({
  id: 'konfituere', icon: '🍓', title: 'Bittersüß', tag: 'Bad Schwartau, Sommer 1956',
  blurb: 'Nachtschicht in der Konfitürenfabrik: Der Fabrikant stürzt von der Galerie über den Kochkesseln. Das Geländer hat nachgegeben – ganz von allein?',
  victim: 'Wilhelm Lindemann, 64, Inhaber der Konfitürenfabrik Lindemann',
  intro: [
    'Bad Schwartau im Juli 1956. Es ist Erdbeerzeit, und über der ganzen Stadt liegt der süße Duft aus der Konfitürenfabrik Lindemann. Die Kessel laufen Tag und Nacht.',
    'Jeden Abend um halb elf macht der alte Wilhelm Lindemann seinen Rundgang. Er steigt auf die Galerie über den großen Kupferkesseln und prüft Kessel drei. Seit dreißig Jahren. Das weiß in der Fabrik jeder.',
    'Heute Abend um 22 Uhr 40 findet ihn die Köchin Margot Hinz unter der Galerie. Ein Stück Geländer ist weggebrochen. Lindemann ist tot.',
    'Ein Arbeitsunfall, sagen die Leute. Doch die Schrauben des Geländers fehlen. Ermittelt!'
  ],
  chars: [
    { name: 'Heinz Brockmann', emoji: '🎩', role: 'Teilhaber der Fabrik', g: 'm', guilty: true },
    { name: 'Erna Lindemann', emoji: '🧮', role: 'Schwester des Opfers, Buchhalterin', g: 'f' },
    { name: 'Paul Schulte', emoji: '🛠️', role: 'Werkmeister', g: 'm' },
    { name: 'Margot Hinz', emoji: '🍓', role: 'Köchin in der Nachtschicht', g: 'f' },
    { name: 'Fritz Oldag', emoji: '🔦', role: 'Nachtwächter', g: 'm' },
    { name: 'Dr. Inge Thal', emoji: '🧪', role: 'Lebensmittelchemikerin', g: 'f' }
  ],
  acts: [
    { title: 'Am Kessel drei',
      text: 'Die Polizei aus Lübeck steht in der Kochhalle. Über Kessel drei klafft eine Lücke im Geländer der Galerie. Die Schraubenlöcher sind leer. Daneben hängt das Inspektionsbuch, in das Lindemann jeden Abend seine Runde eintrug. Und auf der Eisentreppe kleben rote Fußspuren.',
      evidence: [
        { ic: '🚧', name: 'Gebrochenes Geländer', text: 'Ein Stück fehlt. Die Schrauben sind verschwunden.' },
        { ic: '📘', name: 'Inspektionsbuch', text: 'Jeden Abend um 22:30 Uhr: „Kessel 3 geprüft – W. L.“ Seit 1926.' },
        { ic: '👞', name: 'Rote Fußspuren', text: 'Auf der Eisentreppe zur Galerie. Kirschsirup – angetrocknet.' }
      ] },
    { title: 'Der Vertrag',
      text: 'Im Büro des Fabrikanten liegt der Gesellschaftervertrag der Firma im offenen Schreibtisch. Daneben ein Brief eines großen Hamburger Konzerns. Und der Werkmeister hat dem Chef erst gestern einen wütenden Brief geschrieben.',
      evidence: [
        { ic: '📜', name: 'Gesellschaftervertrag', text: 'Die Fabrik gehört Lindemann und Brockmann je zur Hälfte.' },
        { ic: '🏢', name: 'Brief aus Hamburg', text: 'Ein Kaufangebot für die ganze Fabrik. Lindemann hat es abgelehnt.' },
        { ic: '✉️', name: 'Brief des Werkmeisters', text: '„Wenn die Überstunden nicht bezahlt werden, legt die Schicht die Arbeit nieder!“' }
      ] },
    { title: 'Der Schraubenschlüssel',
      text: 'Die Polizei durchsucht die Autos auf dem Fabrikhof. In einem Kofferraum liegt ein schwerer Schraubenschlüssel. Der Nachtwächter legt sein Wächterbuch vor. Und eine Reinigung in der Lübecker Straße meldet sich bei der Polizei.',
      evidence: [
        { ic: '🔧', name: 'Schraubenschlüssel', text: 'Aus dem Kofferraum eines schwarzen Mercedes auf dem Fabrikhof.' },
        { ic: '📗', name: 'Wächterbuch', text: 'Wer hat die Fabrik wann betreten und verlassen?' },
        { ic: '👖', name: 'Hose in der Reinigung', text: 'Heute Mittag abgegeben. Mit roten Flecken an den Knien.' }
      ] }
  ],
  solution: [
    'Der Täter ist Heinz Brockmann, der Teilhaber.',
    'Ein Hamburger Konzern wollte die Fabrik für drei Millionen Mark kaufen. Brockmann wollte verkaufen, Lindemann nicht. Laut Gesellschaftervertrag fällt die Hälfte eines verstorbenen Teilhabers an den anderen – mit Lindemanns Tod gehörte Brockmann die ganze Fabrik.',
    'Am Abend, als die Tagschicht ging, stieg Brockmann auf die Galerie und lockerte die Schrauben am Geländer über Kessel drei. Er wusste, dass Lindemann sich dort jeden Abend um halb elf anlehnt. Am Abend saß er dann mit einem Alibi im Kurpark. Doch der Kirschsirup auf seiner Hose und der Nachtwächter verrieten ihn.'
  ],
  keys: [ 'Wächterbuch: Brockmann um 18:35 Uhr noch in der Fabrik', 'Schraubenschlüssel mit Sirupresten', 'Kirschsirup an seiner Hose', 'Vertrag: Die ganze Fabrik fällt ihm zu' ]
}, {
  talk: [
    [ 'Wilhelm war mein Partner seit zwanzig Jahren. Ich war heute Abend beim Kurkonzert im Kurpark, ab neun Uhr. Das können hundert Leute bezeugen.',
      'Ja, ich wollte verkaufen. Aber wer will das nicht bei drei Millionen? Fragen Sie lieber den Werkmeister, der hat Wilhelm gestern offen gedroht.',
      'Den Schraubenschlüssel habe ich immer im Auto, für Reifenpannen. Und die Hose? Ich habe mich an der Rampe an einer Kiste gerieben, das ist alles.' ],
    [ 'Ich führe die Bücher, seit unser Vater die Fabrik gegründet hat. Wilhelm war ein sturer, aber ehrlicher Mann.',
      'Wilhelm hat das Angebot aus Hamburg gestern abgelehnt. Heinz ist danach mit hochrotem Kopf aus dem Büro gestürmt: „Das wirst du bereuen!“',
      'Ich habe den Vertrag selbst aufgesetzt. Stirbt einer der beiden, gehört dem anderen die ganze Fabrik. Wir Geschwister bekommen nichts.' ],
    [ 'Ich habe Wilhelm gestern einen wütenden Brief geschrieben, das stimmt. Die Leute schuften für Überstunden, die keiner bezahlt.',
      'Heute Abend war ich mit der ganzen Spätschicht im Gewerkschaftshaus, von acht bis elf. Wir haben über einen Streik abgestimmt.',
      'Das Geländer habe ich vor zwei Wochen selbst geprüft. Die Schrauben waren fest. Die lösen sich nicht von allein – das braucht einen großen Schraubenschlüssel.' ],
    [ 'Ich habe ihn gefunden. Er lag unter der Galerie, neben Kessel drei. Ich habe geschrien, so laut ich konnte.',
      'Auf der Treppe war schon um sieben Kirschsirup – dabei kochen wir heute nur Erdbeeren. Kirsche gab es nur in der Tagschicht, bis halb sechs.',
      'Herr Brockmann kommt sonst nie in die Kochhalle. „Zu klebrig“, sagt er immer. Aber gestern Abend stand er plötzlich unten an der Treppe und hat nach oben geschaut.' ],
    [ 'Ich mache jede Nacht meine Runden und schreibe jeden ins Buch, der kommt oder geht.',
      'Der Chef kam um 22:25 Uhr zu seinem Rundgang, wie jeden Abend. Ich habe nichts Besonderes bemerkt.',
      'Herr Brockmann hat behauptet, er sei um fünf gegangen. Aber in meinem Buch steht: Um 18:35 Uhr verließ er die Fabrik. Er kam aus der Kochhalle und wischte sich die Knie ab.' ],
    [ 'Ich prüfe jede Charge im Labor. Heute Abend war ich bis neun Uhr da und habe Proben gemessen.',
      'Der Sirup auf der Treppe ist Kirschsirup aus der Tagschicht. Er war schon trocken, als ich ihn untersucht habe – mehrere Stunden alt.',
      'An dem Schraubenschlüssel aus dem Mercedes klebt derselbe Kirschsirup. Und die Größe passt genau zu den Geländerschrauben.' ]
  ],
  look: {
    'Gebrochenes Geländer': 'An den Schraublöchern sind frische, blanke Kratzer. Die Schrauben wurden mit Werkzeug gelöst – nicht herausgerissen.',
    'Inspektionsbuch': 'Seit 30 Jahren jeden Abend dieselbe Zeit. Wer in der Fabrik arbeitet, kennt Lindemanns Gewohnheit genau.',
    'Rote Fußspuren': 'Herrenschuhe, feines Leder mit glatter Sohle. Keine Arbeitsstiefel.',
    'Gesellschaftervertrag': 'Paragraf 9: „Verstirbt ein Gesellschafter, so geht sein Anteil auf den anderen über.“',
    'Brief aus Hamburg': '„Wir bieten 3 Millionen Mark. Unser Angebot gilt nur bei Zustimmung beider Gesellschafter.“',
    'Brief des Werkmeisters': 'Der Streik war für Montag geplant. Ein toter Chef hilft Schulte dabei nicht.',
    'Schraubenschlüssel': 'An den Backen: getrockneter Kirschsirup und blanke Metallspäne.',
    'Wächterbuch': '„18:35 Uhr – Herr Brockmann verlässt die Fabrik.“ Er hatte behauptet, um 17 Uhr gegangen zu sein.',
    'Hose in der Reinigung': 'Abgegeben von Herrn Brockmann. Die Flecken an den Knien sind Kirschsirup.'
  },
  motives: [ 'Mit Lindemanns Tod gehörte ihm die ganze Fabrik – und der Verkauf', 'Unbezahlte Überstunden und Streit um den Streik', 'Rache der Schwester für das Erbe', 'Eine verdorbene Charge sollte vertuscht werden' ],
  keys: [ 'Wächterbuch', 'Schraubenschlüssel', 'Hose in der Reinigung', 'Gesellschaftervertrag' ]
});

// ===================================================================================
add({
  id: 'fayence', icon: '🏺', title: 'Kobaltblau', tag: 'Fayence-Manufaktur Stockelsdorf, Winter 1775',
  blurb: 'In der Nacht des großen Brandes liegt der Manufakturherr tot vor dem Ofen. An seinen Händen klebt blaue Farbe.',
  victim: 'Georg Albrecht Lüdemann, 52, Herr der Fayence-Manufaktur',
  intro: [
    'Stockelsdorf vor den Toren Lübecks, im Winter 1775. In der Fayence-Manufaktur wird feinstes Geschirr gebrannt, bemalt mit dem berühmten Stockelsdorfer Blau. Die Farbe dafür, das Kobalt, ist teurer als Gold.',
    'In dieser Nacht läuft der große Brand. Der Ofen muss zwölf Stunden lang glühen, alle zwei Stunden wird nachgelegt.',
    'Um Mitternacht findet der Brennmeister seinen Herrn, Georg Albrecht Lüdemann, tot vor dem Brennofen. Erschlagen mit einer schweren Brennkapsel aus Ton.',
    'Der Schnee liegt hoch, niemand ist in dieser Nacht nach Lübeck gefahren. Der Mörder ist noch in der Manufaktur.'
  ],
  chars: [
    { name: 'Matthias Reese', emoji: '📐', role: 'Faktor, Verwalter der Manufaktur', g: 'm', guilty: true },
    { name: 'Anna Maria Lüdemann', emoji: '🕯️', role: 'Ehefrau des Manufakturherrn', g: 'f' },
    { name: 'Christian Buchwald', emoji: '🖌️', role: 'Obermaler', g: 'm' },
    { name: 'Lene Timm', emoji: '🎨', role: 'Malerin in der Werkstatt', g: 'f' },
    { name: 'Jacob Dreyer', emoji: '🔥', role: 'Brennmeister', g: 'm' },
    { name: 'Dorothea Gerdes', emoji: '🧺', role: 'Händlerin aus Lübeck', g: 'f' }
  ],
  acts: [
    { title: 'Vor dem Brennofen',
      text: 'Der Brennmeister hat die Wachen der Manufaktur geweckt. Neben dem Toten liegt die zerbrochene Brennkapsel. In Lüdemanns Hand steckt ein zerknülltes Blatt, und an seinen Fingern klebt feines blaues Pulver. Das Brennbuch am Ofen verzeichnet jeden, der nachgelegt hat.',
      evidence: [
        { ic: '⚱️', name: 'Zerbrochene Brennkapsel', text: 'Schwerer Ton. Die Tatwaffe.' },
        { ic: '📃', name: 'Zerknülltes Blatt', text: 'In der Hand des Toten. Eine Liste mit Zahlen.' },
        { ic: '🔵', name: 'Blaues Pulver', text: 'An Lüdemanns Fingern. Kobalt – die kostbarste Farbe der Manufaktur.' }
      ] },
    { title: 'Die Kobaltkammer',
      text: 'Die Kobaltkammer ist verschlossen, aber die Waage darin zeigt seltsame Werte. Im Kontor liegt das Kobaltbuch, in das jede Unze eingetragen wird. Und im Kamin der Malstube findet sich ein halb verbrannter Brief.',
      evidence: [
        { ic: '⚖️', name: 'Kobaltwaage', text: 'Im Vorratsfass fehlen fast zwölf Pfund Kobalt.' },
        { ic: '📖', name: 'Kobaltbuch', text: 'Jede Entnahme ist eingetragen – mit Unterschrift.' },
        { ic: '✉️', name: 'Halb verbrannter Brief', text: 'Aus dem Kamin der Malstube. Ein Siegel aus Kiel.' }
      ] },
    { title: 'Die blauen Manschetten',
      text: 'Am Morgen durchsucht der Amtmann aus Lübeck die Kammern der Bediensteten. Er findet einen Rock mit blauen Flecken, einen Lederbeutel und einen Schlüssel, der nicht dorthin gehört, wo er liegt.',
      evidence: [
        { ic: '🧥', name: 'Rock mit blauen Manschetten', text: 'Gefunden in einer Kammer im Verwalterhaus.' },
        { ic: '👝', name: 'Lederbeutel mit Talern', text: '40 Taler in Kieler Münze. Versteckt unter einer Diele.' },
        { ic: '🗝️', name: 'Zweiter Schlüssel zur Kobaltkammer', text: 'Es sollte nur einen geben – den des Manufakturherrn.' }
      ] }
  ],
  solution: [
    'Der Täter ist Matthias Reese, der Faktor der Manufaktur.',
    'Seit einem Jahr verkaufte er heimlich Kobalt an eine Konkurrenzmanufaktur in Kiel. Dafür hatte er sich einen zweiten Schlüssel zur Kobaltkammer nachmachen lassen und das Kobaltbuch gefälscht.',
    'In der Nacht des großen Brandes hatte Lüdemann die Fehlmengen nachgewogen und eine Liste geschrieben. Er stellte Reese vor dem Ofen zur Rede. Reese erschlug ihn mit einer Brennkapsel – doch das Kobalt an seinen Manschetten und die Kieler Taler unter seiner Diele überführten ihn.'
  ],
  keys: [ 'Kobalt an Reeses Manschetten', 'Kieler Taler unter seiner Diele', 'Gefälschte Einträge im Kobaltbuch', 'Nachgemachter Schlüssel zur Kobaltkammer' ]
}, {
  talk: [
    [ 'Ein entsetzliches Unglück. Ich habe die ganze Nacht im Kontor gesessen und die Löhne für Weihnachten ausgerechnet.',
      'Das Kobaltbuch ist in bester Ordnung. Vielleicht hat der Obermaler zu viel verbraucht – er malt verschwenderisch, das sagt jeder.',
      'Der Rock? Ich verwalte das Kobalt, da wird man eben blau. Und die Taler sind meine Ersparnisse – woher sie stammen, geht niemanden etwas an.' ],
    [ 'Georg war seit Tagen unruhig. Er sagte, in der Manufaktur werde gestohlen. Heute Nacht wollte er wach bleiben und nachsehen.',
      'Um elf Uhr nahm er die Laterne und sagte: „Heute Nacht wiege ich das Kobalt selbst nach. Und dann rede ich mit Reese.“',
      'Der Schlüssel zur Kobaltkammer hing immer an Georgs Gürtel. Er hat ihn niemals aus der Hand gegeben. Einen zweiten durfte es nicht geben.' ],
    [ 'Ich habe mit dem Herrn um meinen Lohn gestritten, ja. Aber ich habe die ganze Nacht in der Malstube gesessen und Teller für den Herzog bemalt.',
      'Ich verbrauche nicht mehr Kobalt als früher. Ich bekomme sogar weniger! Seit einem Jahr gibt mir Reese jede Woche nur halbe Rationen.',
      'Gegen halb zwölf habe ich Reese durchs Fenster der Malstube gesehen. Er kam vom Brennofen, ohne Laterne, und rieb sich die Ärmel ab.' ],
    [ 'Ich male die feinen Blumen auf die Teller. Ich habe bis zehn Uhr gearbeitet und bin dann in meine Kammer gegangen.',
      'Den Brief im Kamin habe ich gefunden, als ich am Morgen Asche holen wollte. Das Siegel kenne ich: Es ist das der Manufaktur in Kiel.',
      'Herr Reese hat vor einem Monat einen Schlosser aus Lübeck kommen lassen. Er sagte, es gehe um seine Truhe. Aber der Schlosser hat lange an der Kobaltkammer gestanden.' ],
    [ 'Ich habe ihn um Mitternacht gefunden, als ich nachlegen wollte. Er lag direkt vor dem Ofenloch.',
      'Ins Brennbuch tragen wir jeden ein, der nachlegt. Um 22 Uhr war ich es, um 23 Uhr der Herr selbst. Er hat mir das Feuer abgenommen, damit ich schlafen kann.',
      'Um zwanzig nach elf hörte ich vom Ofen her laute Stimmen. Die eine war die des Herrn. Die andere sprach leise und schnell – wie Reese, wenn er nervös ist.' ],
    [ 'Ich kaufe Geschirr für meinen Laden in Lübeck. Wegen des Schnees konnte ich heute Abend nicht mehr zurückfahren.',
      'In Kiel erzählt man sich, dass die dortige Manufaktur plötzlich viel Kobaltblau hat. Billiger als in Holland. Woher, weiß niemand.',
      'Ich habe Herrn Reese letzten Monat in Kiel gesehen, in einer Gastwirtschaft am Hafen. Er saß mit dem Faktor der Kieler Manufaktur zusammen.' ]
  ],
  look: {
    'Zerbrochene Brennkapsel': 'An einer Scherbe kleben blaue Fingerabdrücke. Wer zugeschlagen hat, hatte Kobalt an den Händen.',
    'Zerknülltes Blatt': 'In Lüdemanns Schrift: „Kobalt laut Buch: 48 Pfund. Gewogen: 36 Pfund. Es fehlen 12 Pfund! – R.?“',
    'Blaues Pulver': 'Frisches Kobalt. Lüdemann hat in dieser Nacht selbst in der Kobaltkammer gewogen.',
    'Kobaltwaage': 'Die Fehlmenge passt genau zu den Zahlen auf dem zerknüllten Blatt.',
    'Kobaltbuch': 'Seit einem Jahr unterschreibt der Faktor jede Entnahme selbst. Die Zahlen sind mit anderer Tinte nachträglich geändert.',
    'Halb verbrannter Brief': '„… die nächsten zwölf Pfund Kobalt wie besprochen, 40 Taler bei Lieferung …“',
    'Rock mit blauen Manschetten': 'Der Rock gehört dem Faktor. Das Blau an den Manschetten ist frisch – und am Saum klebt Tonstaub vom Ofen.',
    'Lederbeutel mit Talern': 'Genau 40 Taler – die Summe aus dem verbrannten Brief.',
    'Zweiter Schlüssel zur Kobaltkammer': 'Neu und blank. Gefunden in der Westentasche des Faktors. Der Lübecker Schlosser hat ihn angefertigt.'
  },
  motives: [ 'Heimlicher Verkauf von Kobalt an die Kieler Konkurrenz', 'Streit um den Lohn des Obermalers', 'Eifersucht der Ehefrau', 'Schulden der Händlerin' ],
  keys: [ 'Rock mit blauen Manschetten', 'Lederbeutel mit Talern', 'Zweiter Schlüssel zur Kobaltkammer', 'Kobaltbuch' ]
});

// ===================================================================================
add({
  id: 'hanse', icon: '🏛️', title: 'Das Siegel der Hanse', tag: 'Lübeck, Gala im Hansemuseum, heute',
  blurb: 'Nach der Gala liegt der Kurator tot im Gewölbekeller. Das wertvollste Siegel der Ausstellung ist plötzlich eine Fälschung.',
  victim: 'Dr. Felix Brandau, 56, Kurator der Hanse-Ausstellung',
  intro: [
    'Lübeck, ein Freitagabend im Herbst. Im Museum an der Untertrave feiern zweihundert Gäste die Eröffnung der großen Hanse-Ausstellung. Der Star der Schau: ein Stadtsiegel aus dem Jahr 1280, unbezahlbar.',
    'Um 23 Uhr verlassen die letzten Gäste das Haus. Um Mitternacht findet der Wachmann den Kurator Felix Brandau im Gewölbekeller unter der Ausstellung. Er ist die steile Kellertreppe hinuntergestoßen worden.',
    'In seiner Hand hält er eine Lupe. Und oben in der Vitrine liegt das berühmte Siegel – doch eine Expertin erkennt sofort: Das ist nicht das Original.',
    'Wer hat das echte Siegel – und wer hat dafür getötet?'
  ],
  chars: [
    { name: 'Clara Jensen', emoji: '🧑‍🎨', role: 'Restauratorin des Museums', g: 'f', guilty: true },
    { name: 'Dr. Ulrich Kamp', emoji: '🏦', role: 'Museumsdirektor', g: 'm' },
    { name: 'Nina Petrović', emoji: '🥂', role: 'Eventmanagerin der Gala', g: 'f' },
    { name: 'Thorsten Ewert', emoji: '🛡️', role: 'Wachmann', g: 'm' },
    { name: 'Konsul Hendrik van Dijk', emoji: '🧐', role: 'Sammler aus Amsterdam', g: 'm' },
    { name: 'Prof. Marion Albers', emoji: '📚', role: 'Historikerin, Leihgeberin', g: 'f' }
  ],
  acts: [
    { title: 'Die Kellertreppe',
      text: 'Die Spurensicherung untersucht die steile Treppe zum Gewölbekeller. Brandau hielt eine Lupe in der Hand, als er stürzte. Oben im Ausstellungssaal ist die Vitrine mit dem Siegel verschlossen, die Alarmanlage meldet keinen Einbruch. Doch das Siegel darin ist falsch.',
      evidence: [
        { ic: '🔍', name: 'Lupe des Kurators', text: 'In Brandaus Hand. Er hat sich etwas sehr genau angesehen.' },
        { ic: '🖼️', name: 'Vitrine mit dem Siegel', text: 'Verschlossen, keine Einbruchspuren. Und doch liegt eine Kopie darin.' },
        { ic: '🪜', name: 'Kellertreppe', text: 'Steil, aus Backstein. Oben ist das Geländer, Brandau stürzte rückwärts.' }
      ] },
    { title: 'Die Werkstatt',
      text: 'Die Polizei sieht sich in der Restaurierungswerkstatt im Keller um. Dort gibt es Wachs, Formen und feines Werkzeug. Die Videoüberwachung des Kellers hat in der Nacht eine Lücke. Und auf Brandaus Schreibtisch liegt ein Notizzettel.',
      evidence: [
        { ic: '🕯️', name: 'Gussform aus Silikon', text: 'In der Werkstatt, gut versteckt. Mit Spuren von dunklem Harz.' },
        { ic: '📹', name: 'Videoüberwachung', text: 'Die Kamera im Keller war von 23:05 bis 23:35 Uhr ausgeschaltet.' },
        { ic: '🗒️', name: 'Notiz des Kurators', text: 'Mit Kugelschreiber auf einem Gala-Programm.' }
      ] },
    { title: 'Der Käufer',
      text: 'In der Nacht noch sperrt die Polizei die Ausfahrten der Stadt. Ein Gast aus Amsterdam wird befragt. Die Bank meldet eine Überweisung. Und im Wagen der Eventagentur findet sich eine Kiste, die nicht auf der Liste steht.',
      evidence: [
        { ic: '📦', name: 'Kiste im Eventwagen', text: 'Zwischen Gläsern und Tischdecken. Ein kleines, gepolstertes Kästchen.' },
        { ic: '💶', name: 'Überweisung aus Amsterdam', text: '150.000 Euro, vor einer Woche angekündigt, heute fällig.' },
        { ic: '🪪', name: 'Schlüsselkarte', text: 'Wer hat in der Nacht die Werkstatt geöffnet?' }
      ] }
  ],
  solution: [
    'Die Täterin ist Clara Jensen, die Restauratorin.',
    'Sie hatte für einen Sammler aus Amsterdam eine perfekte Kopie des Siegels gegossen. Während der Gala, als sie das Siegel angeblich „für die Beleuchtung“ zurechtrückte, tauschte sie es aus. Das Original versteckte sie im Wagen der Eventagentur, um es unbemerkt hinauszuschmuggeln.',
    'Doch Dr. Brandau bemerkte mit seiner Lupe, dass das Siegel nicht stimmte. Er folgte ihr in den Keller, zu ihrer Werkstatt. Clara schaltete die Kamera aus und stieß ihn die Treppe hinunter. Ihre Schlüsselkarte und die Gussform verrieten sie.'
  ],
  keys: [ 'Schlüsselkarte: Clara öffnete um 23:06 Uhr die Werkstatt', 'Gussform mit Harz in ihrer Werkstatt', 'Notiz des Kurators: „Siegel falsch! – C.J.?“', 'Überweisung aus Amsterdam = Motiv' ]
}, {
  talk: [
    [ 'Felix war ein wunderbarer Mensch. Ich war nach der Gala sofort zu Hause, ich hatte furchtbare Kopfschmerzen.',
      'Gussformen gibt es in jeder Restaurierungswerkstatt, das ist mein Beruf. Und die Kamera? Die fällt dauernd aus, fragen Sie den Wachmann.',
      'Meine Karte? Die muss mir jemand gestohlen haben. Ich hatte sie den ganzen Abend in der Jackentasche an der Garderobe. Und den Konsul kenne ich kaum.' ],
    [ 'Das Siegel ist eine Leihgabe. Wenn es verschwunden ist, ist das Museum ruiniert – und ich auch.',
      'Ich war bis halb zwölf mit Konsul van Dijk in meinem Büro, wir haben über eine Spende gesprochen. Er ist ein großer Freund des Hauses.',
      'Felix hat mir um halb elf gesagt: „Ulrich, mit dem Siegel stimmt etwas nicht. Ich sehe mir das nachher in Ruhe an.“ Ich habe es für Übermüdung gehalten.' ],
    [ 'Ich habe die Gala organisiert. Um elf habe ich mit meinem Team abgebaut, Gläser, Tischdecken, alles in den Wagen.',
      'Die kleine Kiste im Wagen gehört nicht zu uns. Frau Jensen hat sie um 22:50 Uhr gebracht: „Ein Geschenk für einen Gast, bitte mitnehmen.“',
      'Wir sollten den Wagen morgen früh nicht ins Lager fahren, sondern zuerst zu einer Adresse in Travemünde. Das hat Frau Jensen extra bestellt.' ],
    [ 'Ich habe den Kurator um Mitternacht auf meiner Runde gefunden. Unten an der Kellertreppe.',
      'Die Kamera im Keller fällt nie einfach aus. Sie wurde am Schaltkasten in der Werkstatt ausgeschaltet. Dafür braucht man eine Schlüsselkarte für die Werkstatt.',
      'Laut Kartenprotokoll wurde die Werkstatt um 23:06 Uhr geöffnet. Mit der Karte von Frau Jensen. Und um 23:30 Uhr wieder verlassen.' ],
    [ 'Ich sammle Siegel und alte Urkunden. Ich bin als Gast hier, nichts weiter.',
      'Ja, ich habe eine Zahlung angekündigt. Für eine … Restaurierung. Ein Privatauftrag. Frau Jensen ist eine Künstlerin.',
      'Nun gut. Man hat mir ein Siegel angeboten, eine „Dublette aus Privatbesitz“. Dass es das Original aus der Ausstellung ist, habe ich nicht gewusst. So wahr ich hier sitze.' ],
    [ 'Das Siegel gehört meiner Stiftung. Ich habe es dem Museum für die Ausstellung geliehen.',
      'Ich kenne das Original seit vierzig Jahren. Am Rand hat es eine winzige Kerbe aus dem Jahr 1630. Die Kopie in der Vitrine hat sie nicht.',
      'Frau Jensen hat das Siegel vor einem Monat zur Prüfung in ihrer Werkstatt gehabt. Drei ganze Tage lang. Ich fand das damals schon sehr lang.' ]
  ],
  look: {
    'Lupe des Kurators': 'Auf dem Glas: ein winziger Krümel dunkles Harz. Brandau hatte die Kopie in der Hand gehabt.',
    'Vitrine mit dem Siegel': 'Das Schloss lässt sich nur mit dem Restauratorenschlüssel öffnen. Den hatte Clara Jensen am Gala-Abend.',
    'Kellertreppe': 'Oben auf der dritten Stufe: ein kleiner Absatzabdruck – ein schmaler Damenschuh.',
    'Gussform aus Silikon': 'Die Form passt exakt auf die Kopie in der Vitrine. Das Harz ist dasselbe.',
    'Videoüberwachung': 'Die Kamera wurde am Schaltkasten in der Restaurierungswerkstatt ausgeschaltet.',
    'Notiz des Kurators': '„Siegel falsch! Kerbe fehlt. – C.J.? Werkstatt prüfen.“',
    'Kiste im Eventwagen': 'Darin das echte Siegel – mit der kleinen Kerbe von 1630. Auf dem Deckel: Claras Handschrift.',
    'Überweisung aus Amsterdam': 'Empfängerin: Clara Jensen. Verwendungszweck: „Restaurierung privat“.',
    'Schlüsselkarte': '23:06 Uhr: Werkstatt geöffnet mit Karte „Jensen, C.“ – 23:30 Uhr: verlassen.'
  },
  motives: [ 'Das echte Siegel für 150.000 Euro an einen Sammler verkauft', 'Der Ruin des Museums', 'Eine neue Kopie für die eigene Stiftung', 'Ärger um die Gala' ],
  keys: [ 'Schlüsselkarte', 'Gussform aus Silikon', 'Notiz des Kurators', 'Kiste im Eventwagen' ]
});

// ===================================================================================
add({
  id: 'herrenhausen', icon: '⛲', title: 'Tod im Großen Garten', tag: 'Hannover-Herrenhausen, Sommer 1714',
  blurb: 'Beim Sommerfest des Hofes treibt der Hofmarschall im Becken der großen Fontäne. Und die Juwelen der Kurfürstin sind verschwunden.',
  victim: 'Kuno von Heidenreich, 58, Hofmarschall',
  intro: [
    'Hannover im Juni 1714. Im Großen Garten von Herrenhausen feiert der Hof ein prächtiges Sommerfest. Fackeln brennen an den Hecken, Musik weht über die Beete, und die große Fontäne steigt in den Abendhimmel.',
    'Um 23 Uhr wird der Hofmarschall Kuno von Heidenreich vermisst. Eine Stunde später findet ihn ein Gärtnerbursche im Becken der großen Fontäne. Ertrunken.',
    'Zur selben Zeit meldet eine Kammerzofe: Aus dem Schmuckkästchen der Kurfürstin fehlt das kostbare Perlencollier.',
    'Hat der alte Hofmarschall den Dieb ertappt? Ihr habt bis zum Morgengrauen, um es herauszufinden.'
  ],
  chars: [
    { name: 'Anton Kröpke', emoji: '🕴️', role: 'Kammerdiener', g: 'm', guilty: true },
    { name: 'Gräfin Luise von Brede', emoji: '🪭', role: 'Hofdame', g: 'f' },
    { name: 'Johann Georg Pape', emoji: '🌹', role: 'Obergärtner', g: 'm' },
    { name: 'Charlotte Wendland', emoji: '🎀', role: 'Kammerzofe der Kurfürstin', g: 'f' },
    { name: 'Leutnant Moritz von Arnim', emoji: '⚔️', role: 'Offizier der Leibgarde', g: 'm' },
    { name: 'Madame Isabeau Fournier', emoji: '🎻', role: 'Musikerin am Hof', g: 'f' }
  ],
  acts: [
    { title: 'Die große Fontäne',
      text: 'Fackeln erhellen das Becken der großen Fontäne. Der Hofmarschall hat Kratzer an den Händen und einen nassen Abdruck an seinem Kragen, als hätte ihn jemand gepackt. Im Kies am Beckenrand liegt ein Knopf. Und seine Taschenuhr ist stehen geblieben.',
      evidence: [
        { ic: '🕰️', name: 'Taschenuhr des Hofmarschalls', text: 'Stehen geblieben um 23:12 Uhr, als sie ins Wasser kam.' },
        { ic: '🔘', name: 'Silberknopf im Kies', text: 'Am Beckenrand. Ein Livreeknopf mit Krone.' },
        { ic: '🫧', name: 'Nasser Rasen', text: 'Eine nasse Spur führt vom Becken weg – Richtung Irrgarten.' }
      ] },
    { title: 'Das Schmuckkästchen',
      text: 'Im Schloss öffnet die Kammerzofe das Schmuckkästchen der Kurfürstin. Das Perlencollier fehlt, das Schloss ist nicht aufgebrochen. Auf dem Boden des Gemachs liegt ein Fetzen Papier. Und die Wachliste der Leibgarde verzeichnet, wer heute Abend im Schloss war.',
      evidence: [
        { ic: '💎', name: 'Leeres Schmuckkästchen', text: 'Das Perlencollier fehlt. Das Kästchen wurde mit dem Schlüssel geöffnet.' },
        { ic: '📄', name: 'Papierfetzen', text: 'Ein Stück aus einem Brief, im Gemach der Kurfürstin.' },
        { ic: '📋', name: 'Wachliste der Leibgarde', text: 'Wer hat das Schloss zwischen 21 und 23 Uhr betreten?' }
      ] },
    { title: 'Der Irrgarten',
      text: 'Im Morgengrauen durchsuchen die Gärtner den Irrgarten. In einer Hecke tief im Inneren hängt etwas. In einer Gesindekammer finden die Wachen eine nasse Livree. Und ein Brief eines Amsterdamer Juweliers wird abgefangen.',
      evidence: [
        { ic: '🌿', name: 'Fund in der Hecke', text: 'Tief im Irrgarten, in die Buchsbaumhecke gesteckt.' },
        { ic: '👔', name: 'Nasse Livree', text: 'In einer Gesindekammer. Die Ärmel sind noch feucht.' },
        { ic: '✉️', name: 'Brief aus Amsterdam', text: 'An einen Bediensteten des Hofes adressiert.' }
      ] }
  ],
  solution: [
    'Der Täter ist Anton Kröpke, der Kammerdiener.',
    'Er hatte Spielschulden bei einem Amsterdamer Juwelier und wollte sie mit dem Perlencollier der Kurfürstin bezahlen. Während des Festes nahm er den Schlüssel zum Schmuckkästchen, den er als Kammerdiener kannte, und stahl das Collier.',
    'Der Hofmarschall sah ihn mit dem Collier durch den Garten eilen und stellte ihn an der großen Fontäne. Kröpke drückte den alten Mann unter Wasser, bis er sich nicht mehr regte. Dann versteckte er das Collier im Irrgarten. Sein abgerissener Livreeknopf und die nassen Ärmel verrieten ihn.'
  ],
  keys: [ 'Livreeknopf am Beckenrand', 'Nasse Livree mit fehlendem Knopf', 'Collier im Irrgarten', 'Brief des Amsterdamer Juweliers = Motiv' ]
}, {
  talk: [
    [ 'Gnädige Herren, ich habe den ganzen Abend Wein für die Gäste getragen. Ich war nie in der Nähe der Fontäne.',
      'Das Schmuckkästchen? Den Schlüssel trägt die Kammerzofe bei sich, nicht ich. Fragen Sie lieber sie, wo sie um elf Uhr war.',
      'Viele Diener tragen diese Livree. Ich bin heute Abend im Regen über die Wiese gelaufen, darum sind meine Ärmel nass. Und Amsterdam kenne ich nur vom Hörensagen.' ],
    [ 'Ich war mit der Kurfürstin auf der Terrasse und habe dem Feuerwerk zugesehen. Kuno – der Hofmarschall – stand eben noch neben mir.',
      'Um elf Uhr sagte er plötzlich: „Da läuft einer mit etwas Glänzendem in der Hand!“ und eilte die Treppe hinunter. Ich habe ihn nie wiedergesehen.',
      'Der Kammerdiener Kröpke hat beim Kartenspiel mit den Offizieren hohe Summen verloren. Das weiß der halbe Hof.' ],
    [ 'Ich pflege diesen Garten seit zwanzig Jahren. Heute Abend war ich bei den Wasserspielen und habe die Fontäne gesteuert.',
      'Um zehn nach elf habe ich jemanden in Livree vom Becken weglaufen sehen, Richtung Irrgarten. Im Fackellicht sah ich nur, dass er triefnass war.',
      'Im Irrgarten kennt sich kaum einer aus. Nur wir Gärtner – und die Diener, die den Gästen bei den Spielen den Weg zeigen. Kröpke führt die Damen oft hinein.' ],
    [ 'Ich bin die Zofe der Kurfürstin. Den Schlüssel zum Kästchen trage ich an einer Kette um den Hals. Er war die ganze Zeit bei mir.',
      'Aber … es gibt einen zweiten Schlüssel. Er hängt im Ankleidezimmer, für Notfälle. Davon wissen nur die Kammerdiener und ich.',
      'Kröpke hat mich gestern gefragt, wie viel das Perlencollier wohl wert sei. „Nur aus Neugier“, sagte er.' ],
    [ 'Ich kommandiere heute die Wache im Schloss. Jeder, der hinein- oder hinausgeht, wird notiert.',
      'Laut Wachliste ging Anton Kröpke um 22:40 Uhr ins Schloss, „um Wein zu holen“. Er kam um 22:55 Uhr wieder heraus – ohne Wein.',
      'Kröpke schuldet beim Kartenspiel einem Juwelier aus Amsterdam 2000 Taler. Der hat ihm gedroht, sich an den Kurfürsten zu wenden.' ],
    [ 'Ich habe den ganzen Abend mit meinem Ensemble im Heckentheater gespielt. Von dort sieht man die Fontäne.',
      'Gegen elf sah ich zwei Männer am Becken streiten. Der eine trug den Rock des Hofmarschalls, der andere eine Livree. Dann verloschen dort die Fackeln.',
      'Nach dem Fest kam ein Diener mit nassen Ärmeln an unserem Wagen vorbei. Er fragte, ob wir morgen nach Holland reisen. Er wollte mitfahren.' ]
  ],
  look: {
    'Taschenuhr des Hofmarschalls': 'Um 23:12 Uhr ins Wasser gefallen – zwölf Minuten, nachdem er die Terrasse verlassen hat.',
    'Silberknopf im Kies': 'Ein Livreeknopf mit der Krone des Hauses. Am Faden hängt noch ein Stück grüner Stoff.',
    'Nasser Rasen': 'Die Spur stammt von Schnallenschuhen, wie sie die Diener tragen.',
    'Leeres Schmuckkästchen': 'Am Schloss: ein Kratzer vom Notschlüssel aus dem Ankleidezimmer.',
    'Papierfetzen': '„… die Schuld von 2000 Talern bis Johanni, sonst … A. K.“',
    'Wachliste der Leibgarde': '22:40 Uhr: „Kröpke, Kammerdiener – Wein holen“. 22:55 Uhr: „Kröpke – zurück“.',
    'Fund in der Hecke': 'Das Perlencollier der Kurfürstin, eingewickelt in ein Taschentuch mit den Initialen „A. K.“',
    'Nasse Livree': 'Am Ärmel fehlt ein Silberknopf – genau der aus dem Kies am Beckenrand.',
    'Brief aus Amsterdam': '„An Anton Kröpke: Ich erwarte Ihre Zahlung von 2000 Talern. Ware wird auch angenommen.“'
  },
  motives: [ 'Spielschulden – er wollte sie mit dem Collier bezahlen', 'Eifersucht einer Hofdame', 'Ein Streit unter Offizieren', 'Rache des Gärtners für seine Entlassung' ],
  keys: [ 'Silberknopf im Kies', 'Nasse Livree', 'Fund in der Hecke', 'Wachliste der Leibgarde' ]
});

// ===================================================================================
add({
  id: 'expo', icon: '🌐', title: 'Pavillon der Zukunft', tag: 'Expo 2000, Hannover',
  blurb: 'Nach Ausstellungsschluss liegt die Architektin tot unter der Dachterrasse ihres eigenen Pavillons. Am Morgen sollte er gesperrt werden.',
  victim: 'Dr. Katrin Moll, 44, Architektin des „Pavillons der Zukunft“',
  intro: [
    'Hannover im August 2000. Die Weltausstellung ist in vollem Gang. Jeden Tag strömen Zehntausende über das Expo-Gelände, und der „Pavillon der Zukunft“ ist einer der Publikumsmagneten.',
    'Um 23 Uhr schließen die Tore. Um 23 Uhr 40 findet ein Sicherheitsmann die Architektin des Pavillons, Dr. Katrin Moll, am Fuß der Dachterrasse. Sie ist aus zwölf Metern Höhe gestürzt.',
    'Ein Unglück? Ein Selbstmord? Am Abend hatte sie noch angekündigt: „Morgen früh wird mein Pavillon geschlossen. Für alle.“',
    'Die Expo öffnet morgen um neun Uhr. Bis dahin muss der Fall gelöst sein.'
  ],
  chars: [
    { name: 'Bernd Hollweg', emoji: '🏗️', role: 'Bauunternehmer', g: 'm', guilty: true },
    { name: 'Svenja Moll', emoji: '🎒', role: 'Tochter des Opfers, Studentin', g: 'f' },
    { name: 'Dr. Ralf Seidel', emoji: '📐', role: 'Statiker', g: 'm' },
    { name: 'Yvonne Bauer', emoji: '🎤', role: 'Pavillon-Managerin', g: 'f' },
    { name: 'Mehmet Yıldız', emoji: '🦺', role: 'Sicherheitsmann', g: 'm' },
    { name: 'Corinna Weiss', emoji: '📰', role: 'Reporterin', g: 'f' }
  ],
  acts: [
    { title: 'Die Dachterrasse',
      text: 'Die Polizei sperrt die Dachterrasse des Pavillons. Das Glasgeländer ist hüfthoch und unbeschädigt. Neben der Absturzstelle liegt Katrins Aktenmappe, aufgerissen. Und auf der Terrasse steht ein halb volles Glas Wein.',
      evidence: [
        { ic: '🧱', name: 'Glasgeländer', text: 'Hüfthoch und unbeschädigt. Ein Sturz aus Versehen ist kaum möglich.' },
        { ic: '💼', name: 'Aufgerissene Aktenmappe', text: 'Katrins Mappe. Es fehlen Unterlagen.' },
        { ic: '🍷', name: 'Weinglas', text: 'Halb voll, auf der Dachterrasse. Jemand war bei ihr.' }
      ] },
    { title: 'Der Stahl',
      text: 'Im Büro der Architektin finden die Ermittler einen Laborbericht über den Stahl des Pavillons. Daneben einen Entwurf für einen Brief an die Expo-Leitung. Und auf Katrins Handy ist eine Sprachnachricht vom Abend.',
      evidence: [
        { ic: '🧪', name: 'Laborbericht', text: 'Prüfung von Stahlproben aus dem Dachtragwerk des Pavillons.' },
        { ic: '📝', name: 'Briefentwurf an die Expo-Leitung', text: 'Datiert auf morgen früh. Betreff: „Sofortige Schließung“.' },
        { ic: '📞', name: 'Sprachnachricht', text: 'Auf Katrins Handy, 22:31 Uhr. Eine Männerstimme.' }
      ] },
    { title: 'Das Drehkreuz',
      text: 'Jeder, der nach Ausstellungsschluss auf das Gelände will, muss durch ein Drehkreuz mit Ausweis. Die Betreiberfirma schickt ihre Liste. Dazu kommen ein Fund aus einem Papierkorb und die Lieferscheine der Baufirma.',
      evidence: [
        { ic: '🎫', name: 'Drehkreuz-Protokoll', text: 'Wer war nach 23 Uhr noch auf dem Gelände?' },
        { ic: '🗑️', name: 'Papierkorb am Ausgang', text: 'Zerrissene Blätter, frisch hineingeworfen.' },
        { ic: '📑', name: 'Lieferscheine der Baufirma', text: 'Welcher Stahl wurde tatsächlich geliefert?' }
      ] }
  ],
  solution: [
    'Der Täter ist Bernd Hollweg, der Bauunternehmer.',
    'Um Geld zu sparen, hatte seine Firma für das Dach des Pavillons billigeren Stahl verbaut als vorgeschrieben – und die Prüfzeugnisse gefälscht. Katrin Moll hatte Proben untersuchen lassen. Am nächsten Morgen wollte sie den Pavillon sperren lassen, und Hollweg drohten Gefängnis und der Ruin.',
    'Nach Ausstellungsschluss bestellte er sie zu einem Gespräch auf die Dachterrasse. Als sie sich weigerte zu schweigen, stieß er sie über das Geländer. Dann riss er den Laborbericht aus ihrer Mappe und warf ihn in einen Papierkorb am Ausgang. Das Drehkreuz und seine Sprachnachricht überführten ihn.'
  ],
  keys: [ 'Drehkreuz: Hollweg um 23:08 Uhr hinein, 23:31 Uhr hinaus', 'Laborbericht: zu schwacher Stahl = Motiv', 'Zerrissene Unterlagen im Papierkorb', 'Sprachnachricht: „Wir treffen uns oben“' ]
}, {
  talk: [
    [ 'Katrin und ich haben diesen Pavillon zusammen gebaut. Ich war heute Abend im Hotel in der Innenstadt und habe Fußball geguckt.',
      'Der Stahl ist einwandfrei, alles geprüft und zertifiziert. Katrin war übernervös. Fragen Sie lieber den Statiker – der hat die Berechnungen gemacht.',
      'Mein Ausweis? Ich verleihe ihn manchmal an meinen Polier. Und die Nachricht auf dem Handy … ich wollte mich nur für morgen verabreden.' ],
    [ 'Meine Mutter hat mich um zehn angerufen. Sie klang aufgeregt: „Ich muss heute Abend noch jemandem die Wahrheit sagen.“',
      'Ich war mit Freunden am Maschsee, bis nach Mitternacht. Es gibt Fotos, und die halbe Studentengruppe war dabei.',
      'Mama hat mir letzte Woche erzählt, dass Herr Hollweg ihr Geld angeboten hat. Viel Geld. „Damit du die Sache mit dem Stahl vergisst.“' ],
    [ 'Meine Berechnungen gingen vom bestellten Stahl aus. Wenn etwas anderes verbaut wurde, stimmt meine Statik nicht mehr.',
      'Katrin hat mich heute Nachmittag angerufen. Sie sagte: „Ralf, der Stahl ist zu schwach. Das Dach kann bei Sturm einstürzen.“',
      'Ich war heute Abend bei einem Vortrag in der Messehalle, bis 23 Uhr. Danach bin ich mit dem Taxi nach Hause gefahren – die Quittung habe ich noch.' ],
    [ 'Ich leite den Betrieb im Pavillon. Eine Schließung wäre eine Katastrophe für uns – aber Menschenleben gehen vor.',
      'Katrin hat mir um neun gesagt, dass sie morgen den Pavillon schließen lässt. Ich war entsetzt, aber ich habe es verstanden.',
      'Um zehn nach elf hörte ich unten im Büro Schritte auf der Treppe zur Dachterrasse. Schwere Schritte, wie von Arbeitsschuhen. Katrin trug Turnschuhe.' ],
    [ 'Ich habe sie um 23:40 Uhr gefunden. Ich mache meine Runde jede Nacht um diese Zeit.',
      'Kurz nach halb zwölf ist ein Mann mit Bauhelm am Gürtel sehr schnell Richtung Ausgang Nord gegangen. Er hat etwas in einen Papierkorb geworfen.',
      'Der Ausweis am Drehkreuz zeigt ein Foto. Der Kollege am Ausgang Nord hat sich das Gesicht angesehen – es war Herr Hollweg selbst, nicht sein Polier.' ],
    [ 'Ich recherchiere seit Wochen über Pfusch beim Bau der Expo. Katrin hatte mir für morgen ein Interview versprochen.',
      'Sie sagte mir am Telefon: „Ich habe die Beweise. Der Stahl im Dach ist nicht der, der bezahlt wurde.“',
      'Herr Hollweg hat mich letzte Woche angerufen und gedroht, mich zu verklagen, wenn ich „Lügen über seine Firma“ schreibe. Dabei hatte ich noch gar nichts geschrieben.' ]
  ],
  look: {
    'Glasgeländer': 'Auf dem Handlauf: Abdrücke von zwei Händen, große Männerhände, mit Spuren von Baustaub.',
    'Aufgerissene Aktenmappe': 'Das Inhaltsverzeichnis nennt „Laborbericht Stahl“ und „Lieferscheine Hollweg“. Beides fehlt.',
    'Weinglas': 'Nur ein Glas. Katrin hat getrunken – ihr Besuch nicht.',
    'Laborbericht': '„Festigkeit der Proben: 40 Prozent unter dem vorgeschriebenen Wert. Akute Einsturzgefahr bei Sturm.“',
    'Briefentwurf an die Expo-Leitung': '„… muss ich die sofortige Schließung verlangen. Verantwortlich ist die Firma Hollweg Bau.“',
    'Sprachnachricht': '„Katrin, hier Bernd. Wir müssen reden, heute noch. Ich komme nach Schluss vorbei. Wir treffen uns oben.“',
    'Drehkreuz-Protokoll': '23:08 Uhr Eingang Nord: Ausweis „Hollweg, B.“ – 23:31 Uhr Ausgang Nord: derselbe Ausweis.',
    'Papierkorb am Ausgang': 'Der zerrissene Laborbericht aus Katrins Mappe – mit einem Fingerabdruck voller Baustaub.',
    'Lieferscheine der Baufirma': 'Bestellt und bezahlt: Stahl S355. Geliefert laut Werkstempel: der viel billigere S235.'
  },
  motives: [ 'Billiger Stahl verbaut – Gefängnis und Ruin drohten', 'Das Erbe der Tochter', 'Falsche Berechnungen des Statikers', 'Eine Schließung ruiniert den Pavillon-Betrieb' ],
  keys: [ 'Drehkreuz-Protokoll', 'Sprachnachricht', 'Papierkorb am Ausgang', 'Lieferscheine der Baufirma' ]
});
})();
