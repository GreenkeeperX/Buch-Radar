# Lokaler Erkennungsbenchmark

Der Testsatz besteht zunächst aus drei öffentlichen Fotos: zwei historische
Fontane-Einbände mit Frakturschrift und ein Regal mit zwanzig vollständig sichtbaren
Buchrücken. Herkunft, Urheberangaben, Lizenzlinks und SHA256 stehen in `manifest.json`.
Die Fotos werden nicht in App, Git-Repository oder Quellcode-ZIP eingebettet.

```sh
python3 tools/recognition/fetch.py /tmp/buchradar-public-fixtures
```

Das Skript prüft die Dateien und erzeugt eine Quellenliste im Zielordner. Der
Originalbestand bleibt unverändert; Skalierung und Drehung erfolgen nur im Test.
Abgeleitete Bilder bei Weitergabe gemäß der jeweiligen Bildlizenz kennzeichnen.
Der Programmcode steht separat unter GPL-3.0-only.

## Ausführung auf Android

App und Testpaket aus demselben Commit bauen/installieren
(`./gradlew installDeviceTest installDeviceTestAndroidTest`). Getestet wird die
eigene App `de.buchradar.app.devicetest`, nie die installierte Bücher-Radar-App.
Ein lokaler Ordner `cache/recognition-benchmark` dieser App enthält Manifest und Bilder
(alle Bilddateien aus dem Manifest kopieren, auch die ISBN-Fotos). Keine
echten Datenbanken ersetzen oder die App-Daten löschen. Beispielsweise:

```sh
adb push /tmp/buchradar-public-fixtures /data/local/tmp/buchradar-public-fixtures
adb shell run-as de.buchradar.app.devicetest mkdir -p cache/recognition-benchmark
for f in /tmp/buchradar-public-fixtures/manifest.json /tmp/buchradar-public-fixtures/*.jpg; do
  adb shell run-as de.buchradar.app.devicetest cp "/data/local/tmp/buchradar-public-fixtures/$(basename "$f")" cache/recognition-benchmark/
done
adb shell am instrument -w -e class de.buchradar.app.RecognitionBenchmarkTest -e recognitionBenchmark true de.buchradar.app.devicetest.test/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as de.buchradar.app.devicetest cat cache/recognition-benchmark/report.json > /tmp/recognition-report.json
```

Ohne ausdrückliches Argument wird der Benchmark übersprungen. Er stellt keine
Netzwerkanfragen und verändert die Bücherliste nicht. Die Fotos sind bereits
öffentlich; bei privaten Erweiterungen Berichte und Bilddateien ebenfalls privat halten.

## Messgrößen und Grenzen

- Einzelbuch: erwartete Titel-/Autorwörter gegen tatsächlich erkannte Wörter bei
  0°, 90° und 270°. Die Normalisierung entfernt Großschreibung/Umlautakzente.
- Regal: zwanzig manuell im öffentlichen Foto markierte X-Regionen. Als sauber
  erkannt zählt eine Region nur bei genau einer zugeordneten OCR-Gruppe, keiner
  Vermischung mit einer anderen markierten Region und mindestens 60 % ihrer
  erwarteten Wörter. Die vollständigen Texte bleiben zur Prüfung im lokalen Bericht.
- Getrennt ausgewiesen: zu mehreren Büchern gehörende Gruppen, auf mehrere Gruppen
  verteilte Rücken und Laufzeit. 1280/1920/2560 Pixel werden getrennt gemessen. Die Kamera fordert ab 0.14 eine
  höhere Auflösung an und begrenzt Arbeitsbilder auf 1920 Pixel; die tatsächliche
  Auflösung hängt vom Gerät ab. 2560 Pixel bleiben ein diagnostischer Vergleich.

Das ist ein kleiner Entwicklungsdatensatz, keine repräsentative Erkennungsquote.
Drehungen desselben Fotos sind keine unabhängigen Beispiele. Die Ausgaben haben
keine anhand des Fotos bestätigte ISBN; eine ISBN wird nicht aus dem Werktitel
erfunden. Noch nicht abgedeckt: Live-Bewegung/Auslösung, Barcode, Spiegelungen,
Katalog-Top-5-Ausgabeidentifikation und längere Kamera-/Speichertests. Diese bleiben
in der Review-Checkliste offen. Vor Optimierung eine Baseline sichern; nachher mit
denselben Bildern und Parametern vergleichen und zusätzliche ungesehene Fotos ergänzen.

## Gemessener Entwicklungsstand 0.14

Im selben Regalfoto, mit unveränderten Sollregionen und der beschriebenen strengen
Wort-/Gruppenwertung:

| Lange Bildkante | Bisherige Textgruppen | Getrennte Bildregionen | Zusätzliche Laufzeit |
|---|---:|---:|---:|
| 1280 Pixel | 2/20 | 4/20 | ca. 4 s |
| 1920 Pixel | 3/20 | 8/20 | ca. 11 s |
| 2560 Pixel | 6/20 | 11/20 | ca. 11 s |

