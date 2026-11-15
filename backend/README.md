# BuchRadar Backend

FastAPI-Schnittstellenentwurf. Aktuell liefert der Provider bewusst `UNKNOWN`; es findet **keine** Momox-Abfrage statt.

Vor produktivem Betrieb ergänzen:
- Authentifizierung pro App-Installation (z. B. kurzlebige Tokens)
- HTTPS/TLS und Secret-Management
- persistente Datenbank und Änderungsverlauf
- Rate-Limits, Logging ohne unnötige personenbezogene Daten
- geprüften Momox-Provider mit klarer Fehlerklassifikation
- täglicher Scheduler serverseitig (nicht abhängig vom Android-Gerät)
