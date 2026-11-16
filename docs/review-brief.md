# Ursprünglicher Prüfkatalog

Historischer Review-Auftrag. Die Umsetzung wurde anschließend ausdrücklich beauftragt.
Aktueller Status: `review-checklist.md` und `implementation-status.md`.

Du bist ein sehr kritischer Senior Software Engineer, Android-Architekt, UX-Reviewer und Produktstratege. Führe ein vollständiges Review des Projekts „Bücher-Radar“ durch.

Projektpfad:
`.`

Kontext und Produktziel:
Bücher-Radar ist eine Android-App für Menschen, die viele gebrauchte Bücher verwalten und regelmäßig prüfen möchten, ob Momox sie aktuell ankauft und welchen Preis Momox bietet. Die App soll besonders für ältere Menschen mit eingeschränktem Sehvermögen leicht verständlich und zuverlässig bedienbar sein.

Die App umfasst unter anderem:

- Verwaltung einer persönlichen Bücherliste
- ISBN- und EAN-Eingabe
- Barcode-Erkennung per Kamera
- OCR-Erkennung von Titel, Autor, ISBN und Buchseiten
- Einzelbuch-, Cover-, Buchrücken- und Regal-Scan
- automatische Unterscheidung zwischen Einzelbuch und Bücherregal
- experimentellen visuellen Cover-Abgleich
- Buchsuche über Open Library, Google Books und die Deutsche Nationalbibliothek
- Zusammenführung von Katalogtreffern
- Momox-Ankaufsprüfung für einzelne, neue oder alle Bücher
- Preise und Prüfhistorie
- Markieren, Mehrfachauswahl, Löschen und „als verkauft“ markieren
- getrennte Ansichten für vorhandene und verkaufte Bücher
- automatische regelmäßige Prüfungen und Benachrichtigungen
- Benachrichtigungsschwellen bei 10 € Versandwert und 15 € Abholwert
- Darkmode, Geräteeinstellung und Protokollansicht

Wichtige Produktanforderungen:

1. Eine korrekte Buchidentifikation ist wichtiger als ein vorschnelles Ergebnis.
2. Ein erkannter Barcode muss nach Möglichkeit zur exakten Ausgabe führen.
3. Unterschiedliche Ausgaben eines Buches dürfen nicht unbemerkt zusammengeführt werden.
4. Mehrere Metadatenquellen sollen sich ergänzen, ohne widersprüchliche oder doppelte Treffer zu erzeugen.
5. Der Cover-Abgleich soll als zusätzliches Indiz helfen und Treffer ohne Cover nicht unangemessen benachteiligen.
6. Ein Regal-Scan muss benachbarte Buchrücken räumlich trennen und Titel, Autor sowie gedrehten Text sinnvoll zuordnen.
7. Wiederkehrende Momox-Prüfungen langer Listen sind die Kernfunktion.
8. Die Bedienung muss platzsparend, verständlich, fehlertolerant und barrierearm sein.
9. Hintergrundprüfungen dürfen Akku, Datenvolumen und externe Dienste nicht unnötig belasten.
10. Fehler und unsichere Erkennungen müssen nachvollziehbar sein, ohne normale Nutzer mit technischen Details zu überfordern.

Arbeitsweise:
Untersuche das gesamte Repository. Lies zunächst alle relevanten Projekt-, Build-, Konfigurations- und Dokumentationsdateien. Erfasse danach Architektur, Datenfluss und zentrale Nutzerabläufe, bevor du Einzelprobleme bewertest.

Führe zunächst ein Review durch und verändere noch keinen Code. Behaupte nichts aufgrund von Dateinamen oder Vermutungen. Belege technische Feststellungen mit konkreten Dateien und möglichst engen Zeilenangaben. Unterscheide klar zwischen:

- nachgewiesenem Fehler
- wahrscheinlichem Risiko
- Wartbarkeitsproblem
- UX-Problem
- Optimierung
- Produktidee
- Annahme, die durch Tests oder Messungen bestätigt werden muss

Prüfbereiche:

1. Produkt und Funktionsumfang

- Erfüllt die App ihr eigentliches Kernziel?
- Welche Funktionen zahlen direkt darauf ein?
- Welche Funktionen wirken unfertig, doppelt oder ablenkend?
- Fehlen entscheidende Abläufe?
- Wo entstehen Sackgassen oder unnötige Arbeitsschritte?
- Welche Funktionsnamen oder Texte sind missverständlich?
- Ist das Verhalten konsistent und für technisch unerfahrene Personen vorhersehbar?

2. Android-Architektur

