# Umsetzung des Reviews

Der vollständige Bereichsabgleich steht in [review-checklist.md](review-checklist.md).
Die Umsetzung erfolgt in überprüfbaren Schritten. Experimentelle Qualität gilt erst
nach Messung an freigegebenen Scanbeispielen als bestätigt.

## Reihenfolge
1. Identität, Datenkonsistenz, verlorene Regal-Funde, Regressionstests.
2. Gemeinsame persistente Momox-Warteschlange, Fehler und Prüfhistorie.
3. Katalogqualität, Quellenfehler, Cache und Antwortvalidierung.
4. Scanner-Lebenszyklus, Qualitätsprüfung und räumliche Erkennung.
5. Navigation, Barrierefreiheit, Einstellungen, Diagnose und Datensicherung.
6. Release-Prüfungen und bereinigte Dokumentation.

## Implementiert für 0.11.0
- Stabile lokale Buch-IDs, ISBN-Normalisierung und getrennte Bücher ohne ISBN.
- Datenbankmigration 4 → 5; ISBN-Korrekturen verwerfen ausgabenabhängige Altwerte.
- Gemeinsame persistente Momox-Warteschlange mit Fortsetzen, Abbrechen und
  begrenzten Wiederholungen. Ein verzögerter Auftrag blockiert andere Bücher nicht.
- Letztes Angebot und letzter Prüfversuch getrennt; Fehler löschen keinen alten
  Preis. Prüfverlauf in den Buchdetails, Datum einschließlich Uhrzeit.
- Antwortvalidierung für ISBN, Buchtyp, Währung und exakte positive Centbeträge.
  Eine Live-Abfrage mit öffentlicher Beispiel-ISBN bestätigte HTTP 200,
  `status=offer`, `currency=eur` und `product.type=book`.
- Konkrete Open-Library-Ausgaben, Quellenherkunft, begrenzter Katalogcache und
  Drosselung je Quelle. Abbrüche werden nicht als leere Trefferliste verschluckt.
- Stabile Regal-Fund-IDs und schrittweise Trefferanzeige; Bestätigung entfernt
  nur die Vorschläge des bestätigten Fundes.
- CameraX-Ressourcenfreigabe, OCR außerhalb des UI-Threads, gedrehte OCR,
  Bildruhe und Textstabilität vor automatischer Übernahme.
- Coverabgleich mit gemeinsamem Bildcache, maximal zwölf Kandidaten und acht
  Sekunden Downloadbudget. Texttreffer ohne Cover bleiben erhalten.
- Separate Einstellungen und Diagnose, sichtbare Version, große bedienbare
  Schalter, JSON-Sicherung und Import mit Vorschau und Transaktion.
- Such-/Auswahlzustand im ViewModel überlebt eine Activity-Neuerstellung.
- Monochromes Benachrichtigungssymbol und weniger doppelte Benachrichtigungen.
- Ungenutzte ältere Scanner und Demo-Sync-Worker entfernt.
- GPL-3.0-only, Build-/Lint-/Test-Workflow, kein Zugangswert im aktuellen Quelltext.

## Nächste Arbeitspakete
1. Scanqualität messbar machen: freigegebene lokale Referenzbilder mit Soll-ISBN,
   getrennte Messung von Barcode, gedruckter ISBN, Cover, alten Schriften und Regal.
2. Echte Buchrückengrenzen und perspektivische Einzelzuschnitte statt alleiniger
   Textbox-Heuristik; Grenzfälle sichtbar zur Korrektur anbieten.
3. Feldherkunft dauerhaft am Buch speichern und Budgetverteilung zwischen Quellen
   messen; Gesamtbudget und sichtbare Teilfehler/Konflikte sind ab 0.13 implementiert.
   Weitere Quellen erst nach geprüfter Schnittstelle und nachgewiesenem Nutzen.
4. Einen künftigen Coververgleich für Einzelbücher an Referenzbildern prüfen.
   Der bisherige Vergleich entfiel nach der Messung (AUC ca. 0,50–0,66, kein
   brauchbarer Schwellenwert). Die Suche übernimmt die Textreihenfolge.
   Die separat getestete Regel `coverEvidenceAllowed` lässt eine künftige Prüfung
   im Buchmodus oder bei einem erkannten Einzelcover im Automatikmodus zu.
   Regalscans und Regalkorrekturen sind ausgeschlossen.
5. Hauptansicht weiter in eigenständige Ansichten und Aktionen aufteilen;
   Großschrift, TalkBack, Rotation und Prozesswiederherstellung automatisiert prüfen.
