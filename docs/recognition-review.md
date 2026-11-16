# Prüfung der Bucherkennung – Stand 0.10.6

> Historischer Review für 0.10.6. Der aktuelle Umsetzungsstand und die noch offenen
> Aufgaben stehen in [implementation-status.md](implementation-status.md).

Die Befunde stammen aus dem Quellcode. Die Qualität mit echten Büchern wurde für diese Änderung noch nicht vermessen. Ein erfolgreicher Android-Build belegt keine Erkennungsqualität.

## Korrigierte Ursachen

- `BookScannerView.kt`: Ein längeres, früheres OCR-Ergebnis konnte spätere Korrekturen verdrängen. Jetzt wird die aktuelle Lesung übernommen.
- Der bisherige Wortvergleich teilte durch die kleinere Wortmenge: Ein einzelnes gemeinsames Wort konnte dadurch 100 % Stabilität ergeben. Jetzt zählt die Vereinigungsmenge beider Wortmengen. Mindestens fünf ähnliche Lesungen über 1,2 Sekunden gehen der zusätzlichen Bildruheprüfung voraus. Diese Schwellen sind heuristisch und müssen mit Aufnahmen kalibriert werden.
- Leere OCR-Lesungen setzen den Stabilitätszustand zurück. Die räumliche Gruppierung verwendet die zur Bildrotation passende Breite.
- `BookCatalog.kt`: Suchtext einschließlich Autor ging bislang ausschließlich in das Open-Library-Titelfeld. Jetzt wird die allgemeine Suche verwendet. Die ersten zwei OCR-Suchvarianten ziehen Google Books hinzu; bei fehlenden oder schwachen Treffern folgt DNB. Weitere Varianten werden zur Begrenzung der Anfragen reduziert verarbeitet.
- Regal-Kandidaten durchlaufen jetzt dieselbe Textbewertung wie Einzelbücher. Treffer verschiedener Suchvarianten werden zusammengeführt, statt ergänzende Angaben beim Entfernen von Duplikaten zu verlieren.
- DNB-Autorennamen wurden am ersten Komma abgeschnitten. Vornamen bleiben nun erhalten.
- Ein Google-Ergebnis ohne bestätigende ISBN wird nicht mehr automatisch der gescannten ISBN zugeordnet.
- `CoverImageMatcher.kt`: Das bereits normierte Farbhistogramm wurde ein zweites Mal durch drei geteilt. Damit erreichten selbst identische Merkmale höchstens etwa 83 Punkte. Dieser Rechenfehler ist behoben. Alte und neue Coverwerte sind daher nicht direkt vergleichbar.
- In der normalen Suche kann ein hoher Coverwert einen Texttreffer nur noch leicht aufwerten (maximal fünf Rangpunkte). Ein fehlendes oder abweichendes Cover senkt dessen Textwert nicht. Im Cover-Test bleibt die rein visuelle Sortierung erhalten.
- Temporäre Bitmaps der Merkmalsextraktion werden freigegeben. Die Anzeige nennt einen experimentellen Ähnlichkeitswert statt einer vermeintlichen Erkennungswahrscheinlichkeit.

## Verbleibende Grenzen und Prioritäten

### P1: Ausgabeidentität absichern

Die Open-Library-Suche liefert derzeit Werkdaten mit mehreren ISBNs. `findByTitle` wählt daraus die erste gültige ISBN und kombiniert sie mit dem Werkcover und dem Jahr der Erstveröffentlichung. Diese Daten müssen nicht dieselbe Ausgabe beschreiben. Auch ein exakter ISBN-Suchtreffer kann Werkmetadaten enthalten. Vor einer sicheren Momox-Zuordnung braucht es einen zusätzlichen Schritt zur Auflösung der konkreten Ausgabe. ISBN-10 und zugehörige ISBN-13 sollten eine gemeinsame Identität erhalten. Verschiedene Ausgaben müssen getrennt bleiben.

### P1: Buchrücken tatsächlich trennen

