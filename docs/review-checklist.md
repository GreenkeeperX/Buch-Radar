# Verbindlicher Abgleich mit dem vollständigen Prüfkatalog

Grundlage: `review-brief.md` und der historische Erkennungsreview. Diese Tabelle
trennt implementierte Mechanismen von noch fehlender Abnahme. Sie behauptet nicht,
dass jede im Prüfkatalog gestellte Frage bereits vollständig untersucht wurde.
Alle offenen Verbesserungen bleiben Teil des Auftrags; bloße Feature-Ideen werden
nicht ohne Nutzennachweis zu Pflichtfunktionen oder neuen externen Diensten.

Status: **umgesetzt** = Code und gezielte Prüfung vorhanden; **teilweise** =
Mechanismus vorhanden, Restarbeit/Abnahme offen; **offen** = noch nicht erledigt;
**extern** = Anbieterzugang, Schlüsselverwaltung oder freigegebene Testdaten nötig.

| ID | Bereich / Verbesserung | Status | Code / Nachweis | Verbleibende Abnahme |
|---|---|---|---|---|
| R01 | Stabile Identität, ISBN-10/13, alte Bücher ohne ISBN | umgesetzt | `domain/BookIdentity.kt`, `BookEntity.kt`, Identity-/DB-Tests | Weitere reale Ausgaben über Referenzsatz |
| R02 | Migration ohne Verlust, Duplikate, verspätete Antworten | umgesetzt | Room 4→5, `DatabaseRegressionTest` | Neue Migrationen immer mit Altbestand prüfen |
| R03 | Regal-Funde bei Rücknavigation und Prozessende erhalten | umgesetzt | `ShelfReviewController`, AtomicFile, Shelf-Tests | Kamera bis Bestätigung mit Referenzregal testen |
| R04 | Gemeinsame Momox-Queue, Wiederaufnahme, begrenzte Wiederholung | umgesetzt | `MomoxCheckCoordinator`, DAO, DB-Tests | Langer Lauf mit realen Dienstunterbrechungen |
| R05 | Angebotsvalidierung, Fehler getrennt vom letzten Preis, Verlauf | umgesetzt | `MomoxCatalog`, Response-/DB-Tests, Buchdetails | Langfristige Endpunktkompatibilität nicht zugesichert |
| R06 | Offizieller Momox-Zugang / öffentliche Nutzung | extern | Öffentliche APK ohne Zugang; lokaler Build konfigurierbar | Vertrag/Zugang durch Anbieter klären |
| R07 | Hintergrundbedingungen, Intervalle, Meldungen, Schwellen | teilweise | WorkManager, Notification-Deduplizierung | Automatisierte Schwellen-/Benachrichtigungs- und Neustarttests ergänzen |
| R08 | Konkrete Katalogausgaben statt Werk-ISBN-Zuordnung | teilweise | `BookCatalog`, ISBN-Verifikation | Ausgabeautoren und mehrere ISBNs je Datensatz umfassender prüfen |
| R09 | Suchbudget, Cache, Quellenfehler, Metadatenkonflikte | teilweise | 0.13: `CatalogAccess`, `fieldValues`, gezielte Tests | Konflikte dauerhaft pro Buch speichern; Verteilung des Budgets zwischen Quellen messen |
| R10 | OCR-Abweichungen, Umlaute, Autor vorne/hinten | teilweise | Varianten, Tokenbewertung | Referenzfälle und Top-5-Trefferquote, kurze/generische Titel |
| R11 | Kamera-Lebenszyklus, Rotation, Backpressure, Ressourcen | teilweise | `BookScannerView`, lokale OCR, Cleanup | Langlauf-, Speicher- und Rotationstests auf Gerät |
| R12 | Sichtbare Rückenregionen, gedrehte Schrift, Regionstracking | teilweise | 0.14: Bildkanten, getrennte Zuschnitte/OCR, Schrägkorrektur | Mehrere Regalreihen, sichtbare Regionen/Tracking, unabhängige Bildfälle |
| R13 | Auslösezeitpunkt, Bildqualität, Szenenerkennung | teilweise | `RecognitionStability`, Bildruhe und Konturen | Messung falscher Auslösungen, Buchseite gegen Regal |
| R14 | Coverentzerrung, Zuschnitt, Bildvergleich kalibrieren | offen | Bisher mittiger Zuschnitt/Hash/Histogramm | Vergleich mit/ohne Bildbonus auf identischen Kandidaten |
| R15 | Erkennungsbenchmark mit schwierigen Referenzfällen | teilweise | 0.14: drei öffentliche Bilder, markierte Regalregionen, reproduzierbarer Android-Bericht | Mehr unabhängige Bilder, bestätigte Soll-ISBNs, Live-Bewegung und Katalogtrefferquote |
| R16 | Navigation, sichere Insets, klare Reiter und Einstellungen | teilweise | Eigene Ansichten, SafeDrawingPadding, Geräteprüfung | Kleine Displays, Querformat, vollständiger Bedienablauf |
| R17 | Großschrift, Touch-Ziele, TalkBack, Farbe nicht allein | teilweise | Größere Texte, Tab-/Auswahlsemantik | Automatisierte Großschrift-/Semantiktests, TalkBack am Gerät |
| R18 | UI/Logik trennen, Prozesswiederherstellung | teilweise | ViewModel, separate Settings/Details/ShelfController | Hauptansicht zerlegen; übrigen Eingabe-/Auswahlzustand wiederherstellen |
| R19 | Verkaufsstapel, teilbare Liste, Mehrfachaktionen mit Fehlerbehandlung | teilweise | Auswahl/Archiv vorhanden | Vorschau/Summe/Teilen und sichere Stapelaktionen |
| R20 | Sicherung, Import, Gerätewechsel | teilweise | JSON, Transaktion, Vorschau, kein Überschreiben | Regal-Funde/Verlauf beim Gerätewechsel, Import-Regressionstests |
| R21 | Datenschutz, begrenzte Diagnose, keine privaten Artefakte | teilweise | Lokales Log, Datenschutzhinweise, Backup-Ausschlüsse | Log-Redaktion/Import-/XML-Grenzfälle automatisieren |
| R22 | Altzugang aus Git-Historie / Rotation | extern | Aktueller Code und Downloads bereinigt | Koordinierte Historienbereinigung, Zugang ersetzen; kein stilles Force-Push |
| R23 | Abhängigkeiten, Produktionssignierung, reproduzierbarer Build | teilweise / extern | Gradle-Wrapper, JDK17, CI | Updates einzeln prüfen, dauerhafter privater Release-Schlüssel, R8/Release-Test |
| R24 | GitHub, APK, Claude-Quellen, Lizenz und Übergabe | umgesetzt | GPL-3.0, Releases ab 0.12, CLAUDE.md, Prüfsummen | Bei jedem geprüften Release fortführen |
| R25 | Große Listen, Offline-/Störungsfälle, Akku/Startzeit | teilweise | Begrenzte Caches/Queue, einige Ausfalltests | Messungen und Lasttests mit synthetischen Beständen |

## Reihenfolge ab diesem Stand

1. R09 abschließen und prüfen: langsame/ausgefallene Quellen dürfen nutzbare Treffer
   nicht verwerfen oder Widersprüche verdecken.
2. R15 als Messgrundlage, dann R12–R14: Bildregionen und überprüfbare Qualität.
3. R17–R20: Bedienbarkeit, Wiederherstellung und vollständiger Verkaufsablauf.
4. R07, R21, R23, R25: automatisierte Grenzfall-/Langlauf-/Releaseprüfungen.
5. Externe Voraussetzungen separat verfolgen. Sie blockieren keine unabhängigen
   Codeverbesserungen, dürfen aber nicht als technisch erledigt gelten.

Weitere Kataloge und Ankaufdienste erst integrieren, wenn erlaubte Schnittstelle,
Ausgabeidentität und Nutzen geprüft sind. Kein automatischer Verkauf, kein Upload
privater Kameraaufnahmen und kein dauerhaft betriebener Backenddienst ohne Auftrag.