Im neuen Durchlauf wurden hier keine Gruppen zwischen den markierten Rücken
vermischt oder auf mehrere Gruppen aufgeteilt. Nicht lesbare Wörter bleiben jedoch
häufig; diese Zahlen sind keine bestätigten Buchidentifikationen oder Momox-Ergebnisse.
Die historischen Fraktureinbände zeigen weiterhin deutliche Lesefehler. Die Messung
belegt eine Verbesserung am Entwicklungsfoto, keinen allgemeinen Qualitätsnachweis.
Laufzeiten schwanken, unter anderem durch Modellstart und Gerätelast.

## ISBN-Fotos (Entwicklungsstand nach 0.14)

Drei öffentliche Rückseiten-/Etikettfotos mit `category: "isbn"` und bestätigten
`expectedIsbns`. Gewertet wird die Vereinigung von `BookIdentity.isbnsInText` über die
OCR-Durchgänge 0°/90°/270°, also dieselbe Extraktion wie in der App
(`isbnHits/isbnExpected`, dazu `isbnsFound` zum Erkennen von Falschtreffern).
Gemessen auf einem Pixel 9a, 3 Bilder × 1280/1920 Pixel × 0°/90°/270°:

| Stand | Gefundene Soll-ISBNs | Falschtreffer |
|---|---:|---:|
| vor der Trennzeichen-/Barcodezeilen-Toleranz | 14 von 18 Fällen | 0 |
| danach | 24/24 ISBNs in allen 18 Fällen | 0 |

Die Fehlschläge vorher: als Punkt gelesener Bindestrich („3-12.5 17154-7“) und
Barcode-Striche in der Klarschriftzeile („9ll783125171541“). Drei Fotos sind kein
Qualitätsnachweis; weitere ungesehene ISBN-Fotos ergänzen.

## Buchkanten nach Zeilenanteil (nach 0.14)

Echte Regalfotos lieferten oft „0 Bildregionen“: Der vorzeichenbehaftete Median der
Farbdifferenz hob sich bei ähnlich dunklen Nachbarrücken oder Hell/Dunkel-Wechseln entlang
eines Rückens auf. Jetzt zählt der Anteil der abgetasteten Zeilen mit einem deutlichen
Farbsprung, und jeder Rücken wird mit etwas Rand gelesen, damit Anfangsbuchstaben nicht
abgeschnitten werden. Gemessen auf einem Pixel 9a (deviceTest-App), Regionen-Durchgang,
sauber gelesene Rücken:

| Satz | 1280 px | 1920 px | 2560 px |
|---|---:|---:|---:|
| Öffentliches Regalfoto, vorher | 4/20 | 7/20 | 12/20 |
| Öffentliches Regalfoto, nachher | 3/20 | 7/20 | 13/20 |
| 4 private Handyfotos (32 Rücken), vorher | 2/32 | 2/32 | 2/32 |
| 4 private Handyfotos (32 Rücken), nachher | 7/32 | 8/32 | 6/32 |

Vorher fand der Regionen-Durchgang in drei der vier privaten Fotos keine einzige Region,
nachher 7–13 je Foto. Im öffentlichen Foto teilt der Kantenumbau einzelne Rücken häufiger
(2–5 statt 0 aufgeteilte Rücken). Private Fotos und ihre Sollwerte bleiben außerhalb des
Repositorys.

## Textspalten und Bruchstücke (nach 0.15)

Kanten, die mitten durch eine senkrechte Textzeile laufen, gelten nicht mehr als Buchgrenze;
breite Regionen werden an Lücken zwischen Textspalten geteilt; Autor oben und Titel unten in
derselben Spalte bleiben ein Rücken; schräge Rücken werden bis zu einer Neigung von etwa 1:4
gesucht. Gemessen wie oben (Pixel 9a, Regionen-Durchgang, sauber gelesene Rücken):

| Satz | 1280 px | 1920 px | 2560 px |
|---|---:|---:|---:|
| Öffentliches Regalfoto, vorher | 3/20 | 7/20 | 13/20 |
| Öffentliches Regalfoto, nachher | 3/20 | 7/20 | 13/20 |
| 4 private Handyfotos (32 Rücken), vorher | 6/32 | 8/32 | 6/32 |
| 4 private Handyfotos (32 Rücken), nachher | 12/32 | 13/32 | 12/32 |

Rohmetriken ohne OCR-Texte: `baseline-0.13.json` und `results-0.14.json`.
Die aktuellen Schnitte sind heuristisch; mehrere Regalreihen, schräg gestapelte
Bücher und ein unabhängiger Testsatz sind weitere Aufgaben.

Die Wahl zwischen gedrehten Lesungen verwendet die von ML Kit gelieferten
[Wortkonfidenzen](https://developers.google.com/android/reference/com/google/mlkit/vision/text/Text.Element).
Das ist keine Wahrscheinlichkeit für die korrekte Buchidentifikation. Für ausreichende
Bilddetails siehe Googles [Hinweise zur Bildqualität](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).
