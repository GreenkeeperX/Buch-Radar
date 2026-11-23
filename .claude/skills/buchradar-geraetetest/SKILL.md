---
name: buchradar-geraetetest
description: Bücher-Radar auf einem angeschlossenen Android-Handy testen, ohne die echte Bücherliste zu gefährden. Sicherung, Update-Installation, Gerätetests als deviceTest-App, Erkennungs-Benchmark und Screenshots. Verwenden bei „auf dem Handy testen“, „aufs Gerät installieren“, „Benchmark laufen lassen“ oder „Optik auf dem Gerät prüfen“.
---

# Bücher-Radar auf dem Handy testen

Die echte App `de.buchradar.app` enthält die private Bücherliste. Jeder Schritt schützt sie.

## Niemals

- `de.buchradar.app` deinstallieren, `pm clear` ausführen oder Gerätetests gegen sie laufen lassen.
- `connectedDebugAndroidTest` verwenden. Der Test-Runner deinstalliert die getestete App danach.
- Screenshots, Logs oder Sicherungen veröffentlichen oder committen. Buchtitel aus der privaten Liste nur nennen, wenn der Nutzer sie selbst nennt.
- Auf dem Handy tippen, während der Nutzer es hält oder benutzt. Im Zweifel `dumpsys activity activities` und `dumpsys media.camera` prüfen und nachfragen.

## Ablauf

1. Voraussetzungen: JDK 17, `android/local.properties` mit `sdk.dir`, `adb devices` zeigt ein Gerät, Bildschirm entsperrt (`dumpsys window | grep isKeyguardShowing`).
2. Vor jedem Update eine private Sicherung anlegen (nur Debug-Build, `run-as`):
   `adb exec-out run-as de.buchradar.app tar -cf - databases shared_prefs > <private-folder>/buchradar-<commit>.tar`
3. Lokal prüfen: `cd android && MOMOX_API_TOKEN= ./gradlew testDebugUnitTest lintDebug`
4. Als Update installieren: `./gradlew installDebug`. Nie vorher deinstallieren. Bei Signaturkonflikt abbrechen und berichten.
5. Gerätetests nur als eigene App: `./gradlew connectedDeviceTestAndroidTest` (App-ID `de.buchradar.app.devicetest`).
6. Erkennungs-Benchmark (siehe `tools/recognition/README.md`): Bilder und Manifest kommen in den Cache der deviceTest-App.
   `adb shell run-as de.buchradar.app.devicetest ...`, dann
   `adb shell am instrument -w -e class de.buchradar.app.RecognitionBenchmarkTest -e recognitionBenchmark true de.buchradar.app.devicetest.test/androidx.test.runner.AndroidJUnitRunner`
7. Screenshots: `adb exec-out screencap -p > <private-folder>/x.png` und `uiautomator dump` für Koordinaten. Alles bleibt lokal.
8. Abstürze: `adb logcat -d -s AndroidRuntime:E`. App-Protokoll: `adb logcat -d -s BuchRadar.App:*`.

## Systemeinstellungen zurücksetzen

Jeden Wert vor einer Änderung notieren (`settings get system font_scale`, `user_rotation`, `screen_off_timeout`) und danach wiederherstellen. Abschaltzeit und Helligkeit nicht verändern.

## Bericht

Für jeden Gradle-Lauf die Anzahl der Tests und Fehlerausgaben wörtlich angeben. Dazu das Installationsergebnis, was zu sehen war (nur Layout), Absturzlogs und welche Sicherung angelegt wurde.
