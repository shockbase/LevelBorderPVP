# Runden-Regressionen

Automatisiert: `./gradlew test` prüft Snapshot-Schreibfehler, Rejoin während
einer Runde, Task-Abbruch über Rundenwechsel, Zeitberechnung, Grid-Fehler,
geordnete Hintergrundspeicherung und räumliche Border-Abfragen.

Ergänzend auf einem Paper-Testserver mit WorldBorderAPI prüfen:

1. `timed-score`, 60 Minuten: nach einigen Minuten eine reine Anzeigeoption
   ändern. Restzeit und tatsächliches Rundenende behalten den ursprünglichen
   Startzeitpunkt. Eine kürzere Gesamtdauer berücksichtigt verstrichene Zeit.
2. Advancement-Bonus einschalten. Mit vorhandenem Fortschritt starten, während
   der Reconnect-Frist aus- und einloggen. Der Rundenfortschritt bleibt erhalten;
   erst beim Rundenende wird der vorherige Fortschritt wiederhergestellt.
3. Spieler disqualifizieren und innerhalb von fünf Sekunden eine neue Runde
   starten. Der alte verzögerte Blitz/Tod darf die neue Runde nicht betreffen.
4. Grid-Start über unsicherem Gelände bzw. mit einem Plugin, das Teleports
   blockiert: Start wird abgebrochen, keine Spieler werden am Lobbyspawn aktiv.
   Inventar und XP werden bei diesem Abbruch nicht zurückgesetzt.
5. Mehrere Level ändern, danach den Server regulär stoppen und neu starten.
   `players.yml` enthält den letzten Stand. Während des Spiels werden Änderungen
   einmal pro Sekunde gebündelt; ein harter Prozessabbruch kann noch nicht
   geschriebene Änderungen verlieren.
6. Partikel an getrennten und überlappenden Borders, in verschiedenen Welten und
   an sehr großen Borders prüfen. Maximal 384 Partikel pro Zuschauer und Durchlauf.

Ein Live-Lasttest ist für belastbare TPS-/Latenzaussagen weiterhin erforderlich.
