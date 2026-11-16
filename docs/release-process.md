# Veröffentlichung geprüfter Versionen

Nach jeder fertigen, geprüften App-Version den Quellstand auf GitHub pushen und
einen versionierten Download anbieten. Die Veröffentlichung ist ein Meilenstein,
kein Ersatz für die noch offenen Review-Arbeiten.

## Vorbereitung

1. Versionsname/-code erhöhen, Übergabe und Umsetzungsstand aktualisieren.
2. JVM-Tests, Lint, Build und relevante Gerätetests ausführen. Bildschirm für UI-Tests
   entsperren. Backend-Vertragstests bei Vertragsänderungen ausführen.
3. Vor Aktualisierung echter Geräte privat sichern; keine Datensicherung veröffentlichen.
4. Änderungen und neue Dateien auf persönliche Daten, Zugangswerte und lokale Pfade
   prüfen. Keine Schlüssel, Logs, Bücherlisten oder Kameraaufnahmen einchecken.
5. Einen geprüften Commit erstellen, normal pushen; fremde Änderungen nicht überschreiben.

## Öffentliche Artefakte

Im Verzeichnis `android` den Zugang explizit leeren, auch wenn er lokal gesetzt ist:

```sh
MOMOX_API_TOKEN= ./gradlew assembleDebug
```

Anschließend den leeren Token im generierten BuildConfig und im entpackten APK
prüfen. Ein lokal konfigurierter Zugang ist in einer APK extrahierbar. Kein privates
APK hochladen. Bis zur Produktionssignierung ausschließlich als **Testversion**
kennzeichnen; derselbe lokale Testschlüssel ermöglicht Updates, andere Schlüssel
nicht. Vor Signierungswechsel Bücherliste exportieren. Keystore privat sichern.

Aus genau dem veröffentlichten Commit ein Archiv erzeugen, zum Beispiel:

```sh
git archive --format=zip --prefix=Buecher-Radar-0.12.0/ \
  --output=/tmp/Buecher-Radar-0.12.0-Claude.zip HEAD
```

Das ZIP enthält vollständige Quellen, Gradle-Wrapper, Tests, GPL-Lizenz, `CLAUDE.md`
und Übergabe; keine Git-Historie, lokalen Einstellungen oder Buildverzeichnisse.
APK und ZIP erhalten SHA256-Prüfsummen. Alle drei Dateien an dasselbe GitHub Release
anhängen. Versionsnamen im Beispiel bei späteren Releases ersetzen.

Release-Text nennt Änderungen, tatsächliche Tests, Einschränkungen und ausdrücklich
den fehlenden vorkonfigurierten Momox-Zugang der öffentlichen APK. Bis zur
Produktionsreife als Vorabversion veröffentlichen. GitHub Actions und Download-Assets
prüfen; Links zu APK, Claude-Quellen und Release am Ende der Arbeit bereitstellen.

Die öffentliche APK braucht für Verwaltung und Suche keinen Momox-Zugang. Derzeit
wird ein berechtigter Zugang für Momox beim lokalen Build konfiguriert. Keine
funktionsfähige öffentliche Momox-Anbindung behaupten, solange diese fehlt.
