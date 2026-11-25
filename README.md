# Bücher-Radar

Android-App zur lokalen Verwaltung gebrauchter Bücher und zur wiederkehrenden
Prüfung von Ankaufspreisen. Kotlin, Jetpack Compose, Room, CameraX und WorkManager.

## Downloads und Weiterentwicklung

Aktuelle Testversionen stehen unter [GitHub Releases](https://github.com/GreenkeeperX/Buch-Radar/releases).
Jede veröffentlichte Version erhält eine APK und ein Quellcode-ZIP für die
Weiterarbeit, auch mit Claude. Einstieg: [CLAUDE.md](CLAUDE.md),
[Übergabe](docs/handoff.md) und [Veröffentlichungsablauf](docs/release-process.md).

Die öffentliche Test-APK enthält **keinen vorkonfigurierten Momox-Zugang**.
Bücherverwaltung, Suche und Scanner sind nutzbar; Momox-Prüfungen benötigen
einen separat berechtigten Zugang beim Build. Die APK ist derzeit debug-signiert,
keine produktiv signierte Store-Version. Vor einem Installationswechsel die Liste
exportieren; unterschiedliche Signaturen erlauben kein direktes Update.

## Funktionen

- ISBN-/Titelsuche über Open Library, Google Books und DNB; konkrete Open-Library-
  Ausgaben statt Kombination beliebiger ISBNs mit Werkdaten.
- Barcode und lokale OCR mit Automatik-, Einzelbuch- und Regalmodus.
- Bildgestützte Trennung und separate OCR möglicher Buchrücken im Regal.
- Regal-Funde lokal fortsetzen, korrigieren und für später zurückstellen;
  erkannte Texte und Treffer bleiben auch nach einem App-Neustart erhalten.
- Lokale Liste, Bearbeitung, Auswahl, verkauftes Archiv und Bücher ohne ISBN.
- Gemeinsame persistente Momox-Warteschlange für manuelle und automatische Prüfungen.
- Letzter Versuch und letztes erfolgreiches Angebot getrennt; lokaler Prüfverlauf.
- Tägliche/wöchentliche Prüfung bei Netzverbindung; Android bestimmt den genauen
  Ausführungszeitpunkt. Fortschritt bleibt bei Unterbrechungen erhalten.
- Benachrichtigungen für Änderungen, optionale Schwellen 10/15 Euro.
- Hell/Dunkel/Geräteeinstellung, separate Diagnoseansicht, JSON-Export und Import.

## Grenzen

Dies ist weiterhin ein Prototyp. OCR, Szenenerkennung und Regalgruppierung sind
heuristisch und müssen an echten Büchern überprüft werden. Die Katalogsuche
verwendet ISBN oder Suchtext. Ausgabe und ISBN vor einer Übernahme vergleichen.

Die bisherige Momox-Anbindung verwendet einen nicht als öffentliche Schnittstelle
zugesicherten App-Endpunkt. Ein stabiler autorisierter Zugang ist noch zu klären.
Momox erwähnt eine API für Großbestände in seiner [FAQ](https://www.momox.de/fragen-und-antworten/).
Die App schließt keine Verkäufe ab. Preise und Annahme können sich ändern.

Die Bücherseite nennt 15 Euro für Abholung, die allgemeine FAQ formuliert die
10-Euro-Schwelle anders. Maßgeblich sind die Bedingungen beim Verkaufsabschluss.
Die Benachrichtigungsschwelle ist eine persönliche Einstellung.

## Einrichtung und Build

Voraussetzungen: JDK 17, Android SDK 35, Android ab API 26. Android Studio ist optional.
Im Ordner `android`:

```sh
./gradlew testDebugUnitTest assembleDebug
```

SDK-Pfad lokal in `android/local.properties` als `sdk.dir` konfigurieren.
Ein berechtigter Momox-Zugang kann über die Umgebungsvariable `MOMOX_API_TOKEN`
oder `momox.apiToken` in dieser ignorierten Datei bereitgestellt werden.
Ohne Zugang lässt sich die App bauen und die Bücherverwaltung nutzen; die
Momox-Prüfung pausiert mit einem Hinweis. Zugangswerte niemals einchecken.
Ohne eigenen Schlüssel nutzt die Google-Books-Suche das gemeinsame anonyme
Tageskontingent, das oft erschöpft ist (HTTP 429; die App pausiert die Quelle dann
für drei Stunden und meldet „Anfragelimit erreicht“). Für einen privaten Build kann ein
eigener Schlüssel über `GOOGLE_BOOKS_API_KEY` oder `googleBooks.apiKey` gesetzt werden.
Build-Konfiguration macht einen Wert nicht geheim gegenüber APK-Analyse.

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
Eine dauerhafte Produktionssignierung ist noch offen; öffentliche Downloads sind
ausdrücklich Testversionen. Zugangswerte dürfen nicht in öffentliche APKs gelangen.

## Tests

```sh
./gradlew testDebugUnitTest lintDebug
./gradlew connectedDeviceTestAndroidTest
```

Optionaler lokaler Bildbenchmark: [Anleitung und öffentliche Bildquellen](tools/recognition/README.md).
Der kleine Testsatz ersetzt keine Erprobung an unterschiedlichen realen Regalen.

Die Gerätetests verwenden separate Testdatenbanken. Keine private Bücherliste
oder private Kameraaufnahme in das öffentliche Repository übernehmen.

Backend-Vertragstests nach Installation von `backend/requirements.txt`:
`cd backend` und `python -m unittest discover -s tests -v`.

## Daten und Sicherung

Bilder der Kamera werden nicht hochgeladen. Katalogdienste erhalten Suchtext/ISBN,
Momox erhält die zu prüfende ISBN. Eine Internetverbindung ist dafür erforderlich.
Die Bücherliste bleibt lokal. Export/Import ist über die Einstellungen verfügbar;
vorhandene ISBNs werden beim Import nicht überschrieben. Preise werden neu geprüft.
Offene Regal-Funde werden separat lokal gesichert, ohne Kameraaufnahmen. Sie sind
nicht Teil des Bücherlisten-Exports oder der Android-Systemsicherung.
Android-Systembackup schließt Bücherdatenbank und Diagnose aus; für einen
Gerätewechsel ausdrücklich exportieren und importieren.

## Backend-Entwurf

`backend/` enthält einen unangebundenen FastAPI-Demoentwurf. Er liefert `UNKNOWN`,
keine realen Momox-Angebote. Die Android-App benötigt ihn nicht für Hintergrundarbeit.
Zeitstempel des Vertrags sind Unix-Millisekunden. Vor öffentlichem Betrieb fehlen
unter anderem Authentifizierung, serverseitige Begrenzungen und ein autorisierter
Anbieterzugang. Details siehe `backend/README.md`.

## Entwicklungsstand

Die Grenzen und verbleibenden Prüfungen stehen in `docs/implementation-status.md`.

## Lizenz

Bücher-Radar steht unter der GNU General Public License, Version 3
(`GPL-3.0-only`). Der vollständige Lizenztext steht in [LICENSE](LICENSE).
Bei Weitergabe veränderter Versionen muss der zugehörige Quellcode unter dieser
Lizenz zugänglich sein. Private Änderungen müssen nicht veröffentlicht werden.

Verwendete Bibliothek mit eigenem Lizenzhinweis: [OpenCV](https://opencv.org)
(Apache License 2.0, GPL-3.0-kompatibel) für die Cover-Bestätigung (ORB-Merkmale
und RANSAC-Homographie, nur auf dem Gerät).
[Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) mit Tesseract
und Leptonica sowie die Sprachmodelle `deu`, `deu_latf` aus `tessdata_best` und
`deu` aus `tessdata_fast` (alle Apache License 2.0) lesen beim Einzelbuch alte
Schriften als Rückfallstufe. Die Modelle werden beim Bauen mit fester Prüfsumme
geladen und nicht im Repository gespeichert.
