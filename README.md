# Bücher-Radar – Android-App

Android-Prototyp zur Erfassung von Büchern per ISBN/EAN und zur späteren Synchronisierung mit einem separaten Prüf-Backend.

## Status und Grenzen

- Dies ist ein früher Android-Prototyp. Das Debug-APK lässt sich lokal bauen; ein Release-Build mit eigener Signierung ist noch nicht eingerichtet.
- Die App kann die gespeicherte Liste nacheinander gegen den derzeit bekannten Momox-App-Endpunkt prüfen. Diese Schnittstelle ist undokumentiert, nicht als öffentliche API zugesichert und kann sich ändern oder Anfragen begrenzen.
- Für echte tägliche Prüfungen wird ein separat betriebener HTTPS-Backend-Dienst benötigt. Die Android-App selbst muss dafür nicht dauerhaft laufen.
- Bücher werden lokal mit Room gespeichert und können bearbeitet oder entfernt werden.
- Beim Hinzufügen sucht die App Titel, Autor und Erscheinungsjahr über Open Library, Google Books und ergänzend die Deutsche Nationalbibliothek. Die Daten werden lokal gespeichert; die Suche braucht eine Internetverbindung und auch die kombinierten Kataloge sind nicht vollständig.
- Gültige ISBN-10/ISBN-13 werden geprüft und mit korrekten Bindestrichen angezeigt.
- Ein gemeinsamer Vollbild-Kamerascanner priorisiert EAN-Barcodes, stabilisiert erkannten Text über mehrere Bilder und kann anschließend automatisch eine Suche starten. Einzelbuch- und Regalmodus lassen sich manuell wählen; eine automatische Szeneneinschätzung und ein experimenteller Cover-Test sind vorhanden.
- Der Live-OCR-Text wird über mehrere Frames stabilisiert, bevor er angezeigt oder übernommen wird. Das ISBN-Metadaten-Lookup nutzt neben der Suche einen exakten ISBN-Fallback.
- Der Kamera-Barcode-Scanner liest EAN-13 und füllt die ISBN in das Eingabefeld. Die Buchdaten werden danach wie bei manueller Eingabe gesucht.
- Die Cover-Erkennung arbeitet mit dem Live-Kamerabild. ML Kit liest Titel und ISBN lokal aus; ein geeigneter Frame wird nur im Arbeitsspeicher gehalten. Erkannte Titel/ISBN suchen passende Ausgaben in Open Library. Eine lokale Bildähnlichkeits-Heuristik sortiert deren Coverbilder; das Foto wird nicht hochgeladen oder gespeichert. Der Prozentwert ist eine Sortierhilfe, keine verlässliche Identitätswahrscheinlichkeit.
- Momox lässt sich pro Buch oder für die ganze Liste nacheinander abfragen. Der Listenlauf zeigt den Fortschritt, wartet 750 ms zwischen ISBNs und speichert Preis/Status samt Prüfzeit lokal. Für stabile, regelmäßige Produktivprüfungen wäre eine von Momox freigegebene Schnittstelle nötig.
- Backend-Synchronisierung ist noch nicht integriert. WorkManager und Retrofit bleiben dafür Grundgerüste.

## Enthalten

- Kotlin + Jetpack Compose Grundgerüst
- Kamera-Barcode-Scanner für EAN-13 mit CameraX und ML Kit
- Cover-/Titel-OCR mit ML Kit Text Recognition und Cover-Vorschau bei Katalogtreffern
- Room-Datenbank mit lokaler Buchliste
- ISBN-Prüfung und standardgerechte Bindestrichformatierung
- Katalogsuche nach ISBN und Titel über Open Library und Google Books mit DNB-Fallback
- Zusammenführung gleicher Ausgaben anhand ihrer ISBN bei getrennter Behandlung unterschiedlicher Ausgaben
- OCR-Titelsuche probiert einzelne erkannte Textzeilen und normalisiert Umlaute/Akzente; Titel- und Autorüberlappung werden zur Sortierung mit Coverähnlichkeit kombiniert.
- Live-OCR plus lokale, heuristische Cover-Bildsortierung für wenige Katalogtreffer
- Momox-Preisabfrage pro Buch oder fortlaufend für die gesamte Liste
- Die Darstellung folgt der Geräteeinstellung oder kann in den Einstellungen fest auf Hell oder Dunkel gesetzt werden.
- Das Einstellungsrad zeigt ein lokales Diagnoseprotokoll und erlaubt dessen Löschung.
- Eine optionale tägliche oder wöchentliche Hintergrundprüfung kann bei Netzwerkverbindung laufen. Ergebnisbenachrichtigungen sind optional und können auf einen Momox-Gesamtankaufswert ab 10 € begrenzt werden.
- WorkManager-Sync-Job als Grundgerüst
- Retrofit-Endpunktdefinition als Backend-Grundgerüst
- Backend-Entwurf mit FastAPI und austauschbarem Momox-Provider
- Gradle-Wrapper für einen reproduzierbaren Android-Build

## Voraussetzungen

- Android Studio (optional für grafische Entwicklung)
- JDK 17
- Android SDK 35
- Android-Gerät mit Kamera oder Emulator

## Öffnen und bauen

1. ZIP entpacken.
2. Android Studio → **Open** → Ordner `android/` auswählen.
3. Gradle-Synchronisierung abwarten.
4. App auf Gerät/Emulator starten.

Alternativ lässt sich das Debug-APK im Terminal bauen (JDK 17 und Android SDK 35):

```bash
cd android
./gradlew assembleDebug
```

Das APK liegt anschließend unter `android/app/build/outputs/apk/debug/app-debug.apk`.
Die SDK-Pfaddatei `android/local.properties` ist maschinenspezifisch und soll nicht eingecheckt werden.

Das Backend-Verzeichnis ist ein separater Python-Dienst und wird nicht automatisch durch die Android-App gestartet.

## Backend lokal starten

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Für einen echten externen Betrieb muss der Dienst auf einem geeigneten Server mit HTTPS, Authentifizierung, Backups und abgesicherter Datenbank betrieben werden. Keine Ports am privaten Homeserver öffnen, nur um die App erreichbar zu machen.

## Momox-Integration

`backend/app/providers/base.py` definiert das Provider-Protokoll. `demo.py` liefert absichtlich nur einen neutralen Demo-Status. Erst nach technischer Prüfung einer zulässigen und stabilen Momox-Schnittstelle darf ein echter Provider ergänzt werden. Fehler, unbekannter Status und „wird nicht angekauft“ müssen getrennte Zustände bleiben.