6. Verkaufsvorbereitung mit Auswahl und teilbarer Liste, optionale Bestandskopien
   und Preisänderungen; keine automatischen Verkäufe.
7. Abhängigkeiten einzeln aktualisieren, Release-Signierung und reproduzierbare
   Releaseprüfung einrichten. Öffentliche Test-APKs nur ohne Zugangswerte;
   Produktionsfreigabe erst mit dauerhafter Signierungsidentität.

## Prüfstand 0.11.0
- Android-Debug-Build und Testpaket erfolgreich gebaut.
- 24 JVM-Tests erfolgreich; Lint ohne Fehler (16 Hinweise/Warnungen, vor allem
  neuere Abhängigkeiten und Launcher-Ressourcen).
- Acht isolierte Datenbanktests auf einem echten Android-Gerät erfolgreich:
  Migration, ISBN-Wechsel, Angebotserhalt bei Fehlern, Duplikate, Wiederaufnahme,
  Warteschlangenreihenfolge und verspätete Metadaten.
- Tatsächlichen Bestand vor dem Update privat gesichert und nach der Migration
  feldweise verglichen: Buchdaten und Verkaufsstatus unverändert, Integrität ok.
- Fünf Backend-Vertragstests erfolgreich; der Backend-Entwurf bleibt ein Demo.
- Live-Momox-Formatprobe erfolgreich. Das belegt Erreichbarkeit und Format zum
  Testzeitpunkt, keine dauerhafte Zusicherung des Endpunkts.
- Keine gemessene Erkennungsquote und noch keine vollständige barrierefreie
  Bedienprüfung. Diese Punkte bleiben ausdrücklich offen.

## Nachbesserung 0.11.2
- Geräteprüfung zeigte überlagerte Inhalte in Vollbilddialogen. Einstellungen
  einschließlich Diagnose/Datenschutz sowie Buchdetails sind jetzt normale
  Activity-Ansichten mit sicheren Bildschirmabständen und eigener Zurück-Navigation.
- Ein Geräte-Absturzbericht belegte einen fehlenden Compose-FrameClock beim
  automatischen Scrollen nach dem Speichern. Der Hintergrundauftrag übermittelt
  jetzt nur die neue Buch-ID; ein UI-Effekt wartet auf den sichtbaren Listeneintrag
  und führt die Animation aus. Das betrifft Barcode-/ISBN-Eingabe und Suchtreffer.
- Zwei neue Compose-Gerätetests decken den zeitversetzten Datenbankeintrag und
  den Übergang von der Kamera zur Liste ab.
- Alle zehn Gerätetests erfolgreich; Espresso auf 3.7.0 angehoben, weil die ältere
  Testbibliothek eine auf Android 16 entfernte InputManager-Methode verwendete.
- Build und Lint erfolgreich. Versionsnummer und Diagnosezugang auf dem Gerät
  sichtbar oberhalb der Navigationstasten geprüft; Langdruck-Auswahl und Reiterwechsel
  ebenfalls geprüft. Der Nutzer hat anschließend Barcode-Scans ohne Absturz bestätigt.

## Regal-Nachbearbeitung 0.12.0
- Erkannte Texte, stabile Fund-IDs, ausgabenspezifische Treffer, offene Suchaufträge
  und zurückgestellte Funde werden atomar lokal gespeichert. Keine Kamerabilder.
- Beim App-Neustart werden offene Funde geladen. Erst „Regal-Funde fortsetzen“
  startet die verbleibenden Suchanfragen; bereits gespeicherte Treffer bleiben nutzbar.
- „Zur Liste“ pausiert; weitere Scans ergänzen bestehende Funde. Korrekturen haben
  Vorrang, anschließend werden die übrigen offenen Funde weitergesucht.
- „Für später zurückstellen“ bewahrt den Fund einschließlich vorhandener Treffer.
  Endgültiges Verwerfen erfolgt über das Aktionsmenü mit Rückfrage.
- Fehler eines Katalogaufrufs verhindern nicht die übrigen Suchen. Fehlgeschlagene
  Funde lassen sich gezielt erneut suchen. Speicherfehler lassen den Fund bestehen.
- Stabile Buch-IDs beim Bestätigen vermeiden doppelte ISBN-lose Einträge, falls
  der Prozess zwischen Buchspeicherung und Erledigen des Fundes endet.
