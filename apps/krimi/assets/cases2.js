// midnox Krimi-Abend – Fälle nur für den Detektiv-Modus (1–2 Spieler). solo: true = nicht im Krimi-Dinner.
// g = Stimme der Figur (m/w) für die eingesprochenen Aussagen.
(function(){
const add = (c, d) => { c.solo = true; window.CASES.push(c); window.DETECTIVE[c.id] = d; };

// ===================================================================================
add({
  id: 'vorhang', icon: '🎭', title: 'Der letzte Vorhang', tag: 'Hamburg, Premierenabend 1963',
  blurb: 'Im dritten Akt fällt ein Schuss aus dem Theaterrevolver – und der Hauptdarsteller steht nicht wieder auf.',
  victim: 'Julius Hartmann, 45, gefeierter Hauptdarsteller der Hansa-Bühne',
  intro: [
    'Hamburg, im März 1963. Premiere in der Hansa-Bühne, das Haus ist bis auf den letzten Platz ausverkauft. Gespielt wird ein Kriminalstück – mit einem Schuss im letzten Akt.',
    'Um 21 Uhr 52 ist es so weit. Felix Brandt hebt den Theaterrevolver, zielt auf Julius Hartmann und drückt ab. Hartmann fällt, wie in jeder Probe. Doch diesmal steht er nicht wieder auf.',
    'In der Trommel steckte zwischen den Platzpatronen eine echte Kugel. Jemand hat den Revolver geladen – und Felix Brandt hat, ohne es zu wissen, die Tat ausgeführt.',
    'Der Vorhang ist gefallen, die Zuschauer sind nach Hause geschickt. Jetzt beginnt eure Vorstellung, Detektive.'
  ],
  chars: [
    { name: 'Vera Lind', emoji: '💃', role: 'Hauptdarstellerin, Ehefrau des Opfers', g: 'f', guilty: true },
    { name: 'Felix Brandt', emoji: '🔫', role: 'Schauspieler, hat geschossen', g: 'm' },
    { name: 'Tom Berger', emoji: '🎓', role: 'Zweitbesetzung', g: 'm' },
    { name: 'Karl Möller', emoji: '🧰', role: 'Requisiteur', g: 'm' },
    { name: 'Hanna Graf', emoji: '📖', role: 'Souffleuse', g: 'f' },
    { name: 'Dr. Bruno Adler', emoji: '🎩', role: 'Intendant', g: 'm' }
  ],
  acts: [
    { title: 'Der Schuss im dritten Akt',
      text: 'Die Polizei hat die Bühne abgesperrt. Im Theaterrevolver stecken fünf Platzpatronen – und eine abgefeuerte, echte Hülse. Im Regiebuch ist die Szene genau festgelegt: Felix zielt aus drei Metern auf die Brust. Jeder im Ensemble kannte diesen Moment.',
      evidence: [
        { ic: '🔫', name: 'Theaterrevolver', text: 'Fünf Platzpatronen und eine abgefeuerte echte Hülse.' },
        { ic: '🥉', name: 'Echte Patronenhülse', text: 'Kaliber .38. Woher kommt eine echte Patrone ins Theater?' },
        { ic: '📕', name: 'Regiebuch', text: 'Szene 7: Schuss auf die Brust, aus drei Metern. Jeder kannte den Ablauf.' }
      ] },
    { title: 'Zwanzig Minuten Pause',
      text: 'Der Requisiteur legt seine Prüfliste vor: Um 20 Uhr 45 hat er den Revolver kontrolliert, sechs Platzpatronen. Danach lag die Waffe auf dem Requisitentisch hinter der Bühne – auch in der Pause von 21 Uhr bis 21 Uhr 20. Auf dem Tisch findet die Polizei ein Spitzentaschentuch. Und in Hartmanns Garderobe ein Telegramm aus München.',
      evidence: [
        { ic: '📋', name: 'Prüfliste des Requisiteurs', text: '20:45 Uhr: „Revolver – 6 Platzpatronen – geprüft.“ Unterschrift Karl Möller.' },
        { ic: '🪡', name: 'Spitzentaschentuch', text: 'Gefunden auf dem Requisitentisch, direkt neben dem Revolver.' },
        { ic: '✉️', name: 'Telegramm aus München', text: 'An Julius Hartmann, eingetroffen heute Nachmittag. Ein Filmangebot.' }
      ] },
    { title: 'Die Wohnung am Leinpfad',
      text: 'Die Kriminalpolizei durchsucht die Wohnung der Hartmanns. Im Schreibtisch liegt Julius’ alter Dienstrevolver aus dem Krieg und eine Schachtel Munition. Daneben: eine frische Lebensversicherung. Und im Theater stellt ein Bühnenarbeiter fest, dass in einer der Garderoben die Lampe herausgedreht ist.',
      evidence: [
        { ic: '📦', name: 'Patronenschachtel aus der Wohnung', text: 'Kaliber .38, aus dem Schreibtisch der Hartmanns.' },
        { ic: '📜', name: 'Lebensversicherung', text: '100.000 Mark auf das Leben von Julius Hartmann.' },
        { ic: '💡', name: 'Herausgedrehte Glühbirne', text: 'In der Garderobe von Vera Lind. Die Birne liegt auf dem Schminktisch.' }
      ] }
  ],
  solution: [
    'Die Täterin ist Vera Lind, die Ehefrau des Opfers.',
    'Julius wollte sich scheiden lassen und allein nach München gehen, um einen Film zu drehen. Vor drei Wochen hatte Vera heimlich eine Lebensversicherung über 100.000 Mark auf ihn abgeschlossen.',
    'In der Pause, als der Requisitentisch unbewacht war, tauschte sie eine Platzpatrone gegen eine echte Kugel aus Julius’ altem Dienstrevolver. Damit es so aussah, als sei sie in ihrer Garderobe, drehte sie dort die Glühbirne heraus. Doch ihr Taschentuch blieb neben dem Revolver liegen – und der Requisiteur sah sie am Tisch.'
  ],
  keys: [ 'Taschentuch „V.L.“ am Requisitentisch', 'Patronen aus der eigenen Wohnung', 'Lebensversicherung mit Veras Unterschrift', 'Dunkle Garderobe in der Pause' ]
}, {
  talk: [
    [ 'Ich stand in der Kulisse und sah, wie er fiel. Felix hat geschossen – er hat Julius gehasst, wissen Sie das denn nicht?',
      'In der Pause war ich die ganze Zeit in meiner Garderobe. Ich habe mich umgezogen und mich auf den dritten Akt konzentriert.',
      'Die Versicherung? So etwas ist in unserem Beruf ganz normal. Und an unseren Schreibtisch hätte jeder kommen können, die Wohnung ist nie abgeschlossen.' ],
    [ 'Ich habe geschossen, wie in jeder Probe. Auf die Brust, aus drei Metern. Ich dachte, es sind Platzpatronen! Ich habe einen Freund erschossen …',
      'Ja, ich hatte Streit mit Julius. Er hatte etwas mit meiner Verlobten. Aber in der Pause saß ich in der Kantine – Karl saß direkt neben mir.',
      'Als ich den Revolver nach der Pause vom Tisch nahm, roch der Griff nach Maiglöckchen. Ich habe mir nichts dabei gedacht.' ],
    [ 'Ich bin die Zweitbesetzung. Ja, jetzt bekomme ich die Rolle. Aber so wollte ich sie nie bekommen.',
      'In der Pause habe ich vom Pförtnertelefon aus meine Mutter in Kiel angerufen. Zwanzig Minuten. Der Pförtner hat mich die ganze Zeit gesehen.',
      'Vera hat letzte Woche zu mir gesagt: „Bald spielst du die Hauptrolle, Tom. Verlass dich drauf.“ Damals fand ich das nett von ihr.' ],
    [ 'Der Revolver ist meine Requisite. Um Viertel vor neun habe ich ihn geprüft: sechs Platzpatronen, das schwöre ich.',
      'Ich gebe es zu: Von fünf nach bis Viertel nach neun war ich in der Kantine, mit Felix. Der Tisch war unbewacht.',
      'Als ich zurückkam, stand Frau Lind gerade am Requisitentisch. Sie sagte, sie suche ihren Fächer.' ],
    [ 'Ich sitze im Souffleurkasten und sehe die Bühne von unten. Herr Brandt hat genau so gezielt wie in jeder Probe.',
      'In der Pause brauchte ich Frau Lind wegen einer Textstelle. Ich habe an ihre Garderobe geklopft – keine Antwort. Und unter der Tür war kein Licht.',
      'Am Nachmittag hörte ich Herrn Hartmann telefonieren: „Der Film wird ohne Vera gedreht. Und die Scheidung reiche ich nächste Woche ein.“' ],
    [ 'Das Haus war ausverkauft – und jetzt dieser Skandal! Bei uns werden alle Requisiten geprüft, immer.',
      'In der Pause war ich im Foyer bei den Ehrengästen. Fragen Sie den Senator, er stand neben mir.',
      'Julius hat mir gestern gesagt, dass er das Ensemble verlässt. Für einen Film in München. Allein, betonte er.' ]
  ],
  look: {
    'Theaterrevolver': 'Die echte Kugel steckte in der dritten Kammer – genau die, die in Szene 7 abgefeuert wird. Wer das tat, kannte das Stück.',
    'Echte Patronenhülse': 'Kaliber .38, eine Militärmarke von 1944. So etwas kauft man nicht im Laden.',
    'Regiebuch': 'Randnotiz: „Szene 7 – 3. Schuss!“ Das ganze Ensemble hat das Regiebuch gelesen.',
    'Prüfliste des Requisiteurs': 'Unter der Unterschrift ein Kaffeefleck. Karl war in Eile – er wollte in die Kantine.',
    'Spitzentaschentuch': 'Bestickt mit „V.L.“ – und es duftet nach Maiglöckchen.',
    'Telegramm aus München': '„Vertrag für Sie allein. Ihre Gattin wird nicht besetzt. Drehbeginn Mai.“',
    'Patronenschachtel aus der Wohnung': 'Militärmarke 1944, Kaliber .38 – genau wie die Hülse. Eine Patrone fehlt.',
    'Lebensversicherung': 'Abgeschlossen vor drei Wochen. Unterschrieben hat nicht Julius – sondern Vera Lind.',
    'Herausgedrehte Glühbirne': 'Die Birne ist intakt. Jemand hat sie absichtlich herausgedreht – damit die Garderobe dunkel bleibt.'
  },
  motives: [ 'Lebensversicherung und drohende Scheidung', 'Eifersucht wegen der Verlobten', 'Endlich die Hauptrolle spielen', 'Angst um den Ruf des Theaters' ],
  keys: [ 'Spitzentaschentuch', 'Patronenschachtel aus der Wohnung', 'Lebensversicherung', 'Herausgedrehte Glühbirne' ]
});

// ===================================================================================
add({
  id: 'eiskalt', icon: '🧊', title: 'Eiskalt', tag: 'Meeresforschungsinstitut Kiel, heute',
  blurb: 'Eine Forscherin wird im Kühlraum ihres Instituts gefunden – erfroren. Die Tür ließ sich von innen nicht öffnen.',
  victim: 'Dr. Jana Holst, 41, Meeresbiologin',
  intro: [
    'Kiel, ein grauer Montagmorgen im November. Im Institut für Meeresforschung brummen die Kühlaggregate wie immer.',
    'Um 7 Uhr 30 öffnet Hausmeister Ole Jensen den großen Kühlraum, minus 25 Grad. Auf dem Boden liegt Dr. Jana Holst. Sie ist erfroren. Gestorben irgendwann am Sonntagabend.',
    'Der Innengriff der Tür fehlt. Wer in diesem Raum eingeschlossen wird, kommt nicht mehr heraus.',
    'Ein tragischer Unfall – oder hat jemand die Tür hinter ihr zugedrückt? Ermittelt!'
  ],
  chars: [
    { name: 'Prof. Martin Reuter', emoji: '👔', role: 'Institutsleiter', g: 'm', guilty: true },
    { name: 'Lisa Kramer', emoji: '🔬', role: 'Doktorandin', g: 'f' },
    { name: 'Ole Jensen', emoji: '🔑', role: 'Hausmeister', g: 'm' },
    { name: 'Dr. Erik Lund', emoji: '🧑‍🔬', role: 'Kollege und Ex-Mann des Opfers', g: 'm' },
    { name: 'Mareike Holst', emoji: '📱', role: 'Schwester des Opfers', g: 'f' },
    { name: 'Prof. Annette Brink', emoji: '⚖️', role: 'Ethikkommission der Universität', g: 'f' }
  ],
  acts: [
    { title: 'Der Kühlraum',
      text: 'Die Spurensicherung untersucht den Kühlraum. Der Innengriff der Tür ist abgeschraubt, die Schrauben fehlen. Janas Smartwatch hat alles aufgezeichnet. Und das Zugangsprotokoll des Instituts zeigt: Am Sonntag wurden nur zwei Ausweise benutzt.',
      evidence: [
        { ic: '🚪', name: 'Kühlraumtür', text: 'Der Innengriff ist abgeschraubt. Von innen lässt sich die Tür nicht öffnen.' },
        { ic: '⌚', name: 'Janas Smartwatch', text: 'Sie hat Puls und Bewegung des ganzen Abends gespeichert.' },
        { ic: '🪪', name: 'Zugangsprotokoll', text: 'Sonntag: Jana Holst um 18:30 Uhr. Hausmeister Jensen bei seinen Rundgängen. Sonst niemand.' }
      ] },
    { title: 'Die Daten',
      text: 'Auf Janas Laptop finden die Ermittler einen E-Mail-Entwurf, der erst am Montag um 8 Uhr verschickt werden sollte. Auf ihrem Handy ihre letzte Nachricht vom Sonntagabend. Und im Labor neben dem Kühlraum stehen zwei benutzte Kaffeebecher.',
      evidence: [
        { ic: '📧', name: 'Geplante E-Mail', text: 'An die Ethikkommission, Versand Montag 8:00 Uhr. Betreff: „Projekt OCEAN-7“.' },
        { ic: '💬', name: 'Letzte Nachricht', text: 'Sonntag, 18:58 Uhr, an ihre Schwester: „Treffe gleich M. wegen der Daten. Drück mir die Daumen.“' },
        { ic: '☕', name: 'Zwei Kaffeebecher', text: 'Im Labor neben dem Kühlraum. Jana hatte Besuch.' }
      ] },
    { title: 'Die Ladesäule',
      text: 'Vor dem Institut steht eine Ladesäule für Elektroautos. Die Betreiberfirma schickt auf Anfrage ihr Protokoll vom Sonntag. Im Büro des Institutsleiters liegt ein Werkzeugkasten. Und der Förderantrag für das Projekt OCEAN-7 wird von einer Gutachterin geprüft.',
      evidence: [
        { ic: '🔌', name: 'Protokoll der Ladesäule', text: 'Sonntag, 18:40 bis 19:35 Uhr: Ein Auto wurde am Institut geladen.' },
        { ic: '🧰', name: 'Werkzeugkasten', text: 'Aus dem Büro des Institutsleiters. Ein Schraubendreher-Bit fehlt.' },
        { ic: '📊', name: 'Förderantrag OCEAN-7', text: '2 Millionen Euro Fördergeld. Antragsteller: Prof. Martin Reuter.' }
      ] }
  ],
  solution: [
    'Der Täter ist Professor Martin Reuter, der Institutsleiter.',
    'Für seinen Förderantrag über zwei Millionen Euro hatte er Messdaten gefälscht. Jana Holst hatte es entdeckt und wollte am Montag die Ethikkommission informieren.',
    'Am Sonntagabend traf er sie im Institut. Er kam durch die Tiefgarage, ohne seinen Ausweis zu benutzen – aber sein Auto lud derweil an der Ladesäule. Den Innengriff des Kühlraums hatte er vorher abgeschraubt. Er lockte Jana hinein, um ihr angeblich Proben zu zeigen, und drückte die Tür hinter ihr zu.'
  ],
  keys: [ 'Ladesäule: Reuters Auto war am Sonntag da', 'Nachricht „Treffe gleich M. wegen der Daten“', 'Gefälschte Daten im Förderantrag = Motiv', 'Fehlendes Bit aus seinem Werkzeugkasten' ]
}, {
  talk: [
    [ 'Schrecklich. Jana war eine meiner besten Leute. Ich war den ganzen Sonntag zu Hause in Laboe, im Institut war ich nicht. Das Protokoll wird es Ihnen bestätigen.',
      '„M.“? Das kann jeder sein. Ihre Schwester heißt Mareike, nicht wahr? Und Frau Kramer war wütend auf Jana – die hat ihre Doktorarbeit zerrissen.',
      'Mein Auto? Das muss ein Fehler der Ladesäule sein. Und Werkzeug liegt hier überall herum. OCEAN-7 ist ein einwandfreies Projekt!' ],
    [ 'Ja, Jana hat mein Kapitel abgelehnt. Ich war wütend. Aber umbringen? Ich war am Sonntagabend im Konzert, in der Kieler Oper.',
      'Jana hat mir am Freitag gesagt, dass mit den Daten von OCEAN-7 etwas nicht stimmt. „Die Messreihen sind kopiert, Lisa. Eins zu eins.“',
      'Meine Konzertkarte wurde um 18:55 Uhr am Einlass gescannt, das Konzert ging bis 21:30 Uhr. Und ich habe noch etwas gesehen: Am Freitag war Professor Reuter mit einem Schraubendreher am Kühlraum. „Der Griff wackelt“, sagte er.' ],
    [ 'Ich habe sie heute früh gefunden. Am Sonntag habe ich meine Runden gedreht wie immer, um 18 und um 21 Uhr.',
      'Gegen zehn nach sieben brannte im Laborflügel Licht. Ich habe nicht nachgesehen, ehrlich gesagt – es lief das Fußballspiel.',
      'Über die Tiefgarage kommt man ohne Ausweis rein, wenn man einen Schlüssel für das Rolltor hat. Die Schlüssel haben nur ich und der Institutsleiter.' ],
    [ 'Jana und ich sind seit zwei Jahren geschieden. Wir haben um die Kinder gestritten, ja. Aber ich hätte ihr nie etwas getan.',
      'Am Sonntag war ich von 18 bis 20 Uhr mit den Kindern im Schwimmbad. Es gibt Fotos, und die Kassiererin kennt uns.',
      'Jana hat mir vor einer Woche gesagt: „Wenn das rauskommt, ist Martin erledigt. Und das Institut auch.“ Ich wusste nicht, was sie meinte.' ],
    [ 'Ich bin ihre Schwester. Jana hat mir am Sonntag kurz vor sieben noch geschrieben. Ich hätte zurückrufen sollen …',
      'Mit „M.“ war sicher nicht ich gemeint – mir hat sie die Nachricht ja geschickt! Sie meinte jemanden aus dem Institut.',
      'Jana hat mir letzten Monat erzählt, dass ihr Chef ihr eine Beförderung versprochen hat, wenn sie „die Sache mit den Daten vergisst“.' ],
    [ 'Ich leite die Ethikkommission. Frau Dr. Holst hat mich am Freitag angerufen. Sie sprach von einem schweren Fall von Datenfälschung.',
      'Sie wollte mir am Montag um acht alle Unterlagen schicken. Der Name des Verantwortlichen sollte in der E-Mail stehen.',
      'Ich habe den Antrag OCEAN-7 geprüft. Die Messreihen von 2023 und 2024 sind bis auf die letzte Kommastelle identisch. Das ist eindeutig gefälscht.' ]
  ],
  look: {
    'Kühlraumtür': 'Die Kratzer an den Schraublöchern sind frisch – der Griff wurde erst vor wenigen Tagen abgeschraubt.',
    'Janas Smartwatch': 'Um 19:04 Uhr schnellt der Puls in die Höhe. Danach heftige Bewegung an der Tür – sie wollte hinaus.',
    'Zugangsprotokoll': 'Das Rolltor der Tiefgarage wird nicht protokolliert. Wer einen Schlüssel hat, kommt unbemerkt herein.',
    'Geplante E-Mail': 'Im Entwurf: „Verantwortlich für die Fälschungen ist der Institutsleiter, Prof. M. Reuter.“',
    'Letzte Nachricht': 'Der Nachrichtenverlauf zeigt: Mit „M.“ meinte Jana in früheren Nachrichten immer ihren Chef – „M. ist heute wieder unausstehlich“.',
    'Zwei Kaffeebecher': 'Auf einem Becher: das Logo des Golfclubs Laboe. Professor Reuter ist dort Mitglied.',
    'Protokoll der Ladesäule': 'Geladen wurde mit der Ladekarte „Reuter, M. – privat“.',
    'Werkzeugkasten': 'Das fehlende Bit passt genau zu den Schrauben des Kühlraumgriffs.',
    'Förderantrag OCEAN-7': 'Die Messreihen von 2023 und 2024 sind bis auf die letzte Kommastelle identisch.'
  },
  motives: [ 'Gefälschte Forschungsdaten sollten auffliegen', 'Streit um die abgelehnte Doktorarbeit', 'Sorgerechtsstreit um die Kinder', 'Eifersucht unter Schwestern' ],
  keys: [ 'Protokoll der Ladesäule', 'Geplante E-Mail', 'Werkzeugkasten', 'Zwei Kaffeebecher' ]
});

// ===================================================================================
add({
  id: 'nachtzug', icon: '🚂', title: 'Nachtzug nach Wien', tag: 'Schlafwagen Hamburg – Wien, Winter 1987',
  blurb: 'Ein Kunsthändler liegt tot in Abteil 7. Sein wertvollstes Bild ist verschwunden – und der Zug hat nur einmal gehalten.',
  victim: 'Viktor Halm, 63, Kunsthändler aus Hamburg',
  intro: [
    'Januar 1987. Der Nachtzug von Hamburg nach Wien rattert durch die verschneite Nacht. Im Schlafwagen ist es still, nur die Räder schlagen ihren Takt.',
    'Um 6 Uhr 10 klopft der Schaffner an Abteil 7, um den Morgenkaffee zu bringen. Keine Antwort. Der Kunsthändler Viktor Halm liegt tot in seiner Koje, erdrosselt mit seinem eigenen Seidenschal.',
    'Aus seinem Koffer fehlt eine kleine Zeichnung von Lyonel Feininger, Wert vierhunderttausend Mark. Halm wollte sie in Wien verkaufen.',
    'Der Zug hat in der Nacht nur einmal gehalten: in Nürnberg, um 2 Uhr 14, für fünf Minuten. Der Täter ist also noch an Bord.'
  ],
  chars: [
    { name: 'Henri Laval', emoji: '🖼️', role: 'Kunstexperte aus Paris', g: 'm', guilty: true },
    { name: 'Paula Halm', emoji: '👜', role: 'Nichte und Assistentin des Opfers', g: 'f' },
    { name: 'Werner Kluge', emoji: '🎫', role: 'Schlafwagenschaffner', g: 'm' },
    { name: 'Irina Wolkow', emoji: '🎤', role: 'Opernsängerin', g: 'f' },
    { name: 'Benno Lorenz', emoji: '🧳', role: 'Handelsvertreter', g: 'm' },
    { name: 'Schwester Agnes', emoji: '📿', role: 'Ordensschwester', g: 'f' }
  ],
  acts: [
    { title: 'Abteil 7',
      text: 'Der Schaffner hat Abteil 7 verriegelt, bis die Polizei in Wien zusteigt. Auf dem Boden liegt der Seidenschal. Der kleine Bilderkoffer ist offen und leer. Und auf dem Klapptisch liegt Halms Notizbuch, aufgeschlagen bei der letzten Seite.',
      evidence: [
        { ic: '🧣', name: 'Seidenschal', text: 'Halms eigener Schal. Die Tatwaffe.' },
        { ic: '💼', name: 'Leerer Bilderkoffer', text: 'Die Feininger-Zeichnung fehlt. Das Schloss wurde nicht aufgebrochen.' },
        { ic: '📓', name: 'Halms Notizbuch', text: 'Letzter Eintrag: „Wien – L. zur Rede stellen!“' }
      ] },
    { title: 'Halt in Nürnberg',
      text: 'Bei einer Kontrolle des Zuges findet der Schaffner Halms Brieftasche – in der Manteltasche eines Fahrgastes. Im Gepäck des Toten steckt ein Telegramm, das er noch in Hamburg erhalten hat. Und im Speisewagen liegt eine Rechnung von heute Nacht.',
      evidence: [
        { ic: '👛', name: 'Halms Brieftasche', text: 'Gefunden im Mantel von Benno Lorenz. Das Bargeld fehlt.' },
        { ic: '📨', name: 'Telegramm aus Wien', text: 'An Viktor Halm, Hamburg: „Gutachten liegt vor. Bitte dringend rückrufen. Albertina.“' },
        { ic: '🧾', name: 'Rechnung aus dem Speisewagen', text: 'Ein Glas Sekt, zwei Kaffee – bezahlt um 1:58 Uhr.' }
      ] },
    { title: 'Die Leinwandstreifen',
      text: 'Beim Leeren der Abfallbehälter macht der Schaffner einen Fund: In der Toilette von Wagen 3 liegen zerschnittene Papierstreifen, mit Tusche bemalt. Dazu kommen ein alter Kaufvertrag aus Halms Akten und eine kleine Nagelschere, die im Gang gefunden wurde.',
      evidence: [
        { ic: '✂️', name: 'Zerschnittene Zeichnung', text: 'In der Toilette von Wagen 3. Reste der Feininger-Zeichnung, in Streifen geschnitten.' },
        { ic: '📃', name: 'Kaufvertrag von 1986', text: 'Halm kaufte die Feininger-Zeichnung für 380.000 Mark. Verkäufer: Henri Laval.' },
        { ic: '💅', name: 'Nagelschere', text: 'Im Gang vor Wagen 3 gefunden. Silber, mit Monogramm.' }
      ] }
  ],
  solution: [
    'Der Täter ist Henri Laval, der Kunstexperte aus Paris.',
    'Er hatte Viktor Halm die Feininger-Zeichnung verkauft – eine Fälschung. Ein Gutachten der Albertina in Wien hatte das gerade aufgedeckt. In Wien wollte Halm ihn zur Rede stellen und anzeigen.',
    'Um 1 Uhr 45 klopfte Laval an Abteil 7. Halm nannte ihn einen Betrüger, und Laval erdrosselte ihn mit seinem Schal. Dann nahm er die gefälschte Zeichnung mit, zerschnitt sie in der Toilette seines eigenen Wagens und hoffte, dass niemand je von der Fälschung erfahren würde.'
  ],
  keys: [ 'Zerschnittene Fälschung in Lavals Wagen', 'Kaufvertrag: Laval hat die Zeichnung verkauft', '„L. zur Rede stellen“ + Telegramm über das Gutachten', 'Schwester Agnes hörte „Sie Betrüger!“' ]
}, {
  talk: [
    [ 'Quelle horreur! Ich habe um elf eine Schlaftablette genommen und bis zum Morgen geschlafen. Ich wohne in Wagen 3, weit weg von Abteil 7.',
      '„L.“? Denken Sie doch an diesen Lorenz – er hatte die Brieftasche! Monsieur Halm und ich waren Geschäftsfreunde, seit Jahren.',
      'Ja, ich habe ihm die Zeichnung verkauft. Ein echter Feininger, das schwöre ich! Und Nagelscheren besitzt doch jeder Reisende.' ],
    [ 'Ich bin Viktors Nichte und arbeite für ihn. Ja, ich erbe die Galerie. Aber ich habe ihn geliebt wie einen Vater.',
      'Ich war bis kurz vor zwei im Speisewagen, ich konnte nicht schlafen. Die Rechnung habe ich bezahlt. Der Kellner hat mich die ganze Zeit gesehen.',
      'In Hamburg hat Viktor das Telegramm aus Wien gelesen und ist kreidebleich geworden. Er sagte nur: „Ich bin betrogen worden. Und ich weiß von wem.“' ],
    [ 'Ich habe Herrn Halm um elf das Bett gemacht. Da war alles in Ordnung. Um zehn nach sechs habe ich ihn gefunden.',
      'Um 1 Uhr 40 sah ich Herrn Laval im Gang vor Wagen 2 – angezogen, mit Schuhen. Er sagte, er suche den Speisewagen.',
      'Gegen fünf nach zwei, kurz vor Nürnberg, kam Herr Laval zurück in seinen Wagen. Er hatte es sehr eilig und hielt die Hand in der Manteltasche.' ],
    [ 'Viktor und ich … wir waren vor Jahren ein Paar. Beim Abendessen haben wir gestritten, ja. Er schuldet mir noch Geld für ein Bild.',
      'Ich war allein in meinem Abteil und habe Tonleitern im Kopf geübt. Ein Alibi habe ich nicht, das gebe ich zu.',
      'Beim Abendessen hat Viktor zu diesem Franzosen gesagt: „In Wien reden wir über Ihren Feininger, Laval.“ Laval ist ganz weiß geworden.' ],
    [ 'Ich bin Vertreter für Staubsauger. Mit dem Toten hatte ich nichts zu tun.',
      'Gut, gut. Die Brieftasche habe ich ihm am Abend im Speisewagen aus dem Jackett gezogen. Das ist mein … Nebenberuf. Aber umgebracht habe ich niemanden! Um eins habe ich schon geschnarcht – fragen Sie meinen Abteilnachbarn.',
      'Im Gang vor Wagen 3 hat jemand gegen zwei etwas fallen lassen. Ein kleines Klirren, wie Metall. Ich war zu faul, nachzusehen.' ],
    [ 'Ich kann nachts kaum schlafen und bete im Gang am Fenster. Ich habe vieles gehört, mein Kind.',
      'Gegen Viertel vor zwei hörte ich aus Abteil 7 eine wütende Stimme: „Sie Betrüger!“ Dann wurde es still.',
      'Kurz darauf ging ein Herr mit französischem Akzent an mir vorbei. Er murmelte „Pardon, ma sœur“ und eilte nach hinten, Richtung Wagen 3.' ]
  ],
  look: {
    'Seidenschal': 'Zwischen den Fasern: ein feines Haar mit Pomade. Halm trug keine Pomade.',
    'Leerer Bilderkoffer': 'Das Schloss wurde mit dem Schlüssel geöffnet, der noch steckt. Halm kannte seinen Besucher und hat selbst aufgemacht.',
    'Halms Notizbuch': 'Weiter oben auf der Seite: „Gutachten Albertina: Fälschung! 380.000 Mark – Laval!“',
    'Halms Brieftasche': 'Benno hat nur das Bargeld genommen. Halms Ausweise und die Fahrkarte stecken noch darin – ein Dieb, kein Mörder.',
    'Telegramm aus Wien': 'Rückseite, in Halms Handschrift: „Fälschung bestätigt. Strafanzeige in Wien.“',
    'Rechnung aus dem Speisewagen': 'Auf der Rückseite hat der Kellner notiert: „Fräulein Halm, Tisch 4, von 0:30 bis 1:58 Uhr.“',
    'Zerschnittene Zeichnung': 'Unter der Lupe: Die Tusche ist modern, keine 20 Jahre alt. Die Zeichnung war eine Fälschung.',
    'Kaufvertrag von 1986': 'Laval garantiert im Vertrag die Echtheit – und haftet mit seinem Vermögen, falls es eine Fälschung ist.',
    'Nagelschere': 'Das Monogramm lautet „H. L.“ An den Klingen kleben Tuschereste.'
  },
  motives: [ 'Die verkaufte Zeichnung war eine Fälschung – Anzeige drohte', 'Das Erbe der Galerie', 'Alte Liebe und offene Schulden', 'Beute aus dem Diebstahl' ],
  keys: [ 'Zerschnittene Zeichnung', 'Nagelschere', 'Kaufvertrag von 1986', 'Halms Notizbuch' ]
});
})();
