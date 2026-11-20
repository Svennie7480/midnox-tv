// midnox Krimi-Abend – weitere Fälle für den Detektiv-Modus (1–2 Spieler).
(function(){
const add = (c, d) => { c.solo = true; window.CASES.push(c); window.DETECTIVE[c.id] = d; };

// ===================================================================================
add({
  id: 'heiligabend', icon: '🎄', title: 'Stille Nacht', tag: 'Gut Eichhorst bei Plön, Heiligabend 1999',
  blurb: 'Während unten die Bescherung läuft, stürzt der alte Gutsherr in der Bibliothek von der Leiter. Ein Unfall – sagen alle.',
  victim: 'Heinrich von Eichhorst, 78, Gutsherr und Holzhändler',
  intro: [
    'Heiligabend 1999. Auf Gut Eichhorst am Plöner See brennen die Kerzen am Baum, draußen fällt seit sieben Uhr dicker Schnee. Das neue Jahrtausend ist nur noch eine Woche entfernt.',
    'Um viertel nach sieben verlässt der alte Heinrich von Eichhorst die Bescherung. „Ich hole noch schnell das Familienbuch“, sagt er.',
    'Um 19 Uhr 40 findet ihn sein Enkel Jakob in der Bibliothek. Er liegt am Fuß der Bücherleiter, das Genick gebrochen.',
    'Die Familie spricht von einem tragischen Unfall. Aber ihr seid Detektive – und an Heiligabend glaubt ihr nicht an Zufälle.'
  ],
  chars: [
    { name: 'Ralf Wendt', emoji: '💼', role: 'Schwiegersohn, Geschäftsführer', g: 'm', guilty: true },
    { name: 'Elisabeth Wendt', emoji: '📹', role: 'Tochter des Gutsherrn', g: 'f' },
    { name: 'Jakob Wendt', emoji: '🎹', role: 'Enkel, Musikstudent', g: 'm' },
    { name: 'Hedwig Brandt', emoji: '🍗', role: 'Köchin seit 40 Jahren', g: 'f' },
    { name: 'Pastor Lorenz Behrens', emoji: '✝️', role: 'Gast zur Bescherung', g: 'm' },
    { name: 'Dr. Sophie Lenz', emoji: '🩺', role: 'Hausärztin und gute Freundin', g: 'f' }
  ],
  acts: [
    { title: 'Unter dem Weihnachtsbaum',
      text: 'In der Bibliothek ist es still. Die Bücherleiter steht an ihrem Platz, der Teppich davor ist verrutscht. Auf dem Lesetisch liegt das Familienbuch, aufgeschlagen bei der Seite mit dem Stammbaum. Heinrich hatte es also schon gefunden – bevor er stürzte.',
      evidence: [
        { ic: '🪜', name: 'Bücherleiter', text: 'Die Leiter, von der Heinrich gestürzt sein soll.' },
        { ic: '🧶', name: 'Verrutschter Teppich', text: 'Der alte Perserteppich liegt in Falten.' },
        { ic: '📖', name: 'Familienbuch', text: 'Aufgeschlagen auf dem Lesetisch. Warum dann noch auf die Leiter?' }
      ] },
    { title: 'Das Fax',
      text: 'Im Arbeitszimmer des Gutsherrn liegt ein Fax der Bank, angekommen heute um 18 Uhr 50. Daneben eine Notiz in Heinrichs Handschrift. Und draußen vor der Terrassentür liegt frischer Schnee.',
      evidence: [
        { ic: '📠', name: 'Fax der Bank', text: 'Eingegangen am 24.12. um 18:50 Uhr. Ein Wertpapierkauf über 2 Millionen Mark.' },
        { ic: '✍️', name: 'Handschriftliche Notiz', text: 'Auf Heinrichs Schreibtisch, mit dem Füller geschrieben.' },
        { ic: '❄️', name: 'Schnee auf der Terrasse', text: 'Seit 19 Uhr fällt Neuschnee vor der Bibliothek.' }
      ] },
    { title: 'Die Videokassette',
      text: 'Elisabeth hat die ganze Bescherung mit dem neuen Camcorder gefilmt. Die Polizei schaut sich die Kassette an. Unter dem Teppich in der Bibliothek findet sich ein kleiner, goldener Gegenstand. Und im Kalender des Gutsherrn steht ein Termin für den 27. Dezember.',
      evidence: [
        { ic: '📼', name: 'Videokassette', text: 'Die Bescherung von 19:05 bis 19:35 Uhr, mit Zeitstempel.' },
        { ic: '🪙', name: 'Manschettenknopf', text: 'Gold, unter dem Teppich in der Bibliothek gefunden.' },
        { ic: '📅', name: 'Kalender des Gutsherrn', text: 'Ein Eintrag für den 27. Dezember.' }
      ] }
  ],
  solution: [
    'Der Täter ist Ralf Wendt, der Schwiegersohn.',
    'Er hatte zwei Millionen Mark aus dem Holzhandel in riskante Aktien gesteckt – und verloren. Am Heiligabend kam das Fax der Bank. Heinrich wollte ihn entlassen und nach Weihnachten wegen Untreue anzeigen.',
    'Um 19 Uhr 14 verließ Ralf die Bescherung und folgte ihm in die Bibliothek. Nach einem heftigen Streit stieß er den alten Mann so, dass es wie ein Sturz von der Leiter aussah. Sein Alibi, er sei draußen gewesen, widerlegte der unberührte Schnee.'
  ],
  keys: [ 'Video: Ralf fehlt 19:14–19:31 Uhr', 'Unberührter Schnee widerlegt sein Alibi', 'Fax der Bank = Motiv', 'Manschettenknopf „R.W.“ unter dem Teppich' ]
}, {
  talk: [
    [ 'Ich war draußen und habe das Feuerwerk für Silvester ausprobiert. Furchtbar. Heinrich wollte doch nur das Familienbuch holen – und dann diese alte Leiter …',
      'Das Fax? Eine Routinesache der Bank. Heinrich hat sich über Kleinigkeiten immer aufgeregt. Fragen Sie lieber Jakob, der wollte dringend Geld von ihm.',
      'Den Manschettenknopf habe ich heute Nachmittag verloren, als ich Bücher getragen habe. Und auf der Kassette bin ich nicht, weil ich draußen war. Das sagte ich doch!' ],
    [ 'Ich war mit Hedwig in der Küche und habe die Gans tranchiert. Vater sagte um viertel nach sieben, er hole das Familienbuch.',
      'Ralf hat mir verschwiegen, wie es um die Firma steht. Seit Wochen schläft er schlecht und starrt nachts auf Aktienkurse im Internet.',
      'Ich habe die Bescherung gefilmt. Sehen Sie selbst: Ralf geht um 19:14 Uhr hinaus und kommt um 19:31 Uhr zurück. Von draußen? Er hatte Hausschuhe an.' ],
    [ 'Ich habe Opa gefunden. Ich wollte ihm mein Geschenk geben … Vorher habe ich die ganze Zeit Klavier gespielt, das haben alle gehört.',
      'Ja, ich wollte Geld von Opa, für mein erstes Album. Er hat nein gesagt. Aber deshalb … nein. Niemals.',
      'Als ich gegen zwanzig nach sieben eine Pause gemacht habe, hörte ich durch die Bibliothekstür Onkel Ralf: „Das Geld kommt zurück, Heinrich, ich schwöre es!“' ],
    [ 'Vierzig Jahre koche ich in diesem Haus. Der gnädige Herr ist nie auf Leitern gestiegen. Das Familienbuch steht doch ganz unten im Regal!',
      'Um zehn vor sieben ratterte das Faxgerät im Büro. Der gnädige Herr hat das Fax gelesen und ist ganz rot geworden im Gesicht.',
      'Gegen halb acht kam Herr Wendt durch die Küche. Ohne Mantel, in Hausschuhen – und ganz außer Atem.' ],
    [ 'Ich war zur Bescherung eingeladen. Heinrich wollte nach Weihnachten eine Spende für unsere neue Orgel unterschreiben.',
      'Heinrich sagte mir beim Sekt: „Heute Abend muss ich noch eine sehr unangenehme Sache mit der Familie klären.“',
      'Ich stand um halb acht am Fenster zur Terrasse. Der Schnee war vollkommen unberührt. Da draußen war niemand.' ],
    [ 'Ich habe ihn untersucht. Genickbruch. Aber die Verletzung an der Schläfe passt nicht zu einem Sturz von der Leiter.',
      'Heinrich hat heute am Telefon zu seinem Anwalt gesagt: „Am 27. zeigen wir ihn an.“ Wen er meinte, weiß ich nicht.',
      'Seit seiner Hüftoperation hat er mir versprochen, nie wieder auf eine Leiter zu steigen. Und er hat seine Versprechen immer gehalten.' ]
  ],
  look: {
    'Bücherleiter': 'Die Leiter ist oben fest eingehakt. Sie kann nicht weggerutscht sein.',
    'Verrutschter Teppich': 'Der Teppich ist nur auf der Seite zur Tür verrutscht – dort, wo jemand gestanden und gerungen hat.',
    'Familienbuch': 'Es gehört ins unterste Regalfach. Für dieses Buch braucht niemand eine Leiter.',
    'Fax der Bank': 'Wertpapierkauf: Internet-Aktien für 2 Millionen Mark. Auftraggeber: R. Wendt, Holzhandel Eichhorst.',
    'Handschriftliche Notiz': '„R. – sofort entlassen. Anwalt am 27.12.“',
    'Schnee auf der Terrasse': 'Bis zum Fund um 19:40 Uhr gibt es auf der Terrasse keine einzige Fußspur.',
    'Videokassette': 'Zeitstempel: Ralf verlässt die Halle um 19:14 Uhr – durch die Tür zur Bibliothek, nicht zur Terrasse.',
    'Manschettenknopf': 'Graviert mit „R.W.“ Er lag unter dem Teppich, dort, wo gerungen wurde.',
    'Kalender des Gutsherrn': '27.12.: „Dr. Paulsen – Strafanzeige vorbereiten. Untreue.“'
  },
  motives: [ 'Firmengeld verspekuliert – Anzeige wegen Untreue drohte', 'Das Erbe des Guts', 'Geld für das eigene Album', 'Die versprochene Spende' ],
  keys: [ 'Videokassette', 'Schnee auf der Terrasse', 'Fax der Bank', 'Manschettenknopf' ]
});

// ===================================================================================
add({
  id: 'abpfiff', icon: '⚽', title: 'Abpfiff', tag: 'Stadion an der Trave, heute',
  blurb: 'Nach dem Heimspiel wird der Vereinspräsident tot in der Sauna gefunden. Die Tür war von außen verkeilt.',
  victim: 'Dieter Kowalski, 61, Präsident des VfR Hanse',
  intro: [
    'Samstag, 18 Uhr. Der VfR Hanse hat sein Heimspiel überraschend mit null zu zwei verloren. Die Fans sind längst auf dem Heimweg, im Vereinsheim wird aufgeräumt.',
    'Um 18 Uhr 35 entdeckt Platzwart Gerd Hansen, dass die Saunatür im Keller mit einem Besenstiel von außen verkeilt ist. Drinnen liegt Präsident Dieter Kowalski. Er ist tot. Die Sauna stand auf Höchststufe.',
    'Kowalski ging nach jedem Spiel in die Sauna, das wusste im Verein jeder.',
    'Heute war es ein Spiel zu viel. Findet heraus, wer ihm die Tür zugesperrt hat.'
  ],
  chars: [
    { name: 'Marc Steiner', emoji: '📈', role: 'Sportdirektor', g: 'm', guilty: true },
    { name: 'Kevin Arslan', emoji: '🧤', role: 'Kapitän der Mannschaft', g: 'm' },
    { name: 'Sandra Kowalski', emoji: '🥂', role: 'Ehefrau des Präsidenten', g: 'f' },
    { name: 'Gerd Hansen', emoji: '🔧', role: 'Platzwart', g: 'm' },
    { name: 'Lena Vogt', emoji: '🎙️', role: 'Pressesprecherin', g: 'f' },
    { name: 'Ayşe Demir', emoji: '📋', role: 'Cheftrainerin', g: 'f' }
  ],
  acts: [
    { title: 'Die Sauna',
      text: 'Die Polizei sichert den Saunabereich im Keller. Der Besenstiel hat die Tür blockiert, Fingerabdrücke sind abgewischt. An der Steuerung wurde die Temperatur auf 110 Grad gestellt. Und im Spielbericht stehen zwei seltsame Szenen aus der ersten Halbzeit.',
      evidence: [
        { ic: '🧹', name: 'Besenstiel', text: 'Verkeilte die Saunatür von außen. Abgewischt.' },
        { ic: '🌡️', name: 'Saunasteuerung', text: 'Jemand hat die Temperatur auf 110 Grad gestellt.' },
        { ic: '📝', name: 'Spielbericht', text: 'Rote Karte für den Abwehrchef in der 23. Minute, Eigentor in der 41. – Halbzeit 0:2.' }
      ] },
    { title: 'Die Wetten',
      text: 'In Kowalskis Postfach liegt eine E-Mail der Integritätsstelle des Verbandes. Im Büro des Präsidenten findet sich ein Ausdruck mit Wettquoten. Und Kowalskis Handy zeigt einen letzten Anruf, kurz vor seinem Tod.',
      evidence: [
        { ic: '📧', name: 'E-Mail der Integritätsstelle', text: '„Auffällige Wetten auf Spiele des VfR Hanse. Bitte melden Sie sich bis Montag.“' },
        { ic: '🎲', name: 'Wettquoten-Ausdruck', text: 'Hohe Einsätze aus Asien – genau auf die heutigen Ereignisse.' },
        { ic: '📞', name: 'Letzter Anruf', text: 'Kowalski telefonierte um 17:52 Uhr. Gesprächsdauer: 4 Minuten.' }
      ] },
    { title: 'Der Spind',
      text: 'Die Polizei öffnet die Spinde im Funktionstrakt. In einem liegt ein billiges Zweithandy. Der Platzwart legt sein Schlüsselbuch vor. Und die Bank meldet eine auffällige Überweisung aus Malta.',
      evidence: [
        { ic: '📱', name: 'Zweithandy', text: 'Prepaid-Handy aus einem Spind im Funktionstrakt. Wenige, aber eindeutige Nachrichten.' },
        { ic: '🗝️', name: 'Schlüsselbuch des Platzwarts', text: 'Wer hat wann welchen Schlüssel ausgeliehen?' },
        { ic: '🏦', name: 'Überweisung aus Malta', text: '50.000 Euro, eingegangen am Donnerstag.' }
      ] }
  ],
  solution: [
    'Der Täter ist Marc Steiner, der Sportdirektor.',
    'Er hatte Spiele des VfR Hanse an Wettbetrüger verkauft: eine Rote Karte, ein Eigentor, ein bestimmtes Halbzeitergebnis. Dafür kassierte er 50.000 Euro aus Malta. Die Integritätsstelle war ihm auf der Spur, und Kowalski wollte am Montag alles offenlegen.',
    'Um 17 Uhr 52 rief Kowalski ihn an und stellte ihn zur Rede. Kurz darauf lieh sich Steiner den Saunaschlüssel, verkeilte die Tür mit einem Besenstiel und drehte die Hitze auf. Die Nachrichten auf seinem Zweithandy verrieten ihn.'
  ],
  keys: [ 'Zweithandy mit Nachrichten an die Wettbetrüger', 'Schlüsselbuch: Saunaschlüssel an Steiner', 'Überweisung aus Malta', 'Letzter Anruf ging an Steiner' ]
}, {
  talk: [
    [ 'Furchtbar. Dieter war wie ein Vater für mich. Ich saß nach dem Spiel die ganze Zeit in meinem Büro und habe Videos analysiert.',
      'Wetten? Darüber weiß ich nichts. Fragen Sie den Kapitän – Kevin hatte heute Streit mit Dieter, weil sein Vertrag nicht verlängert wird.',
      'Das Handy gehört mir nicht. Jeder kann einen Spind benutzen. Und den Schlüssel? Ich wollte nur nachsehen, ob die Sauna für die Physio warm ist.' ],
    [ 'Ich habe mich nach dem Spiel mit Dieter gestritten, ja. Er wollte meinen Vertrag nicht verlängern. Aber ich bin danach sofort zur Dopingkontrolle.',
      'Die Dopingkontrolle ging von 17:45 bis 18:40 Uhr. Der Kontrolleur vom Verband war die ganze Zeit bei mir.',
      'In der Kabine hat Marc vor dem Spiel zu unserem Abwehrchef gesagt: „Mach dir keine Sorgen wegen heute. Du wirst versorgt.“ Ich dachte, er meinte Schmerzmittel.' ],
    [ 'Ich war oben im VIP-Raum bei den Sponsoren. Dieter wollte nur kurz in die Sauna, wie nach jedem Spiel.',
      'Dieter war seit Tagen unruhig. Er sagte mir: „Im Verein stimmt etwas nicht, Sandra. Und ich weiß auch schon, wer dahintersteckt.“',
      'Ich weiß, was Sie denken: Ich erbe die Anteile am Verein. Aber die sind fast nichts wert. Der Verein steckt in den roten Zahlen.' ],
    [ 'Ich habe ihn gefunden. Der Besenstiel steckte quer im Türgriff. Ich habe sofort aufgemacht, aber es war zu spät.',
      'Die Sauna stelle ich immer auf 85 Grad. 110 Grad – das macht niemand freiwillig. Die Steuerung ist hinter einer Klappe im Flur.',
      'Um 17:55 Uhr hat sich Herr Steiner bei mir den Saunaschlüssel geliehen. „Für die Physio“, sagte er. Die Physio war aber längst nach Hause gefahren.' ],
    [ 'Ich war die ganze Zeit in der Pressekonferenz, bis 18:20 Uhr. Danach habe ich Fotos sortiert.',
      'Herr Kowalski wollte am Montag eine Pressekonferenz geben. „Es geht um die Sauberkeit unseres Vereins“, hat er gesagt.',
      'Auf einem Foto aus dem Kabinengang sieht man Herrn Steiner um 17:58 Uhr Richtung Keller gehen. Mit einem Besen in der Hand.' ],
    [ 'Ich trainiere die Mannschaft seit einem halben Jahr. Nach dem Spiel war ich bei der Pressekonferenz, mit Lena.',
      'Die Rote Karte heute war ein Witz. Unser Abwehrchef foult sonst nie so dumm. Und das Eigentor … ich verstehe es nicht.',
      'Marc hat mir letzte Woche gesagt, ich soll den Abwehrchef unbedingt aufstellen. „Vertrau mir einfach, Ayşe.“' ]
  ],
  look: {
    'Besenstiel': 'Der Besen stammt aus dem Kabinengang im Erdgeschoss – nicht aus dem Keller.',
    'Saunasteuerung': 'Die Klappe der Steuerung zeigt einen frischen Fingerabdruck. Er gehört nicht dem Platzwart.',
    'Spielbericht': 'Beide Szenen geschahen in der ersten Halbzeit – genau so, wie es in den Wetten stand.',
    'E-Mail der Integritätsstelle': 'Im Anhang: „Verdacht richtet sich gegen eine Person aus der sportlichen Leitung.“',
    'Wettquoten-Ausdruck': 'Gewettet wurde auf „Rote Karte VfR vor der Pause“ und „Halbzeit 0:2“. Beides ist heute eingetreten.',
    'Letzter Anruf': 'Der Anruf um 17:52 Uhr ging an Marc Steiner.',
    'Zweithandy': 'Letzte Nachricht um 16:58 Uhr: „Rot und Eigentor vor der Pause – erledigt. Rest wie besprochen.“',
    'Schlüsselbuch des Platzwarts': '17:55 Uhr: „Sauna – M. Steiner“. Zurückgegeben: nie.',
    'Überweisung aus Malta': 'Empfänger: Marc Steiner. Absender: eine Briefkastenfirma für „Beratungsleistungen“.'
  },
  motives: [ 'Spielmanipulation für Wettbetrüger flog auf', 'Der Vertrag wurde nicht verlängert', 'Die Anteile am Verein erben', 'Streit um die Aufstellung' ],
  keys: [ 'Zweithandy', 'Schlüsselbuch des Platzwarts', 'Überweisung aus Malta', 'Letzter Anruf' ]
});

// ===================================================================================
add({
  id: 'regatta', icon: '⛵', title: 'Die letzte Wettfahrt', tag: 'Kiel-Schilksee, Olympia 1972',
  blurb: 'Der Favorit der Segel-Regatta treibt am Morgen tot im Olympiahafen. Und die geheimen Segelpläne sind verschwunden.',
  victim: 'Lars Thomsen, 28, Segler und Goldfavorit',
  intro: [
    'September 1972. Die Olympischen Spiele sind in München – und die Segler kämpfen im Olympiahafen Kiel-Schilksee um die Medaillen. Die ganze Welt schaut auf die Förde.',
    'Am Morgen vor dem entscheidenden Rennen treibt Lars Thomsen, der deutsche Goldfavorit, zwischen den Stegen im Hafenbecken. Er ist ertrunken – aber am Hinterkopf hat er eine Platzwunde.',
    'Aus seiner Bootskiste fehlt die Mappe mit den geheimen Plänen für das neue Spinnakersegel. Mit diesem Segel sollte er Gold holen.',
    'Mitten im Kalten Krieg gehen hier Sportler aus Ost und West ein und aus. Wer hat Lars Thomsen getötet?'
  ],
  chars: [
    { name: 'Gustav Behn', emoji: '🧭', role: 'Trainer der Mannschaft', g: 'm', guilty: true },
    { name: 'Jochen Wolf', emoji: '🪢', role: 'Vorschoter, Lars’ Bootspartner', g: 'm' },
    { name: 'Ingrid Thomsen', emoji: '👒', role: 'Ehefrau des Opfers', g: 'f' },
    { name: 'Dr. Klaus Reimers', emoji: '💊', role: 'Mannschaftsarzt', g: 'm' },
    { name: 'Brigitte Sander', emoji: '📷', role: 'Sportreporterin', g: 'f' },
    { name: 'Hauke Ketelsen', emoji: '⚓', role: 'Hafenmeister', g: 'm' }
  ],
  acts: [
    { title: 'Im Hafenbecken',
      text: 'Die Wasserschutzpolizei zieht Lars Thomsen aus dem Hafenbecken. Seine Armbanduhr ist um 23 Uhr 41 stehen geblieben, als sie ins Wasser kam. An seinem Boot, der „Seeschwalbe“, fehlt eine Winschkurbel. Und der Mannschaftsarzt hat sich die Kopfwunde angesehen.',
      evidence: [
        { ic: '⌚', name: 'Stehengebliebene Uhr', text: 'Um 23:41 Uhr ins Wasser gefallen.' },
        { ic: '🔩', name: 'Fehlende Winschkurbel', text: 'Von der „Seeschwalbe“ fehlt eine schwere Kurbel aus Messing.' },
        { ic: '🩹', name: 'Platzwunde', text: 'Am Hinterkopf. Ein Schlag – bevor er ins Wasser fiel.' }
      ] },
    { title: 'Die Segelpläne',
      text: 'In der Bootskiste der „Seeschwalbe“ liegt die Mappe für das neue Spinnakersegel – leer. Im Zelt der Mannschaft findet die Polizei einen Briefumschlag. Und eine Reporterin legt Fotos vom Vorabend vor.',
      evidence: [
        { ic: '📂', name: 'Leere Planmappe', text: 'Die geheimen Schnittzeichnungen des Spinnakers sind verschwunden.' },
        { ic: '✉️', name: 'Briefumschlag mit Geld', text: '20.000 D-Mark in bar, gefunden im Mannschaftszelt.' },
        { ic: '🎞️', name: 'Fotos vom Vorabend', text: 'Aufnahmen aus dem Olympiahafen, 22 bis 23 Uhr.' }
      ] },
    { title: 'Das Logbuch',
      text: 'Der Hafenmeister legt sein Logbuch vor: Am Steg-Tor wird jeder eingetragen, der nachts zu den Booten will. Im Kofferraum eines Wagens findet sich ein Segelsack. Und ein Telegramm wird abgefangen – adressiert an jemanden aus der deutschen Mannschaft.',
      evidence: [
        { ic: '📒', name: 'Logbuch des Hafenmeisters', text: 'Wer war in der Nacht am Steg der „Seeschwalbe“?' },
        { ic: '🎒', name: 'Segelsack im Kofferraum', text: 'Aus dem Wagen des Trainers. Etwas Schweres liegt darin.' },
        { ic: '📨', name: 'Abgefangenes Telegramm', text: 'Aus Rostock-Warnemünde. Nur ein Satz.' }
      ] }
  ],
  solution: [
    'Der Täter ist Gustav Behn, der Trainer.',
    'Er hatte die geheimen Pläne des neuen Spinnakers an einen Mann der DDR-Delegation verkauft – für 20.000 Mark. Lars Thomsen bemerkte, dass die Mappe leer war, und fand das Geld.',
    'Um 23 Uhr 30 stellte Lars ihn am Steg zur Rede. Gustav schlug ihn mit der Winschkurbel nieder und stieß ihn ins Hafenbecken. Die Kurbel versteckte er in einem Segelsack in seinem Auto. Das Logbuch des Hafenmeisters und die Fotos der Reporterin überführten ihn.'
  ],
  keys: [ 'Winschkurbel im Segelsack des Trainers', 'Logbuch: Behn am Steg 23:20–23:50 Uhr', 'Foto: Behn mit dem Mann aus Rostock', 'Geld und leere Planmappe = Motiv' ]
}, {
  talk: [
    [ 'Lars war wie ein Sohn für mich. Ich war gestern Abend früh im Hotel und habe geschlafen. Wir hatten doch heute das große Rennen.',
      'Die Pläne? Vielleicht hat Lars sie selbst versteckt, er war misstrauisch. Und das Geld gehört sicher Jochen – der hat gestern im Bierzelt mit Lars gestritten.',
      'Der Segelsack liegt seit Tagen in meinem Wagen. Jeder kommt an das Auto, es ist nie abgeschlossen. Und das Telegramm kenne ich nicht.' ],
    [ 'Ich habe mit Lars gestritten, ja. Er wollte für das Finale einen anderen Vorschoter. Ich war betrunken und wütend.',
      'Nach dem Bierzelt bin ich um elf ins Olympische Dorf. Mein Zimmernachbar hat gesehen, wie ich ins Bett gefallen bin. Ich habe bis zum Morgen geschnarcht.',
      'Lars hat mir am Nachmittag gesagt: „Jemand aus unserem Team verkauft unsere Pläne. Heute Nacht stelle ich ihn.“ Ich dachte, er übertreibt.' ],
    [ 'Ich war im Hotel in Strande und habe auf Lars gewartet. Er wollte noch einmal zum Boot, gegen halb zwölf.',
      'Ja, es gibt eine Lebensversicherung. Die hat der Verband für alle Segler abgeschlossen. Fragen Sie nach.',
      'Lars hat mir am Telefon gesagt: „Ich habe das Geld gefunden, Ingrid. Ich weiß jetzt, wer es ist. Ich rede heute Nacht mit ihm.“' ],
    [ 'Die Wunde am Hinterkopf stammt von einem harten, schweren Gegenstand. Er war bewusstlos, als er ins Wasser fiel.',
      'In der Wunde waren Spuren von Messing und Fett. Wie von einer Winschkurbel.',
      'Gustav Behn kam heute früh um sechs zu mir. Er wollte Schlaftabletten – er habe die ganze Nacht nicht geschlafen.' ],
    [ 'Ich berichte für die Kieler Nachrichten über die Regatta. Gestern Abend habe ich im Hafen Stimmungsbilder fotografiert.',
      'Auf einem Foto von 22:15 Uhr sieht man Gustav Behn am Parkplatz – mit einem Mann aus der DDR-Delegation. Sie tauschen eine Mappe.',
      'Ich habe in der Nacht nachgefragt: Der Mann ist kein Segler. Er ist „Berater“ der DDR-Delegation – und kam erst gestern aus Rostock.' ],
    [ 'Nachts bin ich am Steg-Tor. Wer zu den Booten will, wird ins Logbuch eingetragen. So ist die Vorschrift bei Olympia.',
      'Lars Thomsen kam um 23:25 Uhr. Er wirkte wütend und wollte zur „Seeschwalbe“.',
      'Kurz vorher, um 23:20 Uhr, kam Herr Behn. Er ging um 23:50 Uhr wieder – allein. Er hatte einen Segelsack über der Schulter.' ]
  ],
  look: {
    'Stehengebliebene Uhr': 'Das Glas ist von innen beschlagen. Die Uhr lief bis 23:41 Uhr – keine Minute länger.',
    'Fehlende Winschkurbel': 'Die Halterung ist leer. Die Kurbel passt nur auf Boote dieser Klasse – sie gehört zur Mannschaftsausrüstung.',
    'Platzwunde': 'Die Wunde ist rund, etwa so groß wie das Ende einer Winschkurbel.',
    'Leere Planmappe': 'Innen ein Stempel: „Nur für Trainer und Steuermann“. Nur zwei Menschen hatten einen Schlüssel zur Kiste.',
    'Briefumschlag mit Geld': 'Auf der Rückseite, mit Bleistift: „Rest nach dem Rennen. G.“',
    'Fotos vom Vorabend': 'Ein Foto um 22:15 Uhr zeigt den Trainer auf dem Parkplatz. Er übergibt einem Mann eine Mappe.',
    'Logbuch des Hafenmeisters': '23:20 Uhr: „Behn, Trainer“ – 23:25 Uhr: „Thomsen“ – 23:50 Uhr: „Behn geht“. Thomsen ist nie gegangen.',
    'Segelsack im Kofferraum': 'Darin: die fehlende Winschkurbel, nass, mit einem dunklen Fleck.',
    'Abgefangenes Telegramm': '„Ware erhalten. Restzahlung folgt. Gratulation zur Bronze.“ Adressiert an G. Behn.'
  },
  motives: [ 'Die Segelpläne an die DDR verkauft – Lars hatte es entdeckt', 'Rauswurf aus dem Boot vor dem Finale', 'Die Lebensversicherung', 'Eifersucht auf den Erfolg' ],
  keys: [ 'Segelsack im Kofferraum', 'Logbuch des Hafenmeisters', 'Fotos vom Vorabend', 'Briefumschlag mit Geld' ]
});

// ===================================================================================
add({
  id: 'spielbank', icon: '🎰', title: 'Rien ne va plus', tag: 'Spielbank Travemünde, Silvester 1929',
  blurb: 'Punkt Mitternacht knallt das Feuerwerk – und im Büro des Casinodirektors ein Schuss, den niemand hört.',
  victim: 'Emil Strauss, 55, Direktor der Spielbank',
  intro: [
    'Travemünde, Silvester 1929. Im Kurhaus-Casino klirren die Champagnergläser, die Kapelle spielt Charleston. Draußen über der Ostsee warten alle auf das Feuerwerk.',
    'Punkt Mitternacht schlagen die Glocken, und am Himmel explodieren die Raketen. Die ganze Gesellschaft steht auf der Terrasse und jubelt.',
    'Um fünf nach zwölf findet die Sekretärin den Casinodirektor Emil Strauss in seinem Büro. Erschossen, mit seiner eigenen Pistole. Der Tresor steht offen.',
    'Den Schuss hat im Lärm des Feuerwerks niemand gehört. Rien ne va plus – nichts geht mehr. Ermittelt!'
  ],
  chars: [
    { name: 'Baronin Adele von Mehring', emoji: '💎', role: 'Stammgast an den Spieltischen', g: 'f', guilty: true },
    { name: 'Max Hellwig', emoji: '🎲', role: 'Croupier', g: 'm' },
    { name: 'Baron Friedrich von Mehring', emoji: '🎖️', role: 'Ehemann der Baronin', g: 'm' },
    { name: 'Lilly Marlow', emoji: '🎶', role: 'Sängerin der Casinokapelle', g: 'f' },
    { name: 'Kommerzienrat Otto Brandes', emoji: '🏛️', role: 'Besitzer des Konkurrenzcasinos', g: 'm' },
    { name: 'Gerda Kühn', emoji: '🗂️', role: 'Sekretärin des Direktors', g: 'f' }
  ],
  acts: [
    { title: 'Der Schuss um Mitternacht',
      text: 'Emil Strauss sitzt zusammengesunken an seinem Schreibtisch. Die Pistole liegt auf dem Teppich – es ist seine eigene, aus der Schreibtischschublade. Der Tresor ist offen, und auf dem Schreibtisch stehen zwei Gläser Champagner.',
      evidence: [
        { ic: '🔫', name: 'Browning-Pistole', text: 'Die eigene Waffe des Direktors, aus seiner Schreibtischschublade.' },
        { ic: '🗄️', name: 'Offener Tresor', text: 'Das Bargeld ist noch da. Es fehlt nur eine Mappe.' },
        { ic: '🥂', name: 'Zwei Champagnergläser', text: 'Strauss hatte kurz vor Mitternacht Besuch.' }
      ] },
    { title: 'Die Schuldscheine',
      text: 'Die Sekretärin erklärt, was im Tresor fehlt: die Mappe mit den Schuldscheinen der Spieler. Im Kamin des Büros findet die Polizei verkohltes Papier. Und auf dem Teppich unter dem Schreibtisch liegt ein Damenhandschuh aus Seide.',
      evidence: [
        { ic: '📁', name: 'Fehlende Schuldschein-Mappe', text: 'Darin waren die Schulden der Spieler beim Casino verzeichnet.' },
        { ic: '🔥', name: 'Verkohltes Papier im Kamin', text: 'Reste von Formularen mit dem Wappen der Spielbank.' },
        { ic: '🧤', name: 'Seidenhandschuh', text: 'Weiß, ellenbogenlang. Unter dem Schreibtisch gefunden.' }
      ] },
    { title: 'Das Blitzlichtfoto',
      text: 'Ein Fotograf der Lübecker Zeitung hat Punkt Mitternacht die ganze Gesellschaft auf der Terrasse fotografiert, mit Blitzlicht. Der Abzug ist fertig. Dazu kommen ein Brief aus dem Schreibtisch des Direktors und das Dienstbuch des Croupiers.',
      evidence: [
        { ic: '📸', name: 'Mitternachtsfoto', text: 'Die Gesellschaft auf der Terrasse, aufgenommen um Punkt 0:00 Uhr.' },
        { ic: '💌', name: 'Brief an den Baron', text: 'Aus dem Schreibtisch des Direktors. Adressiert, aber nie abgeschickt.' },
        { ic: '📗', name: 'Dienstbuch des Croupiers', text: 'Wer hat heute Nacht wie viel verloren?' }
      ] }
  ],
  solution: [
    'Die Täterin ist Baronin Adele von Mehring.',
    'Heimlich hatte sie ein Vermögen verspielt. Emil Strauss hielt ihre Schuldscheine über 80.000 Mark und erpresste sie: Wenn sie nicht zahlte, sollte ihr Mann am Neujahrsmorgen alles erfahren.',
    'Kurz vor Mitternacht trank sie mit Strauss ein Glas Champagner. Als draußen das Feuerwerk begann, griff sie nach der Pistole aus seiner Schublade und schoss. Dann verbrannte sie ihre Schuldscheine im Kamin. Doch auf dem Mitternachtsfoto fehlt sie – und ihr Handschuh blieb unter dem Schreibtisch liegen.'
  ],
  keys: [ 'Mitternachtsfoto: Die Baronin fehlt', 'Seidenhandschuh mit Pulverspuren', 'Verbrannte Schuldscheine', 'Erpresserbrief an den Baron = Motiv' ]
}, {
  talk: [
    [ 'Mein Gott, wie schrecklich. Um Mitternacht stand ich auf der Terrasse und habe das Feuerwerk bewundert, wie alle anderen auch.',
      'Schuldscheine? Ich spiele doch nur zum Vergnügen. Fragen Sie lieber Herrn Hellwig – an seinem Tisch fehlen ständig Jetons.',
      'Das Foto muss verwackelt sein, ich stand ganz am Rand. Und Seidenhandschuhe trägt heute Abend jede Dame im Saal.' ],
    [ 'Ich war bis kurz vor Mitternacht am Roulettetisch, dann bin ich mit allen auf die Terrasse gegangen.',
      'Ja, ich habe ein paar Jetons mitgehen lassen. Der Direktor wusste es und hat mir gedroht. Aber um Mitternacht stand ich auf der Terrasse – direkt vor dem Fotografen.',
      'Die Baronin hat in diesem Jahr an meinem Tisch über 80.000 Mark verloren. Gezahlt hat sie nie. Sie hat nur unterschrieben.' ],
    [ 'Ich habe meine Frau um halb zwölf zuletzt gesehen. Sie sagte, sie wolle sich „die Nase pudern“.',
      'Ich spiele nicht, und ich habe dem Direktor nie etwas geschuldet. Um Mitternacht stand ich auf der Terrasse und habe nach Adele gesucht.',
      'Was in diesem Brief steht … Adele hat gespielt? Achtzigtausend Mark? Davon wusste ich nichts. Nichts!' ],
    [ 'Emil und ich … wir waren uns nahe. Ich habe bis 23:45 Uhr gesungen, danach war Pause für das Feuerwerk.',
      'Um zehn vor zwölf sah ich die Baronin die Treppe zum Büro hinaufgehen. Sie hatte zwei Gläser Champagner dabei.',
      'Emil hat mir gestern gesagt: „Morgen früh wird ein Baron sehr unglücklich sein. Und ich sehr reich.“' ],
    [ 'Ja, ich bin die Konkurrenz. Emil hat mir die besten Spieler abgeworben. Aber ich bin nicht hier, um zu morden, sondern um zu feiern.',
      'Um Mitternacht stand ich auf der Terrasse, gleich neben dem Bürgermeister. Fragen Sie ihn.',
      'Emil hat Spieler erpresst, das weiß in Travemünde jeder. Er nannte seine Schuldscheine „meine Lebensversicherung“.' ],
    [ 'Ich habe ihn um fünf nach zwölf gefunden. Ich wollte ihm ein gutes neues Jahr wünschen …',
      'In der Mappe im Tresor lagen die Schuldscheine. Die größten waren die der Baronin von Mehring, über 80.000 Mark.',
      'Den Brief an den Baron hat Herr Strauss mir gestern diktiert. Ich sollte ihn am Neujahrsmorgen per Boten zustellen lassen.' ]
  ],
  look: {
    'Browning-Pistole': 'Keine Fingerabdrücke auf dem Griff – wer geschossen hat, trug Handschuhe.',
    'Offener Tresor': 'Der Tresor wurde mit dem Schlüssel geöffnet, der noch an Strauss’ Uhrkette hängt.',
    'Zwei Champagnergläser': 'Am Rand eines Glases: ein Abdruck von dunkelrotem Lippenstift.',
    'Fehlende Schuldschein-Mappe': 'Im Tresorbuch notiert: „Mappe S. – Schuldscheine. Größter Posten: v. M.“',
    'Verkohltes Papier im Kamin': 'Auf einem Rest ist noch zu lesen: „…von Mehring … 80.000 Mark …“',
    'Seidenhandschuh': 'Am Zeigefinger: schwarze Pulverspuren. Innen eingestickt: ein kleines Wappen mit Krone – das Wappen der Mehrings.',
    'Mitternachtsfoto': 'Alle Gäste sind darauf zu sehen. Nur eine fehlt: die Baronin von Mehring. Der Baron schaut suchend zur Tür.',
    'Brief an den Baron': '„Sehr geehrter Herr Baron, Ihre Gattin schuldet meinem Haus 80.000 Mark …“',
    'Dienstbuch des Croupiers': 'Seit März: Baronin v. Mehring – Verluste 80.400 Mark, alle auf Schuldschein.'
  },
  motives: [ 'Erpressung mit ihren Spielschulden', 'Gestohlene Jetons sollten auffliegen', 'Eifersucht einer Geliebten', 'Konkurrenz zwischen den Casinos' ],
  keys: [ 'Mitternachtsfoto', 'Seidenhandschuh', 'Verkohltes Papier im Kamin', 'Brief an den Baron' ]
});
})();