- Die lokale Datei liegt außerhalb des Android-Backups und des öffentlichen Repos;
  sie gehört nicht zum Bücherlisten-Export. Keine Datenbankmigration erforderlich.

## Prüfstand 0.12.0
- 32 JVM-Tests und zwölf Gerätetests erfolgreich, einschließlich Regal-Persistenz,
  beschädigter Speicherdatei und der zwei Compose-Scrollregressionen.
- Der Teststart wartet ausdrücklich auf die angehängte Compose-Oberfläche; ein
  zuvor sporadischer Fehler beim kalten Activity-Start wird damit vermieden.
- Android-Build und Lint erfolgreich, fünf Backend-Vertragstests erfolgreich.
- Version 0.12.0 auf dem angeschlossenen Gerät installiert. Die reale Qualität
  der Regal-/Coveridentifikation ist dadurch weiterhin nicht nachgewiesen.

## Katalogsuche 0.13.0
- Gemeinsames Netzwerkbudget von 30 Sekunden pro ISBN-/OCR-Suche einschließlich
  aller Varianten und Wartezeiten der Quelldrosselung. Frühe Treffer bleiben erhalten.
- Sichtbare Hinweise bei ausgefallenen Quellen und Zeitlimit, auch in gespeicherten
  Regal-Funden. Vollständiger Ausfall wird nicht als erfolgreiche Leersuche behandelt.
- Widersprüchliche Titel, Autoren, Jahre und Verlage bleiben mit Quellenangaben
  am Kandidaten erhalten; wiederholtes Zusammenführen bewahrt die Feldherkunft.
- ISBN-Scans mit widersprüchlichem Titel/Autor verlangen eine Trefferbestätigung.
  Automatisches Ergänzen bei Widersprüchen verändert die gespeicherten Angaben nicht.
- Suchtext und Varianten begrenzt; DNB-Antworten mit Dokumenttyp-/Entitydeklarationen
  abgelehnt. Die Kandidatenansicht kann Quellenangaben platzsparend aufklappen.
- 38 JVM-Tests, zwölf Gerätetests, Build und Lint erfolgreich; 0.13.0 installiert.
- Weiter offen: dauerhaftes Speichern der Feldbelege am bestätigten Buch, faire
  Budgetverteilung bei sehr langsamen Quellen sowie gemessene Erkennungsqualität.

## Regal-Bildregionen 0.14.0
- Öffentlicher, herunterladbarer Bildtestsatz mit drei dokumentierten Fotos,
  Quellen, Lizenzangaben und Prüfsummen. Opt-in-Benchmark läuft lokal auf Android.
- Lange Farbübergänge liefern mögliche Rückenbegrenzungen. Die Bildbereiche werden
  vor der OCR getrennt und moderat entzerrt; kurze Schriftkanten sollen keine
  Buchgrenze erzeugen. Vorhandene vertikale Textkästen schützen vor falschen Schnitten.
- Kleine Raster getrennter Zuschnitte begrenzen die Zahl der OCR-Aufrufe. Wörter
  werden nach Bildregion zugeordnet; gedrehte Lesungen nach OCR-Evidenz gewichtet.
- Automatische und manuelle Regalübernahme verwenden denselben zusätzlichen
  Bilddurchlauf. Unlesbare Bereiche oder ein erreichtes Zeitlimit werden gemeldet.
- Kamera fordert eine höhere Analyseauflösung an, soweit vom Gerät unterstützt;
  Arbeitsbilder bleiben auf 1920 Pixel begrenzt. Bitmaps werden nach Übergabe oder
  Abbruch freigegeben; die Regal-Fortsetzung speichert weiterhin keine Bilder.
- Grenzen: Kantenheuristik für überwiegend aufrecht stehende Bücher, noch keine
  robuste Verfolgung mehrerer Regalreihen. Keine allgemeine Erkennungsquote und
  keine verbesserte Frakturlesung behauptet. Gemessene Resultate unter
  `tools/recognition/`; Einzelfoto-Verbesserungen sind kein Nachweis für alle Regale.

## Geräteprüfung 0.14.0
- 41 JVM-Tests sowie Android-Build und Lint erfolgreich.
- Alle 13 regulären Gerätetests bestanden, einschließlich zweifachem Öffnen,
  Analysieren und Schließen der echten Kamera. Keine Testbilder gespeichert und
  keine Bücher durch den Kameratest eingefügt.