- Projektstruktur und Verantwortlichkeiten
- Trennung von UI, Geschäftslogik, Persistenz, Kamera und Netzwerk
- Zustandsverwaltung in Compose
- Lebenszyklusbehandlung
- Navigation und Back-Handling
- Coroutine- und Job-Verwaltung
- Fehler- und Abbruchbehandlung
- Wiederherstellung nach Rotation, Prozessende oder App-Neustart
- Gefahr übergroßer Klassen oder Composables
- Erweiterbarkeit und Wartbarkeit

3. Datenmodell und Persistenz

- Room-Schema, DAO-Abfragen und Migrationen
- Eindeutigkeit und Normalisierung von ISBN-10, ISBN-13 und EAN
- Behandlung verschiedener Ausgaben desselben Werks
- verkaufte, gelöschte und aktive Bücher
- Momox-Status, Preis und Prüfzeitpunkt
- Prüfhistorie und Datenherkunft
- Duplikaterkennung
- Konsistenz bei parallelen oder abgebrochenen Vorgängen
- sinnvolle lokale Zwischenspeicherung von Katalogdaten

4. Buchsuche und Quellenzusammenführung

Prüfe Open Library, Google Books und DNB einzeln sowie gemeinsam:

- Anfrageaufbau und Antwortverarbeitung
- Timeouts, Retries und Ratenbegrenzungen
- ISBN-Verifikation
- Zusammenführung gleicher Ausgaben
- Trennung unterschiedlicher Ausgaben
- Umgang mit fehlenden und widersprüchlichen Angaben
- Priorität einzelner Quellen
- Herkunft einzelner Metadaten
- Titel-, Autoren- und Jahresnormalisierung
- alte Bücher ohne ISBN
- Bücher mit mehreren ISBNs
- Trefferbewertung bei OCR-Abweichungen
- Umlaute, Schreibfehler, Wortreihenfolge und voran- oder nachgestellte Autoren
- Gefahr falscher positiver Treffer
- Möglichkeiten für DNB, K10plus oder weitere seriöse Quellen
- rechtliche und technische Grenzen der Dienste

Entwirf gegebenenfalls ein besseres Modell für:

- Kandidatensammlung
- Ausgabeidentität
- Quellenprovenienz
- Feldkonfidenz
- Zusammenführung
- Rangfolge
- Cache
- Anzeige von Unsicherheit

5. Kamera, Barcode, OCR und Coververgleich

Prüfe den vollständigen Kameraablauf:

- CameraX-Lebenszyklus und Ressourcenfreigabe
- Bildrotation und Koordinatensysteme
- Analysefrequenz und Backpressure
- Barcode-Priorisierung
- Erkennung gedruckter ISBNs
- OCR-Stabilisierung über mehrere Bilder
- Auslösezeitpunkt
- automatische Szenenerkennung
- Einzelbuch, Cover, Buchrücken, Buchseite und Regal
- vertikaler beziehungsweise gedrehter Text
- räumliche Gruppierung von Buchrücken
- Vermischung benachbarter Bücher
- Speicherverbrauch und Bitmap-Lebenszyklen
- Crash-Risiken
- Verhalten bei schlechten Lichtverhältnissen, Spiegelungen und unscharfen Bildern

Bewerte den visuellen Cover-Abgleich besonders kritisch:

- Misst die derzeitige Methode tatsächlich eine sinnvolle Coverähnlichkeit?
- Wie empfindlich ist sie gegenüber Perspektive, Beschnitt, Beleuchtung, Aufklebern und Hintergrund?
- Wie wird verhindert, dass visuelle Ähnlichkeit ein textlich korrektes Buch verdrängt?
- Wie können Treffer ohne Katalogcover fair behandelt werden?
- Welche lokale oder servergestützte Methode wäre realistisch?
- Welche diagnostischen Werte sollten im experimentellen Cover-Modus sichtbar sein?
- Wie könnte der Coververgleich später verantwortungsvoll in die allgemeine Erkennung einfließen?

6. Momox-Anbindung

- Wie belastbar ist die aktuelle Abgleichmethode?
- Sind Endpunkte oder HTML-Strukturen fragil?
- Wie werden 404, 429, Timeouts, Captchas und geänderte Antworten behandelt?
- Können Preise einer falschen Ausgabe zugeordnet werden?
- Werden Anfragen sinnvoll sequenziert, begrenzt und zwischengespeichert?
- Ist „alle“, „nur neue“ und „seit längerem nicht geprüft“ sauber definiert?
- Wie funktionieren Abbruch, Fortschritt und Wiederaufnahme?
- Wie werden 10-€- und 15-€-Schwellen berechnet?
- Was geschieht bei Teilfehlern?
- Welche rechtlichen, vertraglichen oder betrieblichen Risiken bestehen?
- Wäre ein eigener Backend-Dienst sinnvoll, und welche Aufgaben sollte er übernehmen?