`groupShelfText` gruppiert erkannte Textkästen nach Abstand und Überlappung. Es erkennt keine Buchgrenzen. Das vollständige Paarvergleichsverfahren verhindert zwar Kettenverschmelzungen, kann aber Namen auf einem Rücken trennen oder Text benachbarter Rücken verbinden. Gruppierungen werden außerdem pro Bild neu gebildet; wiederholte identische Texte werden dedupliziert. Damit sind weder räumliche Identität noch mehrere gleiche Bücher zuverlässig abgebildet.

Nächster Ausbau: sichtbare Rückenregionen markieren, jede Region über mehrere Bilder verfolgen, pro Region OCR sammeln und ihre Gruppierung vor der Suche korrigierbar machen. Gedrehte Regionen benötigen eigene OCR-Durchläufe in passenden Orientierungen. Das vorhandene Auswerten eines OCR-Zeilenwinkels dreht das Bild nicht für die Texterkennung.

### P1: Unsicherheit vor dem automatischen Auslösen berücksichtigen

Text- und Bildruhe garantieren keine korrekte Lesung: Auch ein unscharfes Bild kann stabil bleiben. Bildschärfe, verfügbare Textkonfidenz und Übereinstimmung mehrerer Lesungen sollten gemeinsam bewertet werden. Die automatische Regalwahl beruht bislang auf Textanzahl, Streuung und Winkeln; sie ist kein trainierter Szenenklassifikator. Mehrspaltige Buchseiten können wie Regale aussehen.

Nächster Ausbau: eine sichtbar markierte Erkennungsregion, zeitlich stabile Szenenentscheidung und eine begründete Anzeige wie „zu unscharf“ oder „mehrere Bücher vermutet“. Manuelle Übernahme und Moduskorrektur erhalten.

### P2: Coververgleich geometrisch verbessern

Der Vergleich verwendet einen festen Mittelausschnitt, einen Differenzhash und grobe Farbverteilungen. Er erkennt weder das Coverrechteck noch Perspektive oder Gestaltung semantisch. Hintergrund, Schräglage und Aufkleber können den Wert stark verändern. Mehr Katalogquellen lösen dieses Problem nicht. Ohne lesbaren Text fehlen dem aktuellen Cover-Test weiterhin die Suchkandidaten.

Nächster Ausbau: Coverfläche erkennen oder manuell zuschneiden, perspektivisch entzerren und erst dann vergleichen. Einen stärkeren Bildvergleich nur anhand eines beschrifteten Beispielsatzes auswählen. Prozentwerte niemals als kalibrierte Wahrscheinlichkeit darstellen.

### P2: Suchvarianten und Laufzeit begrenzen

OCR liefert bislang gruppierte und rohe Zeilen gemeinsam. Dadurch entstehen wiederholte Wörter, unpassende Titel-Autor-Kombinationen und viele sequenzielle Abfragen. Kurze oder generische Titel können beim Wortüberlappungswert zu gut abschneiden. Netzwerkfehler werden oft als leere Trefferliste behandelt; Abbruchausnahmen werden teilweise von `runCatching` abgefangen.

Nächster Ausbau: maximal wenige begründete Varianten aus Titel-/Autorenhypothesen, Rückfragen bei unklarer Ausgabe, Cache für wiederholte Abfragen, globale Laufzeitgrenze, klarer Fehlerstatus je Quelle und durchgängige Abbruchweitergabe. Quellenherkunft liegt momentan nur am Kandidaten und nicht dauerhaft pro Metadatenfeld vor.

## Erforderliche Qualitätsmessung

Ein freiwillig bereitgestellter Beispielsatz sollte Einzelcover, schmale Rücken, gedrehte Schrift, ältere Ausgaben, ISBN-Text, Buchseiten, Spiegelungen und Regale enthalten. Bilder bleiben außerhalb des öffentlichen Repositorys, sofern ihre Veröffentlichung nicht ausdrücklich freigegeben ist.

Zu messen sind: korrekte Ausgabe unter den ersten fünf Treffern, Anteil falscher automatischer Übernahmen, zusammengeführte oder aufgeteilte Rücken, Suchdauer, Anfragen pro Scan und Speicherverbrauch während längerer Kameranutzung. Für den Coververgleich denselben Kandidatensatz einmal mit und einmal ohne Bildbonus vergleichen. Erst damit lässt sich eine tatsächliche Verbesserung der Trefferqualität belegen.
