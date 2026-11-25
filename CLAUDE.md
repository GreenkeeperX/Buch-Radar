# Bücher-Radar: Einstieg für Claude

Lies zuerst `AGENTS.md`, `docs/handoff.md` und `docs/implementation-status.md`.
Dieses Verzeichnis ist der vollständige gemeinsame Quellstand, kein separater Fork
für einen bestimmten Assistenten. Die APK allein reicht nicht zur Weiterentwicklung.

## Ziel

Viele gebrauchte Bücher zuverlässig identifizieren, lokal verwalten und wiederholt
auf Momox-Ankauf prüfen. Gute Lesbarkeit und einfache Bedienung sind zentral.
Ausgabeidentität hat Vorrang vor schnellen oder scheinbar sicheren Treffern.

## Build und Tests

JDK 17, Android SDK 35; `android/local.properties` enthält den lokalen `sdk.dir`.
Keinen privaten Rechnerpfad in versionierte Dateien schreiben.

```sh
cd android
MOMOX_API_TOKEN= GOOGLE_BOOKS_API_KEY= ./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDeviceTestAndroidTest
```

Ablauf für Arbeit am echten Gerät: Skill `.claude/skills/buchradar-geraetetest`.
Gerätetests laufen als eigene App `de.buchradar.app.devicetest` (Build-Typ
`deviceTest`), weil der Test-Runner die getestete App danach deinstalliert. Niemals
Gerätetests gegen `de.buchradar.app` ausführen, niemals `connectedDebugAndroidTest`
wieder einführen, kein `pm clear`/`uninstall` auf dem echten Gerät. Vor jeder Arbeit
am echten Gerät zuerst eine private Sicherung der Bücherliste anlegen.
Gerätetests brauchen ein entsperrtes Android-Gerät/Emulator. Sie verwenden isolierte
Testdatenbanken und temporäre Dateien; niemals die echte Bücherliste zurücksetzen.
Backend-Vertragstests: Anforderungen aus `backend/requirements.txt` in einer
virtuellen Umgebung installieren, dann im Ordner `backend`:
`python -m unittest discover -s tests -v`.

## Orientierung

- `android/app/src/main/java/de/buchradar/app/MainActivity.kt`: Hauptansicht,
  Suche, Auswahl, Kamera-/Regalablauf; Aufteilung bleibt eine offene Aufgabe.
- `BookSessionViewModel.kt`: Zustand über Activity-Neustarts.
- `ShelfReviewController.kt`, `data/AtomicShelfReviewStore.kt`: fortsetzbare
  Regal-Nachbearbeitung, unabhängig von der Bücherdatenbank.
- `data/`: Room, Migrationen, Warteschlange, Angebote und Export/Import.
- `MomoxCheckCoordinator.kt`, `MomoxPeriodicWorker.kt`: gemeinsamer Prüfablauf.
- `network/`: konkrete Katalogausgaben, Quellenzugriff, Momox.
- `scanner/`: CameraX, lokale ML-Kit-Erkennung und Stabilitätsregeln.
- `src/test` und `src/androidTest` unter `android/app`: Regressionstests.
- `backend/`: unangebundener Demoentwurf, keine echte Momox-Implementierung.

## Unverzichtbare Regeln

- ISBN-10/13 normalisieren; verschiedene Ausgaben nicht nach bloßem Titel vereinen.
- Coroutine-Abbruch weiterreichen; verspätete Antworten nicht über neuere Änderungen schreiben.
- Fehlgeschlagene Prüfungen dürfen den letzten gültigen Preis nicht löschen.
- Nur UI-Coroutines mit Compose-FrameClock dürfen Listenanimationen ausführen.
- Kamera-Bilder bleiben lokal und kurzlebig. Regal-Fortsetzung speichert keine Fotos.
- Keine Qualitätsbehauptungen ohne Messung an Referenzfällen. Katalogtreffer
  behalten die Reihenfolge der Textsuche; einzige Ausnahme: eine Cover-Bestätigung
  rückt eine Ausgabe vor andere Ausgaben desselben Werks.
- GPL-3.0-only erhalten. Keine Zugangswerte, privaten Bilder, Listen, Gerätekennungen
  oder lokalen Pfade veröffentlichen; auch APK-Inhalte prüfen.
  `tools/check-secrets.sh` läuft in CI und als Commit-Hook
  (`git config core.hooksPath tools/githooks`, einmal je Klon); Funde nie umgehen.

Arbeite die offenen Review-Aufgaben weiter ab. Ein grüner Build erledigt weder den
gesamten Review noch den Nachweis guter Regal-/Covererkennung. Fertige geprüfte
Versionen gemäß `docs/release-process.md` veröffentlichen.
