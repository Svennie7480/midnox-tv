// midnox Krimi-Abend – Detektiv-Modus (1–2 Spieler).
// talk[figur][akt] = Aussage beim Verhör in diesem Akt (der Täter lügt).
// look[beweis]     = Detail, das man mit der Lupe findet.
// motives[0] ist das richtige Motiv (wird beim Anzeigen gemischt). keys = Beweise, die als „entscheidend“ zählen.
window.DETECTIVE = {
  seeblick: {
    talk: [
      [ 'Ich hatte Kopfschmerzen und war auf meinem Zimmer. Konrad habe ich zuletzt um zwanzig nach zehn gesehen. Er schimpfte über „Zahlen, die nicht stimmen“.',
        'Na gut … ich war nicht auf meinem Zimmer. Ich war unten in der Telefonzelle. Von dort habe ich gegen 22:50 Uhr jemanden in einem grauen Kostüm die Treppe zum Büro hinaufgehen sehen.',
        'Ja, das Gespräch nach Hamburg war ich. Mit meinem Anwalt – ich wollte mich scheiden lassen. Aber umgebracht habe ich ihn nicht. Ich habe bis 23:02 Uhr telefoniert.' ],
      [ 'Wir haben gestritten, ja. Um Geld. Danach war ich auf meinem Zimmer, mehr gibt es nicht zu sagen.',
        'Ich war draußen, am Seesteg. Um 22:35 Uhr habe ich Vater noch oben am Bürofenster stehen sehen. Er hat also nach halb elf noch gelebt!',
        'Ich habe Spielschulden. Meine Uhr habe ich am Steg einem Mann vom Kasino als Pfand gegeben – er kann bezeugen, dass ich bis 23:10 Uhr dort war. Und auf dem Rückweg sah ich Fräulein Martens aus dem Büroflur kommen. Sie wischte sich die Hände an einem Taschentuch ab.' ],
      [ 'Ich habe den ganzen Abend mit dem Kapitän Rommé gespielt. Die Uhr zeigt doch halb elf – da saß ich an der Bar. Schrecklich, das alles.',
        'Ich war höchstens kurz zum Frischmachen weg. Fragen Sie lieber Henning, wofür er so dringend Geld brauchte. Und Frau Wiese wirkt mir erstaunlich gefasst für eine Witwe.',
        'Das Kassenbuch? Herr Wiese hat sicher selbst alte Unterlagen verbrannt, er wollte aufräumen. Und der Fleck an meinem Ärmel ist Tinte – von der Abrechnung.' ],
      [ 'Ab zehn Uhr habe ich mit Fräulein Martens an der Bar Rommé gespielt. Konrad war mein ältester Freund.',
        'Hm. Wenn ich ehrlich bin: Sie stand gegen 22:40 Uhr auf, „die Abrechnung“, und kam erst gegen elf zurück. Ziemlich blass. Aber ich hatte schon drei Rum …',
        'Konrad sagte mir gestern, die Bilanzen sähen merkwürdig aus. Er wollte „jemandem im Haus auf den Zahn fühlen“. Und ja, ich wollte das Hotel kaufen und er hat abgelehnt. Deswegen tötet man doch keinen Freund.' ],
      [ 'Ich habe ab neun Uhr durchgehend am Flügel gespielt. Der Kapitän saß den ganzen Abend an der Bar.',
        'Gegen 22:40 Uhr ging Fräulein Martens an der Bar vorbei – Richtung Treppe. Die Toiletten liegen in der anderen Richtung.',
        'Sie kam gegen elf zurück, mit einem dunklen Fleck am Ärmel, und bestellte sofort einen doppelten Korn. Sonst trinkt sie nie.' ],
      [ 'Ein einziger Schlag auf den Hinterkopf. Um 23:20 Uhr war der Körper noch warm – der Tod trat höchstens eine halbe Stunde vorher ein.',
        'Der Tee auf dem Schreibtisch war noch lauwarm. Er kann nicht vor halb elf gebracht worden sein.',
        'Am Messing-Leuchtturm klebte ein grauer Wollfaden. Wie von einem Kostüm.' ]
    ],
    look: {
      'Messing-Leuchtturm': 'Im Blut am Sockel klebt ein grauer Wollfaden – wie von einem Kostüm.',
      'Zerschlagene Wanduhr': 'Glassplitter liegen UNTER den Zeigern eingeklemmt: Die Zeiger wurden erst nach dem Zerschlagen gedreht.',
      'Asche im Ofen': 'Zwischen der Asche: ein Fetzen liniertes Papier mit den Spalten „Soll“ und „Haben“.',
      'Teekanne': 'Zwei Tassen auf dem Tablett, nur eine benutzt. Herr Wiese erwartete Besuch.',
      'Sandspuren im Flur': 'Die Abdrücke führen von der Gartentür in den Flur und wieder hinaus – nicht die Treppe hinauf.',
      'Brief vom Kasino Travemünde': 'Kleingedruckt: „Pfand wird angenommen.“ Henning wollte etwas verpfänden – nicht stehlen.',
      'Verkohltes Kassenbuch': 'Die Handschrift auf den Resten ist die von Ilse Martens. Eine Spalte zeigt Abhebungen ohne Beleg – über Jahre.',
      'Protokoll des Fernamts': 'Angemeldet hat das Gespräch: „Frau Wiese, Zimmer 1“.',
      'Fehlende Armbanduhr': 'Herr Kalle vom Kasino bestätigt telefonisch: Uhr am Seesteg als Pfand angenommen, kurz vor elf.'
    },
    motives: [ 'Unterschlagung aus der Hotelkasse – sie sollte angezeigt werden', 'Spielschulden – er brauchte dringend Geld', 'Scheidung – sie wollte ihr Geld retten', 'Hotelkauf abgelehnt – er wollte das Haus' ],
    keys: [ 'Teekanne', 'Verkohltes Kassenbuch', 'Zerschlagene Wanduhr' ]
  },
  huette: {
    talk: [
      [ 'Ich stand den ganzen Abend in der Küche, wie jeden Abend. Furchtbar, so ein junges Ding. Der Bergführer trägt übrigens auch Größe 45.',
        'Der Kaiserschmarrn? Ich war kurz im Keller, Bier holen. Fragen Sie lieber den Manager – den hat sie heute vor allen gefeuert.',
        'Das Foto? Mein … Neffe. Lange her. Und an das Regenfass kommt jeder, die Küchentür ist nie abgeschlossen.' ],
      [ 'Wir hatten unterschiedliche Vorstellungen. Sie hat mich gefeuert, ja. Danach saß ich in der Gaststube am Laptop.',
        'Gegen Viertel nach neun hörte ich die Küchentür zur Terrasse knarren. Gesehen habe ich nichts.',
        'Okay. Ich habe Sponsorengeld abgezweigt, deshalb hat sie mich gefeuert. Aber das Router-Protokoll zeigt: Ich war von 21 bis 21:35 Uhr durchgehend online, in der Gaststube.' ],
      [ 'Um zehn vor neun sagte Lara noch zu mir: „Heute erzähl ich endlich die Mini-Geschichte in meiner Story.“ Ich weiß nicht, was sie meinte.',
        'Der Handschuh ist meiner, ich hatte ihn Lara geliehen. Beim Abendessen erzählte sie lachend, wie sie damals mit ihrem roten Mini auf dem Pass etwas angefahren hat und weitergefahren ist. Da ließ Sepp ein Glas fallen.',
        'Ich erbe alles, ja. Aber ich war von 21 bis 21:40 Uhr in der Sauna – die Zeitschaltuhr beweist es.' ],
      [ 'Um 21:05 Uhr habe ich sie zuletzt gesehen. Sie ging mit dem Handy Richtung Terrasse: „Ich mach noch eine Story.“',
        'Wir … hatten eine Affäre. Um halb neun hat sie Schluss gemacht. Ich war danach im Skiraum und sah gegen 21:15 Uhr durchs Fenster jemanden mit Küchenschürze über die Terrasse gehen.',
        'Von 21:18 bis 21:24 Uhr habe ich mit der Bergwacht gefunkt. Das steht im Funkprotokoll.' ],
      [ 'Beim Abendessen hat Sepp sie sehr lange und sehr still angestarrt. Danach war Lara wie ausgewechselt.',
        'Um 21:20 Uhr wollte ich Wasser holen. Die Küche war leer, und in der Pfanne verbrannte der Kaiserschmarrn.',
        'Meine Kamera zeigt um 21:24 Uhr, wie Sepp durch die Küchentür hereinkommt. Schnee auf den Schultern – keine Bierkiste in der Hand.' ],
      [ 'Druckstellen an beiden Oberarmen. Jemand mit großen, kräftigen Händen hat sie gepackt.',
        'Der Sturz war zwischen 21:10 und 21:25 Uhr. Später nicht, dafür war sie zu ausgekühlt.',
        'Unter ihren Fingernägeln ist Mehl. Sie hat sich an jemandem festgekrallt, der in der Küche gearbeitet hat.' ]
    ],
    look: {
      'Schuhabdrücke am Geländer': 'Im Profil eines Abdrucks steckt ein Krümel Puderzucker.',
      'Ein Damenhandschuh': 'Innen mit Filzstift: „M.S.“ – Mias Initialen. Lara trug ihn beim Shooting am Nachmittag.',
      'Das verschwundene Handy': 'Laras Smartwatch zeigt: Das Handy verlor um 21:16 Uhr die Verbindung.',
      'Verkohlter Kaiserschmarrn': 'Die Küchenuhr am Herd wurde um 21:12 Uhr gestellt und ist abgelaufen – niemand hat sie ausgeschaltet.',
      'Laras letzte Story': 'Auf dem Foto des roten Mini ist ein Datum eingeblendet: 12. März, vor sechs Jahren.',
      'Nachricht „Es ist aus“': 'Jonas’ Antwort um 20:31 Uhr: „Okay. Ich respektiere das.“ Kein Streit.',
      'Handy im Regenfass': 'Auf der Hülle: ein mehliger Daumenabdruck.',
      'Foto mit Trauerflor': 'Auf der Rückseite steht: „Für Papa – dein Flo“.',
      'Router- und Funkprotokolle': 'Router: Tobias’ Laptop 21:00–21:35 Uhr aktiv. Funk: Jonas 21:18–21:24 Uhr. Saunauhr: 21:00–21:40 Uhr.'
    },
    motives: [ 'Rache für den Tod seines Sohnes', 'Unterschlagenes Sponsorengeld flog auf', 'Eifersucht nach der Trennung', 'Das Erbe der Schwester' ],
    keys: [ 'Verkohlter Kaiserschmarrn', 'Handy im Regenfass', 'Foto mit Trauerflor', 'Laras letzte Story' ]
  },
  marzipan: {
    talk: [
      [ 'Ich war den ganzen Abend Gastgeberin im Salon. Albert hat zwei Stücke von Pauls Marzipan gegessen … und dann …',
        'Der Brief? Werbung, nichts weiter. Und Cyankali hat jeder Fotograf. Ein Arzt kommt doch viel leichter an Gift, nicht wahr?',
        'Ich habe ihm das Glas nur weitergereicht – eingeschenkt hat Martha. Und vergessen Sie nicht: Paul erbt die Fabrik!' ],
      [ 'Das Marzipan habe ich heute früh selbst gemacht. Ich habe drei Stücke davon gegessen – mir geht es gut.',
        'Gegen Viertel vor zehn kam Charlotte aus dem Keller, wo ihre Dunkelkammer ist. Mit Handschuhen – mitten auf der Gesellschaft.',
        'Ja, ich wusste vom neuen Testament. Ich sollte die Fabrik bekommen – morgen. Warum sollte ich ihn ausgerechnet heute töten?' ],
      [ 'Blausäure, eindeutig. Aber Marzipan riecht immer nach Mandeln – der Geruch allein beweist gar nichts.',
        'Albert bat mich um 21:40 Uhr, morgen beim Notar als Zeuge zu unterschreiben. „Ich habe eine bittere Entdeckung über Berlin gemacht“, sagte er.',
        'Cyankali benutzt man in der Fotografie. In meiner Tasche ist so etwas nicht – sehen Sie gerne nach.' ],
      [ 'Den Cognac hat heute weder der Senator noch ich eingeschenkt. Das Tablett stand im Herrenzimmer.',
        'Ich … habe um halb zehn an der Tür gelauscht. „Berlin … nie geschieden … morgen der Notar.“ Und um Viertel nach zehn befahl mir die Gnädige Frau, sofort alle Gläser zu spülen.',
        'Ich habe sein Glas versteckt, es roch so seltsam. Und ich habe gesehen, wie Frau Charlotte ihm um 21:55 Uhr ein Glas reichte – selbst eingeschenkt, mit dem Rücken zum Raum.' ],
      [ 'Heute früh kam ein dicker Brief für den Senator: „Detektei Kranz, Berlin“. Er hat ihn sofort eingesteckt.',
        'Gegen Viertel vor zehn brannte Licht in der Dunkelkammer. Einen Schlüssel dazu hat nur Frau Charlotte.',
        'Letzten Monat habe ich Frau Charlotte zur Apotheke gefahren. Sie kam mit einem braunen Fläschchen zurück – „für meine Negative“.' ],
      [ 'In Berlin stand Charlotte als „Lotte Lux“ auf der Varieté-Bühne. Das weiß hier niemand.',
        'Sie war mit einem Zauberkünstler verheiratet, dem „großen Zamboni“. Von einer Scheidung habe ich nie gehört.',
        'Heute Nachmittag fragte sie mich, was passiert, wenn eine Ehe für ungültig erklärt wird. Ob die Frau dann gar nichts bekommt.' ]
    ],
    look: {
      'Marzipanschachtel': 'Die übrigen Stücke riechen nur süß. Paul hat selbst davon gegessen.',
      'Cognac-Karaffe': 'In der Karaffe selbst: kein Bittermandelgeruch. Das Gift kam erst ins Glas.',
      'Bittermandelgeruch': 'Am stärksten riecht es am Mund des Toten – nicht an der Marzipanschachtel.',
      'Halb verbrannter Brief': 'Auf einem Rest: „…Fräulein Lotte Lux, verehelichte Zamb…“.',
      'Fläschchen Kaliumcyanid': 'Auf dem Glas: der Abdruck eines weißen Satinhandschuhs.',
      'Schlüssel zur Dunkelkammer': 'Der Anhänger trägt ein Monogramm: „C. H.“',
      'Befund des Chemikers': 'Zusatz: Das Gift war nur im Glas, nicht in der Karaffe – es wurde gezielt hineingegeben.',
      'Entwurf des neuen Testaments': 'Randnotiz in Alberts Handschrift: „Ehe ungültig – Berlin!“',
      'Das versteckte Glas': 'Martha hat es in ein Tuch gewickelt. Am Fuß: der Abdruck eines weißen Satinhandschuhs.'
    },
    motives: [ 'Ihre Doppelehe flog auf – sie hätte alles verloren', 'Schulden der Konditorei', 'Angst vor der Ärztekammer', 'Groll nach dreißig Jahren im Dienst' ],
    keys: [ 'Das versteckte Glas', 'Fläschchen Kaliumcyanid', 'Halb verbrannter Brief', 'Befund des Chemikers' ]
  }
};
