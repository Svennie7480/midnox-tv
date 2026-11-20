// midnox Krimi-Abend – die Fälle. Eigene Geschichten, frei erfunden.
// Figuren 0–3 sind Pflicht (einer davon ist der Täter), 4–5 kommen erst ab 5 bzw. 6 Mitspielern dazu.
// Die öffentlichen Akte (acts) erwähnen die Zusatzfiguren nie – sie liefern nur Bonus-Hinweise.
// clues[a] = geheimer Hinweis für die Figur im Akt a (0..2), erscheint nur auf dem eigenen Handy.
window.CASES = [
{
  id: 'seeblick', icon: '🌊', title: 'Mord im Hotel Seeblick', tag: 'Timmendorfer Strand, Herbst 1958',
  blurb: 'Sturmnacht an der Ostsee. Der Hotelier liegt tot in seinem Büro – und die Wanduhr ist um halb elf stehen geblieben.',
  victim: 'Konrad Wiese, 62, Besitzer des Hotels Seeblick',
  intro: [
    'Timmendorfer Strand, im Oktober 1958. Draußen peitscht der Sturm die Ostsee gegen den Strand, drinnen im Hotel Seeblick knistert der Kamin.',
    'Um 23 Uhr 15 findet der Hausdiener Fritz den Hotelbesitzer Konrad Wiese in seinem Büro im ersten Stock. Erschlagen. Neben ihm liegt der schwere Messing-Leuchtturm, der sonst auf seinem Schreibtisch stand.',
    'Wegen des Sturms ist die Küstenstraße gesperrt. Niemand hat das Hotel heute Nacht verlassen. Der Mörder ist also noch hier – an diesem Tisch.',
    'Wachtmeister Harms aus dem Ort hat alle in der Halle versammelt. Stellt euch vor!'
  ],
  chars: [
    { name: 'Greta Wiese', g: 'f', emoji: '💍', role: 'Die Ehefrau des Opfers',
      intro: 'Ich bin Greta Wiese. Seit 31 Jahren bin ich mit Konrad verheiratet, und ich habe dieses Hotel mit ihm zusammen aufgebaut. Ich kann nicht glauben, dass er tot ist.',
      secret: 'Du wolltest dich von Konrad scheiden lassen. Er war kalt und geizig geworden. Heute Abend hast du heimlich deinen Anwalt in Hamburg angerufen. Das ist dir peinlich, deshalb sagst du zuerst: „Ich hatte Kopfschmerzen und war auf meinem Zimmer.“',
      story: 'Du bist unschuldig. Gib dein Telefonat erst zu, wenn man dich in die Enge treibt – oder wenn es dich entlastet.',
      clues: [
        'Du hast Konrad um 22:20 Uhr zuletzt gesehen. Er war wütend und murmelte etwas von „Zahlen, die nicht stimmen“.',
        'Aus der Telefonzelle in der Halle hast du gegen 22:50 Uhr gesehen, wie jemand in einem grauen Kostüm die Treppe zum Büro hinaufging. Das Gesicht hast du nicht erkannt.',
        'Das Fernamt hat dein Gespräch notiert: 22:31 bis 23:02 Uhr. Das ist dein Alibi – aber es verrät auch deine Scheidungspläne.'
      ] },
    { name: 'Henning Wiese', g: 'm', emoji: '🎲', role: 'Der Sohn, Lebemann',
      intro: 'Henning Wiese, der Sohn. Mein Vater und ich waren nicht immer einer Meinung, aber so etwas … so etwas hat niemand verdient.',
      secret: 'Du hast 8.000 Mark Spielschulden beim Kasino in Travemünde. Um halb zehn hast du Vater um Geld gebeten – er hat dich angeschrien und abgelehnt. Von 22:30 bis 23:10 Uhr warst du draußen am Seesteg und hast einem Mann vom Kasino, Herrn Kalle, deine goldene Armbanduhr als Pfand gegeben.',
      story: 'Du bist unschuldig. Du schämst dich für die Schulden und behauptest anfangs, du seist auf deinem Zimmer gewesen.',
      clues: [
        'Alle haben euren Streit um 21:30 Uhr gehört. Gib zu, dass ihr gestritten habt – aber erzähl nicht gleich, worum es ging.',
        'Auf dem Weg zum Steg, gegen 22:35 Uhr, hast du Vater noch oben am Bürofenster stehen sehen. Er lebte also nach halb elf noch!',
        'Auf dem Rückweg um 23:05 Uhr hast du durchs Treppenhausfenster gesehen, wie Fräulein Martens aus dem Büroflur kam und sich die Hände mit einem Taschentuch abwischte. Herr Kalle kann bezeugen, dass du bis 23:10 am Steg warst.'
      ] },
    { name: 'Ilse Martens', g: 'f', emoji: '📒', role: 'Die Buchhalterin', guilty: true,
      intro: 'Ilse Martens. Ich führe seit zwölf Jahren die Bücher des Seeblick. Herr Wiese war ein strenger, aber gerechter Chef.',
      secret: 'DU BIST DIE TÄTERIN. Seit vier Jahren hast du rund 30.000 Mark aus der Hotelkasse abgezweigt. Heute um 22:45 Uhr hat Konrad dich ins Büro bestellt: Er hatte alles entdeckt und wollte morgen die Polizei rufen. Gegen 22:50 Uhr hast du ihn mit dem Messing-Leuchtturm erschlagen. Dann hast du die Wanduhr zerschlagen und auf 22:30 gestellt – da hast du noch mit dem Kapitän Karten gespielt. Die Seiten aus dem Kassenbuch hast du im Ofen verbrannt.',
      story: 'Deine Tarngeschichte: Du hast von 22 Uhr bis zum Fund mit dem Kapitän an der Bar Rommé gespielt und warst höchstens kurz zum Frischmachen weg. Lüge, lenke ab, bleib ruhig – niemand darf dich überführen!',
      clues: [
        'Die Uhr zeigt 22:30 – genau als du noch mit dem Kapitän gespielt hast. Bleib dabei: Du warst den ganzen Abend an der Bar.',
        'Gefahr: Der Küchenjunge hat um 22:40 Uhr Tee ins Büro gebracht. Wenn das rauskommt, stimmt die Uhr nicht! Lenk ab: Henning brauchte dringend Geld, und Greta wirkt seltsam gefasst für eine Witwe.',
        'Die Asche im Ofen verrät dich. Behaupte, Konrad habe selbst alte Unterlagen verbrannt, weil er „aufräumen“ wollte. Und falls jemand den Fleck an deinem Ärmel bemerkt: Das sei Tinte von der Abrechnung.'
      ] },
    { name: 'Kapitän Johann Petersen', g: 'm', emoji: '⚓', role: 'Alter Freund des Hauses',
      intro: 'Kapitän Johann Petersen, 40 Jahre auf See. Konrad und ich kennen uns seit der Schulzeit. Ich wohne jeden Herbst im Seeblick.',
      secret: 'Du wolltest das Hotel kaufen und hast Konrad heute ein großzügiges Angebot gemacht – er hat abgelehnt. Außerdem hast du ihm vor Jahren Geld geliehen, das noch nicht zurückgezahlt ist. Das macht dich verdächtig, aber du bist unschuldig.',
      story: 'Du bist unschuldig. Du trinkst gern einen Rum zu viel, daher bist du dir bei Uhrzeiten nicht immer sicher.',
      clues: [
        'Du hast ab 22 Uhr mit Ilse Martens an der Bar Rommé gespielt. Das kannst du ruhig erzählen.',
        'Wenn du ehrlich bist: Ilse ist gegen 22:40 Uhr aufgestanden – „die Abrechnung“ – und kam erst gegen 23 Uhr zurück, ziemlich blass. Du hattest aber schon drei Rum …',
        'Konrad hat dir gestern erzählt, die Bilanzen sähen „merkwürdig“ aus und er wolle „jemandem im Haus auf den Zahn fühlen“.'
      ] },
    { name: 'Viktor Lamm', g: 'm', emoji: '🎹', role: 'Der Barpianist',
      intro: 'Viktor Lamm, ich spiele jeden Abend am Flügel in der Bar. Ich sehe viel – und ich vergesse wenig.',
      secret: 'Du stiehlst ab und zu Kleinigkeiten von Gästen. Heute hast du Gretas Perlenbrosche „gefunden“ und eingesteckt. Mit dem Mord hast du nichts zu tun – aber durchsuchen lassen willst du dich nicht.',
      story: 'Du bist unschuldig. Vom Flügel aus siehst du die ganze Bar.',
      clues: [
        'Du hast den ganzen Abend gespielt, von 21 Uhr bis zum Fund. Der Kapitän saß die ganze Zeit an der Bar.',
        'Ilse Martens kam gegen 23 Uhr an die Bar zurück, mit einem dunklen Fleck am Ärmel, und bestellte sofort einen doppelten Korn. Sonst trinkt sie nie.',
        'Gegen 22:40 Uhr hast du Ilse an der Bar vorbei zur Treppe gehen sehen – nicht zu den Toiletten, die liegen in die andere Richtung.'
      ] },
    { name: 'Dr. Clara Hesse', g: 'f', emoji: '🩺', role: 'Kurärztin, Gast des Hotels',
      intro: 'Dr. Clara Hesse, ich bin Ärztin und zur Kur hier. Ich habe den Toten als Erste untersucht.',
      secret: 'Du hast ein Verhältnis mit dem Kapitän – ganz harmlos, aber diskret soll es bleiben. Du bist unschuldig.',
      story: 'Du bist unschuldig. Als Ärztin kannst du sachliche Hinweise geben.',
      clues: [
        'Ein einziger Schlag auf den Hinterkopf. Der Körper war bei deiner Untersuchung um 23:20 Uhr noch warm – der Tod trat höchstens eine halbe Stunde vorher ein.',
        'Die Teetasse auf dem Schreibtisch war halb leer und der Tee noch lauwarm. So schnell kühlt Tee nicht ab – er kann nicht vor 22:30 Uhr gebracht worden sein.',
        'Am Messing-Leuchtturm klebt neben Blut ein grauer Wollfaden – wie von einem Kostüm.'
      ] }
  ],
  acts: [
    { title: 'Der Tote im Büro',
      text: 'Wachtmeister Harms hat das Büro abgesperrt. Konrad Wiese wurde mit einem einzigen Schlag getötet. Die Wanduhr liegt zerschlagen am Boden, ihre Zeiger stehen auf 22 Uhr 30. Im Kachelofen glimmt frische Asche – dabei war es heute gar nicht kalt im Büro.',
      evidence: [
        { ic: '🗼', name: 'Messing-Leuchtturm', text: 'Die Tatwaffe. Schwer, blutig, keine brauchbaren Fingerabdrücke.' },
        { ic: '🕥', name: 'Zerschlagene Wanduhr', text: 'Stehen geblieben um 22:30 Uhr. Das Glas ist von außen eingeschlagen.' },
        { ic: '🔥', name: 'Asche im Ofen', text: 'Frisch verbranntes Papier. Wer heizt an einem milden Abend?' }
      ],
      ask: [ { c: 1, q: 'Worüber haben Sie um halb zehn so laut mit Ihrem Vater gestritten?' },
             { c: 0, q: 'Wo waren Sie zwischen halb elf und elf Uhr?' } ] },
    { title: 'Das Tee-Tablett',
      text: 'Der Küchenjunge Paul meldet sich zitternd beim Wachtmeister. Um 22 Uhr 40 hat er Herrn Wiese eine Kanne Tee ins Büro gebracht. Der Chef lebte, war wütend und sagte nur: „Sagen Sie unten Bescheid, ich erwarte noch Besuch.“ Im Flur entdeckt Harms außerdem Spuren von nassem Sand.',
      evidence: [
        { ic: '🫖', name: 'Teekanne', text: 'Um 22:40 Uhr ins Büro gebracht. Konrad lebte da noch – die Uhr lügt!' },
        { ic: '👣', name: 'Sandspuren im Flur', text: 'Nasser Strandsand, Herrenschuhe. Jemand war heute Nacht draußen am Wasser.' },
        { ic: '✉️', name: 'Brief vom Kasino Travemünde', text: 'Gefunden in Hennings Jacke: „Letzte Mahnung – 8.000 DM bis Freitag.“' }
      ],
      ask: [ { c: 2, q: 'Haben Sie die Bar heute Abend irgendwann verlassen?' },
             { c: 3, q: 'Wie lange genau hat Fräulein Martens mit Ihnen Karten gespielt?' } ] },
    { title: 'Asche und Zahlen',
      text: 'Wachtmeister Harms hat die Asche aus dem Ofen gesiebt. Darin: verkohlte Seiten des Kassenbuchs, eindeutig in der Handschrift der Buchhaltung. Und das Fernamt bestätigt per Telefon, dass heute Abend aus der Halle ein langes Ferngespräch nach Hamburg geführt wurde – von 22 Uhr 31 bis 23 Uhr 02.',
      evidence: [
        { ic: '📕', name: 'Verkohltes Kassenbuch', text: 'Die Seiten der Jahre 1955 bis 1958 wurden herausgerissen und verbrannt.' },
        { ic: '☎️', name: 'Protokoll des Fernamts', text: 'Gespräch aus der Hotelhalle nach Hamburg, 22:31–23:02 Uhr. Wer hat telefoniert?' },
        { ic: '⌚', name: 'Fehlende Armbanduhr', text: 'Henning trägt sonst immer seine goldene Uhr. Heute nicht.' }
      ],
      ask: [ { c: 2, q: 'Warum fehlen ausgerechnet die Seiten der letzten drei Jahre im Kassenbuch?' },
             { c: 1, q: 'Wo ist eigentlich Ihre goldene Armbanduhr?' } ] }
  ],
  solution: [
    'Die Täterin ist Ilse Martens, die Buchhalterin.',
    'Seit vier Jahren hatte sie Geld aus der Hotelkasse abgezweigt. Heute Abend hatte Konrad Wiese den Betrug entdeckt. Er bestellte sie um 22 Uhr 45 ins Büro und drohte mit der Polizei. Kurz darauf erschlug sie ihn mit dem Messing-Leuchtturm.',
    'Dann stellte sie die Wanduhr auf 22 Uhr 30 und zerschlug sie – zu dieser Zeit saß sie noch mit dem Kapitän beim Kartenspiel. Doch der Tee um 22 Uhr 40 und Henning am Fenster bewiesen: Konrad lebte nach halb elf noch. Die verbrannten Seiten des Kassenbuchs verrieten ihr Motiv.'
  ],
  keys: ['Tee um 22:40 – die Uhr wurde verstellt', 'Ilse verließ die Bar gegen 22:40', 'Verbrannte Kassenbuch-Seiten = Motiv', 'Greta: Telefonat 22:31–23:02 · Henning: am Steg bis 23:10']
},
{
  id: 'huette', icon: '🏔️', title: 'Lawine der Lügen', tag: 'Eingeschneite Berghütte im Allgäu, heute',
  blurb: 'Eine Influencerin stürzt von der Hüttenterrasse in den Tod. Ihr Handy ist verschwunden – und draußen tobt der Schneesturm.',
  victim: 'Lara Sommer, 29, Reise-Influencerin mit zwei Millionen Followern',
  intro: [
    'Die Edelweißhütte auf 1.900 Metern. Seit dem Nachmittag schneit es ohne Pause, die Seilbahn steht still. Bis morgen früh kommt niemand herauf und niemand hinunter.',
    'Um 21 Uhr 40 wird Lara Sommer, die bekannte Reise-Influencerin, unterhalb der Terrasse gefunden. Sieben Meter tiefer, in einer Schneewechte. Tot.',
    'Das Geländer ist hüfthoch. Einfach so fällt da niemand hinüber. Und Laras Handy, ohne das sie nie einen Schritt macht, ist spurlos verschwunden.',
    'Bis die Bergwacht kommt, müsst ihr selbst herausfinden, wer von euch lügt. Stellt euch vor!'
  ],
  chars: [
    { name: 'Sepp Gruber', g: 'm', emoji: '🧔', role: 'Der Hüttenwirt', guilty: true,
      intro: 'Sepp Gruber, mir gehört die Edelweißhütte, schon in dritter Generation. Ich stand den ganzen Abend in der Küche, wie jeden Abend.',
      secret: 'DU BIST DER TÄTER. Vor sechs Jahren, am 12. März, starb dein Sohn Florian auf der Passstraße – Fahrerflucht, ein roter Kleinwagen, nie gefunden. Beim Abendessen erzählte Lara lachend von „damals mit 23, mein roter Mini, irgendwas angefahren auf dem Pass, bin einfach weitergefahren“. Um 21:15 Uhr hast du sie auf der Terrasse zur Rede gestellt. Sie hat gelacht. Da hast du sie gepackt und übers Geländer gestoßen. Ihr Handy mit der Story hast du ins Regenfass hinter der Küche geworfen.',
      story: 'Deine Tarngeschichte: Du warst den ganzen Abend in der Küche und hast Kaiserschmarrn gemacht. Bleib ruhig, sei der freundliche Wirt – und lenk den Verdacht auf andere!',
      clues: [
        'Auf der Terrasse sind Spuren von großen Bergschuhen, Größe 45. Jonas trägt auch 45 – erwähn das ruhig.',
        'Gefahr: Dein Kaiserschmarrn ist um 21:20 Uhr verbrannt, weil du nicht in der Küche warst. Sag, du warst „kurz im Keller, Bier holen“. Und erinnere alle daran, dass Tobias heute gefeuert wurde.',
        'Das Foto von Florian hängt hinter der Theke. Wenn jemand fragt: Sag, es ist dein Neffe. Und das Regenfass? „Da kommt jeder hin, die Tür ist nie abgeschlossen.“'
      ] },
    { name: 'Tobias Brandt', g: 'm', emoji: '💼', role: 'Laras Manager',
      intro: 'Tobias Brandt, ich bin seit fünf Jahren Laras Manager. Ohne mich wäre sie nie so groß geworden – das wusste sie auch.',
      secret: 'Du hast 40.000 Euro Sponsorengeld für dich abgezweigt. Lara hat es herausgefunden und dich beim Abendessen um 20:45 Uhr vor allen gefeuert. Von 21:00 bis 21:35 Uhr saßt du in der Gaststube am Laptop und hast verzweifelt E-Mails gelöscht.',
      story: 'Du bist unschuldig. Du willst nicht, dass die Sache mit dem Geld herauskommt.',
      clues: [
        'Alle haben gehört, wie Lara dich gefeuert hat. Behaupte, es ging um „unterschiedliche Vorstellungen“.',
        'Von der Gaststube aus hast du gegen 21:15 Uhr gehört, wie die Küchentür zur Terrasse knarrte. Gesehen hast du nichts.',
        'Der WLAN-Router der Hütte protokolliert jede Verbindung: Dein Laptop war von 21:00 bis 21:35 Uhr ununterbrochen aktiv – in der Gaststube. Das ist dein Alibi.'
      ] },
    { name: 'Mia Sommer', g: 'f', emoji: '📸', role: 'Laras jüngere Schwester',
      intro: 'Ich bin Mia, Laras Schwester. Ich mache die meisten Fotos für ihren Kanal. Wir waren … sehr eng.',
      secret: 'Die schönsten Bilder auf Laras Kanal sind von dir – sie hat dich nie erwähnt, und das hat dich wütend gemacht. Außerdem erbst du alles. Von 21:00 bis 21:40 Uhr warst du in der Sauna im Keller. Deine Handschuhe hattest du Lara am Nachmittag geliehen.',
      story: 'Du bist unschuldig. Du hast Angst, dass dein Erbe dich verdächtig macht.',
      clues: [
        'Lara hat dich um 20:50 Uhr noch angestrahlt und gesagt: „Heute erzähl ich endlich die Mini-Geschichte in meiner Story.“ Du weißt nicht, was sie damit meinte.',
        'Beim Abendessen erzählte Lara lachend, wie sie vor sechs Jahren mit ihrem roten Mini auf dem Pass „irgendwas angefahren“ hat und einfach weitergefahren ist. In dem Moment ließ Sepp ein Glas fallen.',
        'Die Sauna hat eine Zeitschaltuhr: Sie lief von 21:00 bis 21:40 Uhr, und du hast sie selbst gestartet. Das ist dein Alibi.'
      ] },
    { name: 'Jonas Keller', g: 'm', emoji: '🧗', role: 'Der Bergführer',
      intro: 'Jonas Keller, Bergführer. Ich habe die Gruppe heute heraufgeführt. Lara wollte unbedingt noch Sonnenuntergangs-Fotos auf dem Grat.',
      secret: 'Du hattest seit Monaten eine heimliche Affäre mit Lara. Um 20:30 Uhr hat sie per Nachricht Schluss gemacht: „Es ist aus.“ Von 21:10 bis 21:30 Uhr warst du allein im Skiraum und hast eine Bindung repariert.',
      story: 'Du bist unschuldig. Die Affäre willst du verschweigen – sie macht dich verdächtig.',
      clues: [
        'Du hast Lara um 21:05 Uhr zuletzt gesehen: Sie ging mit ihrem Handy Richtung Terrasse. „Ich mach noch eine Story“, sagte sie.',
        'Im Skiraum hast du gegen 21:15 Uhr durch das kleine Fenster jemanden mit Küchenschürze über die Terrasse gehen sehen. Bei dem Schneetreiben war es nur ein Schatten.',
        'Von 21:18 bis 21:24 Uhr hast du aus dem Skiraum per Funk mit der Bergwacht über das Wetter gesprochen. Das Funkprotokoll beweist es.'
      ] },
    { name: 'Nele Wagner', g: 'f', emoji: '🎥', role: 'Kamerafrau des Teams',
      intro: 'Nele Wagner, ich filme für Laras Kanal. Heute hätte unser großes Hütten-Video entstehen sollen.',
      secret: 'Du hast heimlich einen Job bei Laras größter Konkurrentin angenommen und wolltest morgen kündigen. Du bist unschuldig.',
      story: 'Du bist unschuldig. Deine Kamera lief am Abend für Stimmungsbilder in der Gaststube.',
      clues: [
        'Laras Stimmung kippte beim Abendessen, als Sepp ihr sehr lange und sehr still über die Theke hinweg zusah.',
        'Um 21:20 Uhr wolltest du Wasser aus der Küche holen. Die Küche war leer, und in der Pfanne verbrannte der Kaiserschmarrn.',
        'Auf deiner Kamera ist um 21:24 Uhr zu sehen, wie Sepp durch die Küchentür hereinkommt – Schnee auf den Schultern, keine Bierkiste in der Hand.'
      ] },
    { name: 'Dr. Frank Ostermann', g: 'm', emoji: '⛑️', role: 'Arzt und Hüttengast',
      intro: 'Frank Ostermann, ich bin Notarzt und mache hier oben Skitouren-Urlaub. Ich habe Lara untersucht, als man sie gefunden hat.',
      secret: 'Du bist wegen eines Behandlungsfehlers suspendiert und willst nicht, dass das jemand erfährt. Du bist unschuldig.',
      story: 'Du bist unschuldig. Als Arzt kannst du sachliche Hinweise geben.',
      clues: [
        'Lara hatte frische Druckstellen an beiden Oberarmen – jemand hat sie mit großen, kräftigen Händen gepackt.',
        'Der Sturz geschah ungefähr zwischen 21:10 und 21:25 Uhr. Später nicht, dafür war sie zu ausgekühlt.',
        'Unter Laras Fingernägeln ist Mehl. Sie hat sich offenbar an jemandem festgekrallt, der in der Küche gearbeitet hat.'
      ] }
  ],
  acts: [
    { title: 'Absturz in die Wechte',
      text: 'Jonas und Mia haben Lara um 21 Uhr 40 gefunden. Auf der verschneiten Terrasse sind die Spuren schon halb verweht. Was bleibt: Abdrücke großer Bergschuhe direkt am Geländer, ein einzelner Handschuh – und kein Handy. Lara hat es nie aus der Hand gelegt.',
      evidence: [
        { ic: '🥾', name: 'Schuhabdrücke am Geländer', text: 'Große Bergschuhe, etwa Größe 45. Zwei Personen standen dicht beieinander.' },
        { ic: '🧤', name: 'Ein Damenhandschuh', text: 'Auf der Terrasse gefunden. Er gehört Mia – aber Lara trug ihn heute.' },
        { ic: '📵', name: 'Das verschwundene Handy', text: 'Laras Handy ist weg. Hat jemand etwas darauf gesehen, das niemand sehen sollte?' }
      ],
      ask: [ { c: 1, q: 'Warum hat Lara Sie beim Abendessen vor allen gefeuert?' },
             { c: 3, q: 'Wann haben Sie Lara zuletzt gesehen – und in welcher Beziehung standen Sie zu ihr?' } ] },
    { title: 'Verbrannter Kaiserschmarrn',
      text: 'In der Küche riecht es noch immer verbrannt: In der Pfanne liegt ein verkohlter Kaiserschmarrn. Und auf dem Tablet in der Gaststube läuft Laras letzte Instagram-Story von 20 Uhr 58: ein altes Foto von einem roten Mini, darunter steht „Gleich gibt’s ein Geständnis 😅 #damals“.',
      evidence: [
        { ic: '🍳', name: 'Verkohlter Kaiserschmarrn', text: 'Er brannte gegen 21:20 Uhr an. Wer hat da nicht aufgepasst?' },
        { ic: '🚗', name: 'Laras letzte Story', text: '20:58 Uhr: Foto eines roten Mini. „Gleich gibt’s ein Geständnis 😅 #damals“' },
        { ic: '💬', name: 'Nachricht „Es ist aus“', text: 'Auf Jonas’ Handy, von Lara um 20:30 Uhr gesendet.' }
      ],
      ask: [ { c: 0, q: 'Waren Sie wirklich den ganzen Abend in der Küche?' },
             { c: 2, q: 'Wie kommt Ihr Handschuh auf die Terrasse – und wo waren Sie um viertel nach neun?' } ] },
    { title: 'Das Regenfass',
      text: 'Hinter der Küche, im Regenfass unter einer Schicht aus Eis und Schnee, liegt Laras Handy. An dieses Fass kommt man nur durch die Küchentür. Und an der Wand hinter der Theke hängt ein Foto: ein junger Mann in Bergsteigerkleidung, darunter ein Trauerflor und ein Datum – gestorben am 12. März vor sechs Jahren.',
      evidence: [
        { ic: '🛢️', name: 'Handy im Regenfass', text: 'Nur über die Küchentür erreichbar. Das Display ist zersprungen.' },
        { ic: '🖼️', name: 'Foto mit Trauerflor', text: '„Florian Gruber – † 12. März“. Ein Zeitungsartikel daneben: Fahrerflucht, roter Kleinwagen.' },
        { ic: '📡', name: 'Router- und Funkprotokolle', text: 'Sie zeigen, wer wann online war und wer mit der Bergwacht gefunkt hat.' }
      ],
      ask: [ { c: 0, q: 'Wer ist der junge Mann auf dem Foto hinter der Theke?' },
             { c: 1, q: 'Was genau haben Sie zwischen 21 und 21:35 Uhr an Ihrem Laptop gemacht?' } ] }
  ],
  solution: [
    'Der Täter ist Sepp Gruber, der Hüttenwirt.',
    'Vor sechs Jahren starb sein Sohn Florian auf der Passstraße. Der Fahrer eines roten Kleinwagens beging Fahrerflucht. Beim Abendessen erzählte Lara lachend genau diese Geschichte – ohne zu ahnen, wem die Hütte gehört.',
    'Um 21 Uhr 15 stellte Sepp sie auf der Terrasse zur Rede und stieß sie über das Geländer. Ihr Handy mit der angekündigten Geständnis-Story warf er ins Regenfass hinter der Küche. Währenddessen verbrannte in der leeren Küche der Kaiserschmarrn.'
  ],
  keys: ['Kaiserschmarrn verbrannte um 21:20 – Küche leer', 'Story „roter Mini“ + Foto von Florian Gruber = Motiv', 'Handy im Fass – nur über die Küche erreichbar', 'Tobias: Router-Log · Mia: Sauna · Jonas: Funkprotokoll']
},
{
  id: 'marzipan', icon: '🍬', title: 'Marzipan und Bittermandel', tag: 'Lübeck, Advent 1925',
  blurb: 'Bei der Adventsgesellschaft bricht der Marzipan-Senator zusammen. Es riecht nach Bittermandel – aber war es wirklich das Marzipan?',
  victim: 'Senator Albert Holm, 70, Marzipan-Fabrikant',
  intro: [
    'Lübeck, am zweiten Advent 1925. In der Villa Holm an der Wakenitz brennen die Kerzen, im Salon spielt das Grammophon, draußen fällt leiser Schnee.',
    'Senator Albert Holm, der Herr über die größte Marzipanfabrik der Stadt, hat zur Adventsgesellschaft geladen. Um 22 Uhr 10 greift er sich im Herrenzimmer an die Kehle und bricht zusammen. Wenige Minuten später ist er tot.',
    'Über dem Toten liegt ein feiner Geruch nach bitteren Mandeln. Blausäure. In einem Haus voller Marzipan.',
    'Kommissar Dahl von der Lübecker Kriminalpolizei lässt niemanden gehen. Stellt euch vor!'
  ],
  chars: [
    { name: 'Charlotte Holm', g: 'f', emoji: '📷', role: 'Die junge Ehefrau', guilty: true,
      intro: 'Charlotte Holm. Albert und ich sind seit zwei Jahren verheiratet. Ich bin aus Berlin hierhergezogen – für ihn. Ich fotografiere leidenschaftlich gern.',
      secret: 'DU BIST DIE TÄTERIN. Bevor du Albert geheiratet hast, warst du in Berlin als Varieté-Tänzerin „Lotte Lux“ mit einem Zauberkünstler verheiratet – und nie geschieden. Heute früh kam ein Brief einer Berliner Detektei. Um 21:30 Uhr hat Albert dich zur Rede gestellt: Er lässt die Ehe annullieren und ändert morgen um 10 Uhr beim Notar sein Testament. Du hättest alles verloren. Um 21:55 Uhr hast du Cyankali aus deiner Dunkelkammer in seinen Cognac gegeben und ihm das Glas selbst gereicht.',
      story: 'Deine Tarngeschichte: Du warst den ganzen Abend Gastgeberin im Salon. Das Gift muss im Marzipan gewesen sein – das hat ja Paul mitgebracht! Lenke den Verdacht auf ihn und auf den Doktor.',
      clues: [
        'Alle denken an das Marzipan – gut so! Erinnere daran, dass Paul die Schachtel mitgebracht hat und dass Albert zwei Stücke davon gegessen hat.',
        'Der Brief der Detektei liegt halb verbrannt im Kamin – das war zu hastig. Behaupte, es sei ein Werbebrief gewesen. Und das Cyankali? „Das braucht man in der Fotografie, jeder Fotograf hat das.“ Erwähne, dass auch ein Arzt leicht an Gift kommt.',
        'Gefahr: Der Befund wird zeigen, dass das Gift im Cognac war. Sag, du hast das Glas nur „weitergereicht“, eingeschenkt habe Martha. Und erinnere daran: Paul erbt die Fabrik!'
      ] },
    { name: 'Paul Holm', g: 'm', emoji: '🍬', role: 'Der Neffe, Konditormeister',
      intro: 'Paul Holm, Konditormeister und Alberts Neffe. Ich habe meinem Onkel heute eine Schachtel meines besten Marzipans geschenkt. Und jetzt glauben alle, ich hätte ihn vergiftet.',
      secret: 'Deine Konditorei steckt tief in den Schulden. Gestern hat Onkel Albert dir unter vier Augen gesagt, dass er morgen sein Testament ändert – zu deinen Gunsten: Du solltest die Fabrik erben. Du hattest also allen Grund, dass er MORGEN noch lebt.',
      story: 'Du bist unschuldig. Deine Schulden sind dir peinlich, und das neue Testament solltest du eigentlich für dich behalten.',
      clues: [
        'Das Marzipan hast du heute früh selbst gemacht, aus dem gleichen Teig wie die Stücke in deinem Schaufenster. Du hast selbst drei Stücke davon gegessen – dir geht es gut.',
        'Gegen 21:45 Uhr hast du Charlotte aus dem Keller kommen sehen, wo ihre Dunkelkammer ist. Sie trug Handschuhe – mitten auf der Gesellschaft.',
        'Wenn das Testament erst morgen geändert wird, hättest du heute alles verloren, wenn Albert stirbt. Wer verliert durch das neue Testament? Das ist die entscheidende Frage.'
      ] },
    { name: 'Dr. Friedrich Kahl', g: 'm', emoji: '🩺', role: 'Der Hausarzt',
      intro: 'Dr. Friedrich Kahl, seit zwanzig Jahren Hausarzt der Familie Holm. Ich habe sofort den Bittermandelgeruch bemerkt. Für den Senator kam jede Hilfe zu spät.',
      secret: 'Du stellst dir selbst Morphium-Rezepte aus, weil du nicht mehr ohne kannst. Albert hat das vor einer Woche bemerkt und gedroht, es der Ärztekammer zu melden. Ein Motiv – aber du bist unschuldig.',
      story: 'Du bist unschuldig. Deine Sucht darf niemand erfahren.',
      clues: [
        'Die Symptome passen eindeutig zu Blausäure. Aber Marzipan riecht immer nach Mandeln – der Geruch allein beweist nicht, dass das Marzipan vergiftet war.',
        'Albert hat dich um 21:40 Uhr gebeten, morgen um 10 Uhr beim Notar als Zeuge zu unterschreiben. „Ich habe eine bittere Entdeckung über Berlin gemacht“, sagte er.',
        'Cyankali wurde damals in der Fotografie zum Abschwächen von Negativen verwendet. In deiner Arzttasche ist so etwas nicht – das kann jeder nachprüfen.'
      ] },
    { name: 'Martha Kröger', g: 'f', emoji: '🧺', role: 'Die Haushälterin',
      intro: 'Martha Kröger. Ich führe den Haushalt der Holms seit dreißig Jahren. Ich habe den Senator schon gekannt, als er noch Lehrling in der Fabrik war.',
      secret: 'Du hast um 21:30 Uhr an der Tür des Herrenzimmers gelauscht. Du hast Albert sagen hören: „Berlin … nie geschieden … morgen der Notar.“ Danach war es still. Um 22:15 Uhr befahl dir die Gnädige Frau, alle Gläser sofort zu spülen – Alberts Glas hast du aber heimlich in der Speisekammer versteckt, weil es so seltsam roch.',
      story: 'Du bist unschuldig. Du hast Angst, dass man dir das Lauschen übel nimmt, und rückst erst nach und nach mit der Wahrheit heraus.',
      clues: [
        'Den Cognac hat der Senator heute nicht selbst eingeschenkt und du auch nicht. Das Tablett mit der Karaffe stand im Herrenzimmer.',
        'Gegen 22:15 Uhr befahl dir Frau Charlotte, sofort alle Gläser zu spülen. Alberts Glas hast du aber versteckt – es roch nach bitteren Mandeln. Jetzt kannst du es dem Kommissar übergeben.',
        'Du hast mit eigenen Augen gesehen, wie Charlotte dem Senator um 21:55 Uhr ein Glas Cognac reichte. Sie hatte es selbst eingeschenkt, mit dem Rücken zum Raum.'
      ] },
    { name: 'Hinrich Lange', g: 'm', emoji: '🚗', role: 'Der Chauffeur',
      intro: 'Hinrich Lange, Chauffeur der Familie. Ich fahre den Senator seit acht Jahren und weiß, wohin er fährt – und manchmal auch, warum.',
      secret: 'Du verkaufst heimlich Benzin aus der Garage der Holms weiter. Mit dem Mord hast du nichts zu tun.',
      story: 'Du bist unschuldig.',
      clues: [
        'Heute früh hast du die Post geholt. Dabei war ein dicker Brief an den Senator: „Detektei Kranz, Berlin“. Er hat ihn sofort in seine Westentasche gesteckt.',
        'Von der Garage aus hast du gegen 21:45 Uhr Licht im Kellerfenster der Dunkelkammer gesehen. Dort hat nur Frau Charlotte einen Schlüssel.',
        'Letzten Monat hast du Frau Charlotte zur Apotheke in der Breiten Straße gefahren. Sie kam mit einem kleinen braunen Fläschchen zurück: „Für meine Negative.“'
      ] },
    { name: 'Ida von Berg', g: 'f', emoji: '🎭', role: 'Charlottes Freundin aus Berlin',
      intro: 'Ida von Berg, aus Berlin. Charlotte und ich kennen uns aus wilden Zeiten. Ich bin für ein paar Tage zu Besuch an der Ostsee.',
      secret: 'Du schuldest Charlotte viel Geld und bist deshalb eigentlich auf ihrer Seite. Aber du bist unschuldig, und lügen für einen Mord wirst du nicht.',
      story: 'Du bist unschuldig.',
      clues: [
        'In Berlin stand Charlotte unter dem Namen „Lotte Lux“ im Varieté auf der Bühne. Das weiß hier in Lübeck niemand.',
        'Charlotte war in Berlin mit einem Zauberkünstler verheiratet, „dem großen Zamboni“. Von einer Scheidung hast du nie gehört.',
        'Heute Nachmittag hat Charlotte dich gefragt: „Was passiert eigentlich, wenn eine Ehe für ungültig erklärt wird? Bekommt die Frau dann gar nichts?“'
      ] }
  ],
  acts: [
    { title: 'Ein Toast zum Advent',
      text: 'Kommissar Dahl hat das Herrenzimmer versiegelt. Auf dem Tisch steht Pauls Geschenk: eine Schachtel feinstes Lübecker Marzipan, zwei Stücke fehlen. Daneben die Cognac-Karaffe. Die Gläser hat jemand schon abgeräumt. Und überall hängt dieser Duft nach Mandeln.',
      evidence: [
        { ic: '🎁', name: 'Marzipanschachtel', text: 'Ein Geschenk von Paul. Zwei Stücke fehlen – der Senator hat sie gegessen.' },
        { ic: '🥃', name: 'Cognac-Karaffe', text: 'Halb voll. Die Gläser sind schon abgeräumt und gespült.' },
        { ic: '🌰', name: 'Bittermandelgeruch', text: 'Typisch für Blausäure. Aber auch Marzipan riecht nach Mandeln.' }
      ],
      ask: [ { c: 1, q: 'Was genau war in der Marzipanschachtel, die Sie heute mitgebracht haben?' },
             { c: 0, q: 'Wann haben Sie Ihren Mann heute Abend zuletzt allein gesprochen?' } ] },
    { title: 'Die Dunkelkammer',
      text: 'Im Kamin des Herrenzimmers findet der Kommissar einen halb verbrannten Brief. Lesbar ist nur noch: „…Ehe mit Herrn Zamb… wurde nie geschieden… Detektei Kranz, Berlin.“ Und im Keller, in der Dunkelkammer, steht ein braunes Fläschchen mit der Aufschrift „Kaliumcyanid – nur für photographische Zwecke“. Es ist halb leer.',
      evidence: [
        { ic: '📜', name: 'Halb verbrannter Brief', text: '„…Ehe mit Herrn Zamb… wurde nie geschieden…“ – Absender: Detektei Kranz, Berlin.' },
        { ic: '🧪', name: 'Fläschchen Kaliumcyanid', text: 'Aus der Dunkelkammer im Keller. Halb leer. „Nur für photographische Zwecke.“' },
        { ic: '🔑', name: 'Schlüssel zur Dunkelkammer', text: 'Es gibt nur einen einzigen Schlüssel – und den trägt die Hausherrin bei sich.' }
      ],
      ask: [ { c: 3, q: 'Haben Sie heute Abend etwas gehört oder gesehen, das Sie uns bisher verschwiegen haben?' },
             { c: 2, q: 'Herr Doktor, hätten Sie leicht an Gift kommen können?' } ] },
    { title: 'Der Befund',
      text: 'Der Gerichtschemiker hat über Nacht gearbeitet. Das Marzipan ist einwandfrei, kein Gift. Aber in einem Cognacglas aus der Speisekammer findet er eine tödliche Dosis Cyankali. Und der Notar bestätigt: Morgen um 10 Uhr wollte der Senator sein Testament ändern. Seine Frau sollte nichts mehr erben.',
      evidence: [
        { ic: '🔬', name: 'Befund des Chemikers', text: 'Marzipan: sauber. Cognacglas aus der Speisekammer: tödliche Dosis Cyankali.' },
        { ic: '📑', name: 'Entwurf des neuen Testaments', text: 'Termin morgen 10 Uhr. Die Fabrik geht an Paul – Charlotte erhält nichts.' },
        { ic: '🍷', name: 'Das versteckte Glas', text: 'Jemand hat das Glas des Senators vor dem Spülen gerettet.' }
      ],
      ask: [ { c: 0, q: 'Wer hat Ihrem Mann heute Abend den Cognac eingeschenkt?' },
             { c: 1, q: 'Wussten Sie, dass Ihr Onkel morgen sein Testament ändern wollte?' } ] }
  ],
  solution: [
    'Die Täterin ist Charlotte Holm, die junge Ehefrau.',
    'In Berlin war sie als „Lotte Lux“ bereits mit einem Zauberkünstler verheiratet – und nie geschieden. Eine Detektei hatte das herausgefunden. Albert wollte die Ehe annullieren lassen und morgen beim Notar sein Testament ändern. Charlotte hätte alles verloren.',
    'Um 21 Uhr 55 gab sie Cyankali aus ihrer Dunkelkammer in seinen Cognac und reichte ihm das Glas. Das Marzipan war nur ein Ablenkungsmanöver: Es riecht ohnehin nach Mandeln. Doch Martha rettete das Glas vor dem Spülen. Und Paul hatte keinen Grund, seinen Onkel noch vor dem neuen Testament zu töten.'
  ],
  keys: ['Gift im Cognac, nicht im Marzipan', 'Cyankali aus Charlottes Dunkelkammer', 'Brief: Charlotte war noch verheiratet = Motiv', 'Neues Testament morgen: Charlotte verliert alles, Paul gewinnt']
}
];