7. Hintergrundarbeit und Benachrichtigungen

- WorkManager-Konfiguration
- eindeutige Jobs
- Netz- und Akkubedingungen
- Wiederholungen und Backoff
- Zeitzonen
- tägliche und wöchentliche Intervalle
- Android-Berechtigungen und Benachrichtigungskanäle
- Vermeidung doppelter Benachrichtigungen
- Definition eines tatsächlich neuen oder relevanten Ergebnisses
- Schwellenlogik
- Verhalten nach Neustart oder App-Update

8. UI, UX und Barrierefreiheit

Bewerte die App ausdrücklich aus Sicht einer älteren Person mit eingeschränktem Sehvermögen:

- Schriftgrößen und Informationsdichte
- Kontrast in hellem und dunklem Modus
- Touch-Ziele
- Abstand und Gruppierung
- visuelle Hierarchie
- verständliche Beschriftungen
- aktive Reiter und aktueller Kontext
- Android-Systemleisten und Displayausschnitte
- Scrollverhalten
- Tastaturverhalten
- Rücknavigation
- Lade-, Leer-, Fehler- und Erfolgszustände
- Mehrfachauswahl und langes Drücken
- Alle auswählen
- Bestätigungen bei Löschen oder Verkaufen
- Sichtbarkeit neu hinzugefügter Bücher
- Suchfilter, Sortierung und Statusfilter
- Bildschirmleser, Semantik und Content-Descriptions
- dynamische Schriftvergrößerung
- Farbfehlsichtigkeit
- Bedienung im Querformat und auf kleineren Geräten

Erstelle für die wichtigsten Ansichten konkrete UX-Verbesserungsvorschläge. Beschreibe bei Bedarf eine bessere Informationsarchitektur und Navigation.

9. Sicherheit und Datenschutz

- Kamerabilder und deren Lebensdauer
- Protokollierung personenbezogener oder sensibler Daten
- Netzwerkverschlüsselung
- Geheimnisse und API-Schlüssel
- Exportierte Android-Komponenten
- Backup-Verhalten
- lokale Datenbank
- Eingabevalidierung
- XML-Verarbeitung und externe Inhalte
- mögliche Injection-, Spoofing- oder Ressourcenangriffe
- Datenschutz bei Google, Open Library, DNB und Momox
- notwendige Datenschutzhinweise

10. Leistung und Zuverlässigkeit

- UI-Thread-Blockaden
- unnötige Recomposition
- Datenbankzugriffe
- große Listen
- Bildverarbeitung
- Netzwerkfächerung
- Speichernutzung
- Akkuverbrauch
- Startzeit
- Offline-Verhalten
- instabile oder langsame Netze
- Fehlerzustände und Crash-Schleifen
- Wiederholbarkeit schwieriger Fehler

11. Build, Abhängigkeiten und technische Schulden

- Gradle-Konfiguration
- SDK- und Java-Versionen
- veraltete oder riskante Abhängigkeiten
- Versionskonflikte
- Warnungen
- Manifest
- Release-Konfiguration
- Signierung
- ProGuard beziehungsweise R8
- reproduzierbare Builds
- unnötige Dateien oder Altimplementierungen
- Paketstruktur und Namensgebung
- Dokumentation und Einrichtung auf einem neuen Rechner

12. Teststrategie und Beobachtbarkeit

Erstelle eine konkrete Teststrategie für:

- ISBN-Normalisierung
- Katalogzusammenführung
- OCR-Ranking
- Regalgruppierung
- Coverähnlichkeit
- Momox-Antworten
- Room-Migrationen
- Hintergrundjobs
- Compose-Nutzerabläufe
- Regressionen bei bekannten Abstürzen

Schlage eine kleine, datenschutzfreundliche Diagnoseansicht vor, die bei Problemen relevante Informationen zeigt oder exportiert. Technische Logs sollen begrenzt, verständlich und löschbar bleiben.

13. Feature-Ideen

Erarbeite Features, die das Kernziel wirklich verbessern. Bewerte jede Idee nach:

- Nutzen für die Zielgruppe
- Umsetzungsaufwand
- technischem Risiko
- Abhängigkeit von externen Diensten
- Datenschutz
- laufenden Kosten
- Offline-Fähigkeit

Berücksichtige unter anderem:

- Stapelerfassung eines Regals
- spätere Korrektur unsicherer Treffer
- Preisverlauf
- intelligente Prüfreihenfolge
- Wunsch- oder Verkaufsstapel
- Export und Import
- Datensicherung
- Teilen einer Verkaufsliste
- alternative Ankaufdienste
- Erkennung alter Bücher ohne ISBN
- manuelle Metadatenpflege
- Zusammenführen doppelter Einträge
- Gerätewechsel
- verständliche Konfidenzanzeige

