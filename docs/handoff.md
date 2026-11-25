# Übergabe: 0.15.0

## Auftrag und Stand

Alle Verbesserungsvorschläge des ausführlichen Reviews schrittweise umsetzen.
Der Gesamtauftrag ist **nicht abgeschlossen**. Der aktuelle überprüfbare Stand
und die verbleibende Reihenfolge stehen in `implementation-status.md`.
`recognition-review.md` ist ein historischer Befundstand von 0.10.6, kein aktuelles
Verzeichnis ausschließlich offener Fehler.

0.12.0 ergänzt dauerhaft gespeicherte Regal-Funde: pausieren, zurückstellen,
korrigieren, ergänzen und nach App-Neustart fortsetzen. Fehler eines Fundes stoppen
die anderen nicht. Bestätigungen verwenden stabile IDs, auch für Bücher ohne ISBN.
Die Datei liegt in `noBackupFilesDir`; Export/Import betrifft weiterhin die Bücherliste.

Enthalten sind auch die vorherigen Review-Arbeiten: Room-Schema 5 mit stabilen IDs,
Migration 4 → 5, persistente Momox-Aufträge, Angebotserhalt bei Fehlern, Verlauf,
ausgabenspezifische Katalogdaten, Scanner-Lebenszykluskorrekturen, Einstellungen,
Sicherung und die Scroll-Absturzkorrektur nach Barcode-Scans.

0.13.0 ergänzt ein gemeinsames Suchbudget, Hinweise auf ausgefallene Quellen und
sichtbare Metadatenkonflikte. ISBN-Treffer mit widersprüchlichem Titel/Autor werden
nicht ungefragt hinzugefügt. Vollständiger Bereichsabgleich: `review-checklist.md`.

Vorheriger Prüfstand 0.13.0: 38 JVM-Tests, zwölf isolierte Gerätetests, fünf
Backend-Vertragstests; Build und Lint erfolgreich.

0.14.0: Bildregionen vor der Regal-OCR trennen, begrenzt entzerren und in kleinen
Rastern lesen. Benchmark/Downloadskript und ursprüngliche Baseline stehen unter
`tools/recognition/`. Öffentliche Fotos werden separat geladen, nicht mit der App
ausgeliefert. Weitere ungesehene Fotos und echtes Kameraverhalten weiter prüfen.

Geräteprüfung 0.14.0: alle 13 regulären Tests bestanden, einschließlich Kamera
öffnen, analysieren, schließen und erneut öffnen. Das Gerät hat Version 0.14.0.
Die Kameraoperationen laufen ausdrücklich auf dem Android-Hauptthread; beim
Schließen werden nur die eigenen CameraX-Anwendungsfälle freigegeben.
41 JVM-Tests, Android-Build und Lint ebenfalls erfolgreich.
Der Bildbenchmark umfasst neun Varianten von drei Fotos; er ersetzt keinen
Langlauf- oder allgemeinen Erkennungsnachweis.

0.15.0: überarbeitete Oberfläche (kompakte Momox-Zeile, Filterchips, Scan-Knopf,
Detailmenü, Querformat), Rückgängig für Entfernen und Verkauft (auch für mehrere
schnelle Aktionen), einzelne Regal-Funde verwerfen, Enter startet die Suche.
Erkennung: tolerantere ISBN-Lesung (Trenner, Barcode-Klarschriftzeile), Scanner
löst bei leichtem Flackern aus, hochauflösendes Regal-Foto, Buchkanten nach
Zeilenanteil (eigene Regalfotos: 8/32 statt 2/32 sauber gelesene Rücken bei 1920 px),
Kandidatenauswahl, wenn nur ein Autor erkannt wird. Katalog: Pause nach HTTP 429,
optionaler eigener Google-Books-Schlüssel nur für private Builds. Zwei Runden
Codex-Gesamtprüfung; Befunde behoben. Details und Grenzen in `implementation-status.md`
und `tools/recognition/README.md`. Regalerkennung bleibt deutlich verbesserungsbedürftig.

Im aktuellen Arbeitsstand stehen die Suchaktionen unter dem Eingabefeld. Beim
Tippen zeigt die Liste sofort den ersten Treffer und die Trefferzahl. Der Stand
einer laufenden Momox-Prüfung bleibt beim Scrollen sichtbar.

Die Messung des Coververgleichs ergab keinen brauchbaren Schwellenwert. Der
Bildvergleich wurde entfernt. Katalogtreffer behalten ihre Textreihenfolge.
`coverEvidenceAllowed` ist für eine spätere Coverprüfung vorgesehen und wird
im Scanner derzeit nicht aufgerufen.

## Nächste konkrete Arbeit

1. Die noch offenen Review-Bereiche mit Codebelegen und Abnahmekriterien abgleichen.
2. Erkennungsqualität messbar machen und räumliche Rücken-/Covererkennung verbessern.
3. Feldbelege am bestätigten Buch speichern und die Budgetverteilung zwischen Quellen messen.
4. UI-Verantwortlichkeiten aufteilen und Großschrift, TalkBack, Prozessende testen.
5. Verkaufsvorbereitung/teilbare Liste, Produktionssignierung und Abhängigkeiten bearbeiten.

Ein kleiner öffentlicher Bildtestsatz mit lokaler Baseline ist vorhanden. Für
allgemeine Erkennungsquoten fehlen weitere unabhängige, beschriftete Aufnahmen und
End-to-End-Katalogtests. Keine Ergebnisse erfinden oder den kleinen Testsatz als
repräsentativ darstellen. Ohne
Momox-Vereinbarung keine Verfügbarkeit einer offiziellen Schnittstelle versprechen.

## Lokale und öffentliche Builds

VersionName `0.15.0`, versionCode `27`, applicationId `de.buchradar.app`.
Die öffentliche Test-APK ist debug-signiert und ohne vorkonfigurierten Momox-Zugang.
Ein lokal eingerichteter Build kann andere Zugangskonfiguration enthalten; niemals
seine APK ungeprüft veröffentlichen. Quellcode und Lizenz sind identisch.
Eine stabile Produktionssignierung ist noch offen. Schlüssel niemals veröffentlichen.

Ein früher veröffentlichter Zugangswert ist aus aktuellem Code entfernt, aber noch
in der Git-Historie vorhanden. Historienbereinigung/Rotation erfordern Koordination;
kein unangekündigtes Force-Push. Das Quellcode-ZIP enthält keine Git-Historie.

## Weiterarbeit mit Claude

Repository klonen oder das Release-Archiv `Buecher-Radar-0.15.0-Claude.zip` entpacken.
Claude Code im Projektordner starten; `CLAUDE.md` ist der Einstieg.
Für eine Unterhaltung ohne Repositoryzugriff das ZIP bereitstellen, sofern die
verwendete Oberfläche Archive unterstützt. Verfügbare Werkzeuge und Android-SDK
müssen in der jeweiligen Umgebung separat eingerichtet sein.

Geeigneter Anschlussauftrag:

> Lies CLAUDE.md und die Übergabe. Prüfe den tatsächlichen Codezustand, arbeite die
> offenen Review-Punkte in der dokumentierten Reihenfolge weiter ab und ergänze
> aussagekräftige Regressionstests. Bewahre lokale Nutzerdaten. Aktualisiere den
> Umsetzungsstand und veröffentliche fertige geprüfte Versionen mit APK und Quellen.