- CameraX-Vorschau, Bindung und Freigabe laufen ausdrücklich auf dem Hauptthread.
  Eine verzögerte Freigabe entfernt nur die eigenen Kamera-Anwendungsfälle, nicht
  die Bindung eines inzwischen neu geöffneten Scanners.
- Langlauf, Drehung während laufender OCR und allgemeine Erkennungsqualität bleiben
  weitere Abnahmen.

## Oberfläche und Listenaktionen (0.15.0)
- Eigenes Farbschema hell/dunkel aus den Farben des App-Symbols, semantische
  Statusfarben (`ui/Theme.kt`), Material-Icons statt Textzeichen, Fenster-Theme
  ohne weißes Aufblitzen beim Start im Dunkelmodus.
- Navigationsleiste mit Anzahl, Filter-Chips, Gesamt-Ankaufwert, Fortschrittsbalken
  der Momox-Prüfung, Kennzeichnung eingereihter Bücher, Leerzustände.
- Buchkarten mit Cover-Platzhalter und Überlaufmenü; Auswahlmodus mit kontextueller
  Titelleiste. Statusmeldungen als farbige, von TalkBack angesagte Banner.
- Entfernen, Verkauft-Markieren, Zurückholen, Bearbeiten und Prüfstart fangen
  Datenbankfehler ab. Rückgängig per Snackbar für Entfernen und Verkauft-Markieren.
- Filter/Sortierung/Summen in `domain/BookListQuery.kt` mit JVM-Tests; Karten und
  Dialoge in `BookCards.kt`. Kein Schemawechsel.
- Design-Review (Heuristiken, Screenshots vom Gerät): Scannen als schwebender Knopf
  unten, Momox-Prüfung und letztes Ergebnis in einer Zeile, nur ein angebotener Preis
  als farbiger Chip, Filter-Chips mit Anzahl, Sortierung als Symbolknopf, Kartenaktionen
  auch in den Buchdetails, Darstellung als Segmentknopf.
- Zweitprüfung (statisch, Codex) behoben: Listen-Keys getrennt (importierte ID
  „list-header“ hätte die Liste abstürzen lassen), Rückgängig stellt die tatsächlich
  gelöschte Zeile wieder her und liegt im ViewModel (übersteht Drehen), veraltete
  Pausierantwort hält die Warteschlange nicht mehr an, verweigerte Kamera setzt die
  Regal-Bearbeitung fort, mehrzeilige Eingabe wird nie zu einer ISBN verbunden.
- Offen: Großschrift/TalkBack-Abnahme, Rückgängig nach Drehen am Gerät,
  weitere Aufteilung von `MainActivity` (Regal- und Trefferansicht).

## Überarbeitung der Listensuche (Arbeitsstand)
- Bei Texteingabe ersetzt die Trefferzahl den Listenkopf. Eine laufende oder
  pausierte Momox-Prüfung zeigt dort ihren Fortschritt. Beim Leeren erscheinen
  Übersicht, Prüfaktionen, Filter und Sortierung wieder.
- Unter dem Suchfeld stehen die Hauptaktion und die Kamera, bei Buchstaben
  zusätzlich das Stiftsymbol für die manuelle Erfassung. Die Aktionszeile hat
  mindestens 48 dp hohe Tippflächen und kann bei großer Schrift umbrechen.
- Im direkten JVM-Lauf bestanden alle 83 Tests, darunter der Test der Anzeige
  für null, einen und mehrere Treffer. Alle sechs Gerätetest-Quelldateien ließen
  sich kompilieren. Der separate Lint-Lauf meldete drei Warnungen und keine Fehler.
  Die Socket-Sperre der Build-Umgebung blockierte den Gradle-Prüflauf.
  APK-Bau sowie Großschrift- und TalkBack-Prüfung der Suchzeile am Gerät stehen aus.

## Erkennung (0.15.0)
- ISBN aus Text: bis zu zwei Trenner zwischen Ziffern (OCR liest „-“ als „.“) und
  Klarschriftzeile unter dem Barcode mit typischen Fehllesungen; nur 978/979 mit
  gültiger Prüfziffer. Pixel 9a, 3 öffentliche ISBN-Fotos: vorher 14/18 Fälle,
  nachher 24/24 ISBNs in allen 18 Fällen, keine Falschtreffer (`tools/recognition`).
- Buchrücken: Stotter-/Doppellesungen werden je Rücken entfernt (`cleanSpineText`).
- Regal: Beim Abschluss ein hochauflösendes Foto (Ziel 2592×1944, max. 2560 px) statt
  des Analysebilds (1920×1440); Rückfall auf das Analysebild. Wirkung am Gerät noch
  nicht gemessen.
