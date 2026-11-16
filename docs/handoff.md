# Übergabe: 0.12.0

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

Verifiziert: 32 JVM-Tests, zwölf isolierte Gerätetests, fünf Backend-Vertragstests;
Build und Lint erfolgreich. Gerät mit 0.12.0 aktualisiert.

## Nächste konkrete Arbeit

1. Die noch offenen Review-Bereiche mit Codebelegen und Abnahmekriterien abgleichen.
2. Erkennungsqualität messbar machen und räumliche Rücken-/Covererkennung verbessern.
3. Katalogsuche insgesamt zeitlich begrenzen, Teilfehler und Metadatenkonflikte anzeigen.
4. UI-Verantwortlichkeiten aufteilen und Großschrift, TalkBack, Prozessende testen.
5. Verkaufsvorbereitung/teilbare Liste, Produktionssignierung und Abhängigkeiten bearbeiten.

Für echte Erkennungsquoten fehlt ein beschrifteter, zur Nutzung freigegebener
Referenzdatensatz. Diesen nicht durch ausgedachte Messergebnisse ersetzen. Ohne
Momox-Vereinbarung keine Verfügbarkeit einer offiziellen Schnittstelle versprechen.

## Lokale und öffentliche Builds

VersionName `0.12.0`, versionCode `24`, applicationId `de.buchradar.app`.
Die öffentliche Test-APK ist debug-signiert und ohne vorkonfigurierten Momox-Zugang.
Ein lokal eingerichteter Build kann andere Zugangskonfiguration enthalten; niemals
seine APK ungeprüft veröffentlichen. Quellcode und Lizenz sind identisch.
Eine stabile Produktionssignierung ist noch offen. Schlüssel niemals veröffentlichen.

Ein früher veröffentlichter Zugangswert ist aus aktuellem Code entfernt, aber noch
in der Git-Historie vorhanden. Historienbereinigung/Rotation erfordern Koordination;
kein unangekündigtes Force-Push. Das Quellcode-ZIP enthält keine Git-Historie.

## Weiterarbeit mit Claude

Repository klonen oder das Release-Archiv `Buecher-Radar-0.12.0-Claude.zip` entpacken.
Claude Code im Projektordner starten; `CLAUDE.md` ist der Einstieg.
Für eine Unterhaltung ohne Repositoryzugriff das ZIP bereitstellen, sofern die
verwendete Oberfläche Archive unterstützt. Verfügbare Werkzeuge und Android-SDK
müssen in der jeweiligen Umgebung separat eingerichtet sein.

Geeigneter Anschlussauftrag:

> Lies CLAUDE.md und die Übergabe. Prüfe den tatsächlichen Codezustand, arbeite die
> offenen Review-Punkte in der dokumentierten Reihenfolge weiter ab und ergänze
> aussagekräftige Regressionstests. Bewahre lokale Nutzerdaten. Aktualisiere den
> Umsetzungsstand und veröffentliche fertige geprüfte Versionen mit APK und Quellen.