Erfinde außerdem eigene, realistische Ideen. Vermeide Features, die nur beeindruckend klingen, aber das Produkt unnötig kompliziert machen.

Gewünschtes Ergebnis:

A. Management-Zusammenfassung

- wichtigste Stärken
- größte Risiken
- drei bis fünf dringendste Maßnahmen
- Einschätzung des aktuellen Reifegrads

B. Projekt- und Architekturübersicht

- relevante Module und Dateien
- zentrale Datenflüsse
- Zuständigkeiten
- externe Dienste
- diagrammartige Darstellung in Mermaid, sofern hilfreich

C. Befunde

Erstelle eine priorisierte Tabelle mit:

- ID
- Priorität P0 bis P3
- Kategorie
- konkreter Befund
- Auswirkung
- Beleg mit Datei und Zeile
- empfohlene Lösung
- geschätzter Aufwand
- notwendige Prüfung oder Test

Prioritäten:

- P0: Datenverlust, erhebliche Sicherheitslücke oder unbrauchbare Kernfunktion
- P1: schwerer Fehler, häufige Fehlidentifikation, Crash oder grundlegendes UX-Problem
- P2: relevante Qualitäts-, Wartbarkeits- oder Leistungsverbesserung
- P3: Feinschliff oder optionale Optimierung

D. Vertiefung der Kernalgorithmen

Bewerte separat:

- ISBN-Auflösung
- Quellenzusammenführung
- OCR-Stabilisierung
- Regalgruppierung
- Szenenerkennung
- Treffer-Ranking
- Coververgleich
- Momox-Prüfstrategie

Gib für jeden Bereich einen verbesserten Ablauf oder konkreten Algorithmus an.

E. UX-Review

- Kritik je Hauptansicht
- bessere Navigation
- Verbesserungen für ältere Menschen
- konkrete Text- und Bedienelementvorschläge
- mögliche neue Ansichtshierarchie

F. Sicherheits- und Datenschutzreview

- konkrete Risiken
- Eintrittswahrscheinlichkeit
- Auswirkung
- Abhilfe

G. Testplan

- wichtigste Unit-, Integrations-, UI- und Gerätetests
- Testdaten und schwierige Grenzfälle
- Reihenfolge der Einführung

H. Feature-Backlog

Ordne Ideen nach:

- sofort sinnvoll
- nach Stabilisierung sinnvoll
- experimentell
- derzeit nicht empfehlenswert

I. Roadmap

Erstelle eine realistische Roadmap:

- Phase 0: kritische Fehler und Abstürze
- Phase 1: zuverlässige Buchidentifikation
- Phase 2: Kernworkflow und Barrierefreiheit
- Phase 3: Momox-Automatisierung und Hintergrundarbeit
- Phase 4: Regal- und Cover-Erkennung
- Phase 5: Produktreife, Release und langfristige Wartbarkeit

Gib pro Phase Ziele, Aufgaben, Abhängigkeiten, messbare Abnahmekriterien und grobe Aufwandsklassen an.

J. Konkreter Umsetzungsplan

Schlage anschließend die ersten zehn Änderungen in sinnvoller Reihenfolge vor. Nenne jeweils:

- betroffene Dateien
- genaue Änderung
- erwarteten Nutzen
- Risiken
- notwendige Tests
- Abhängigkeiten zu späteren Schritten

Qualitätsmaßstab:
Sei skeptisch, konkret und ehrlich. Vermeide allgemeine Ratschläge, die auf jedes beliebige Android-Projekt passen würden. Suche besonders nach Wechselwirkungen zwischen Kamera, OCR, Katalogsuche, Trefferbewertung, Datenbank und Momox-Prüfung. Eine große Zahl oberflächlicher Hinweise ist weniger wert als gut belegte, priorisierte Erkenntnisse.

Mache deutlich, wenn dir für eine sichere Aussage Laufzeitdaten, Gerätetests, Momox-Antworten oder echte OCR-Beispiele fehlen. Schlage dann eine konkrete Messung vor.

Beende das Review mit:

1. den fünf größten Produktrisiken,
2. den fünf wertvollsten Verbesserungen,
3. einem empfohlenen nächsten Entwicklungsschritt,
4. einer Einschätzung, welche Teile vor einer Weiterentwicklung zuerst vereinfacht oder umgebaut werden sollten.

Verändere den Code erst, wenn das Review vollständig vorliegt und ausdrücklich eine Umsetzung angefordert wird.