- Scanner hing am Gerät bei „Noch ruhig halten“ (Bewegungswerte 18–58 über der
  Grenze 8; Regaltext nie zweimal identisch, daher Knopf gesperrt). Jetzt: Grenze 12,
  Helligkeitsänderung/Flackern wird herausgerechnet, Regal stabil ab 70 %
  Wortüberlappung. Kompaktere Bedienleisten im Querformat. Nachweis am Gerät offen.
- Nur Autor erkannt (z. B. Rücken „Arthur Hailey“ ohne lesbaren Titel): Die Suche
  fragt zusätzlich nach Büchern dieses Autors (Google Books `inauthor`, DNB `per`),
  zeigt zuerst verschiedene Titel, dann weitere Ausgaben, ohne Trefferbewertung, und
  weist mit einem Hinweis auf die nötige Auswahl hin. Kein automatisches Raten.
  Grundlage sind JVM-Tests der Erkennungsregel; Wirkung am Gerät noch nicht gemessen.
- Codex-Prüfung, Runde 2 (offene Befunde): Rückgängig sammelt mehrere schnelle Lösch-/
  Verkauft-Aktionen statt nur die letzte zu behalten und läuft nicht ab, solange Details oder
  Einstellungen die Liste verdecken. Ein während der Momox-Anfrage verkauftes oder ersetztes
  Buch pausiert die Warteschlange nicht mehr. Nach HTTP 429 prüfen am Quellen-Tor wartende
  parallele Suchen die Sperre erneut. Regal-Foto nur bei gleichem Bildinhalt wie das
  analysierte Bild (Abweichung wird protokolliert), sonst Analysebild. Manuelles „Jetzt
  suchen“ gilt nur für den angezeigten Text; bei verändertem Bild fragt der Scanner erneut.
  Rücken mit nur einer ISBN bleiben im Regalpfad erhalten. Builds mit privaten Zugangswerten
  geben einen Hinweis aus. Am Gerät noch nicht geprüft.

## Externe Voraussetzungen
- Der bisherige Momox-Zugang ist nicht als öffentliche Schnittstelle zugesichert.
  Ein offizieller Zugang muss mit dem Anbieter geklärt werden. Die FAQ erwähnt
  eine API für Großbestände: https://www.momox.de/fragen-und-antworten/
- Ein bereits veröffentlichter Zugangswert bleibt in der Git-Historie, bis eine
  koordinierte Bereinigung erfolgt. Lokale Build-Konfiguration schützt nicht vor
  Extraktion aus einem APK. Keine Schlüssel in öffentliche Dateien aufnehmen.
- Echte Erkennungsraten, Frakturschrift und Regaltrennung benötigen einen lokal
  gehaltenen, beschrifteten Bilddatensatz und Tests auf dem Zielgerät.
- Lizenzentscheidung: GPL-3.0-only. Eine dauerhafte Release-Signierungsidentität
  ist vor produktiver Verteilung einzurichten. Vorabversionen sind als
  debug-signierte Test-APKs ohne vorkonfigurierten Momox-Zugang gekennzeichnet.

## Cover-Bestätigung (Branch claude/cover-orb)

- Nur Bestätigung „Cover passt zu deinem Foto“, nie Abwertung oder Ausblenden. Ein bestätigter
  Kandidat rückt nur vor andere Ausgaben desselben Titels.
- Nur hinter `coverEvidenceAllowed` (Modus Buch oder Automatik mit stabil erkanntem Einzelbuch),
  nie Regal oder Regal-Korrektur. Das Kamerabild (max. 1000 px) bleibt im Speicher und wird nach
  dem Vergleich verworfen.
- OpenCV ORB (1500 Merkmale, Höhe 600 px, Ratio 0,75) plus RANSAC-Homographie (5 px), Schwelle
  100 Inlier, höchstens 10 Kandidaten, Zeitlimit 8 s, abbrechbar.
- Messung an lokalen Cover-Fotos (nicht im Repo): offline und auf dem Gerät keine Bestätigung
  einer falschen Ausgabe oder eines anderen Buchs; Grenzen: Wirkung nur, wenn der Katalog ein Cover
  derselben Gestaltung liefert. Opt-in-Messung: `CoverBenchmarkTest` mit `-e coverBenchmark true`.
- Die App enthält nur noch arm64-v8a-Bibliotheken; OpenCV kostet etwa 24 MB in der APK.
