# Verantwortlichkeiten nach der Aufteilung

`BorderService` bleibt die öffentliche Schnittstelle für Commands, Dialoge und
Listener. Alle bisherigen öffentlichen Methoden und `StartResult` bleiben
verfügbar. Die Klasse hält keine eigenen Spielerdaten, Timer oder Spielregeln.

| Verantwortlichkeit | Zuständige Klasse |
| --- | --- |
| Aufbau der Services, Start/Stop/Lobby, Aktivierung, Runtime-Refresh und gemeinsames Aufräumen | `RoundCoordinator` |
| Rundenstatus, Rundenzeit, Generation und Abbruch verzögerter Aufgaben | `RoundSession` |
| Start-Countdown mit Fortschritts- und Abschlussmeldung | `StartCountdownService` |
| Auswahl und Aufbewahrung der Startkandidaten, Lobbyposition, Countdown-Border | `LobbyService` |
| Sichere Grid-Verteilung und Überprüfung der Teleports | `StartPlacementService` |
| Block-/Spaltensuche für sichere Positionen | `SafeLocationService` |
| Wahl zwischen Vanilla-Respawn und Ersatzposition in der Border | `RespawnService` |
| Bordergröße, Weltzuordnung und Innen-/Außenprüfung | `PersonalBorderGeometry` |
| Persönliche Border anwenden und neue Größe an Endbedingungen melden | `PersonalBorderService` |
| Borderpakete, Animation und Darstellung | `BorderRenderer` |
| Rollen/Borders je Rundenphase, Weltwechsel, XP-Anzeige und Startinventar/-XP | `PlayerStateService` |
| Join-/Quit-Ablauf zwischen Wiederherstellung, Zustand und Reconnect | `PlayerConnectionService` |
| Reconnect-Fristen und Eliminierung nach Disconnect | `DisconnectService` |
| Ausbruchsfrist, Disqualifikation und verzögerter Tod | `BreakoutService` |
| Dimensionen, Portalgedächtnis, Rückkehrziel, Portalradien und Portalhinweise | `PortalRuleService` |
| Leveländerungen und Advancement-Boni | `PlayerProgressionService` |
| Kill-/Todeswertung und Eliminierung durch Tod | `PlayerCombatService` |
| Einmalige Übertragung des Opferbonus | `KillBonusService` |
| Teilnehmer, Zuschauer, Kill-/Todeszähler und beanspruchte Boni | `RoundPlayerTracker` |
| Scoreberechnung, Ranking und Tie-Breaker | `RoundScoreTracker` |
| Zeitlimit, Ziel-Level, Ziel-Border und Eliminationssieg | `RoundEndService` |
| Gewinner-/Platzierungsanzeige und Rollback-Rückmeldung | `RoundResultService` |
| Countdown-/Regelhinweise und HUD-Statistiken | `RoundHudService` |
| Sidebar-Aufbau und Timerdarstellung | `GameTimerDisplay` |
| Partikel fremder Borders und räumliche Vorauswahl | `OtherPlayerBorderRenderer`, `BorderSpatialIndex` |
| Datenzugriff und Speicherung | `PlayerBorderDataService`, `PlayerBorderRepository`, `AtomicFileStore` |
| Starter, Advancement-Sicherungen, Berechtigungen und Rollback-Provider | Bereits bestehende spezialisierte Integrationsservices |

## Zusammenarbeit

Die Services erhalten konkrete, schmale Abhängigkeiten per Konstruktor. Sie
greifen nicht auf Felder von `BorderService` oder `RoundCoordinator` zurück.
Die einzige Rückmeldung zur Lebenszyklussteuerung ist ein Abschluss-Callback:
`RoundEndService` entscheidet, `RoundResultService` zeigt das Ergebnis und
meldet den Abschluss an `RoundCoordinator`, der die Runde aufräumt.

Lobbykandidaten gehören ausschließlich `LobbyService`; Reconnect-Aufgaben
`DisconnectService`; Ausbruchsaufgaben `BreakoutService`. Die gemeinsame
`RoundSession` verhindert, dass verzögerte Aktionen eine neue Runde treffen.

Der bisherige unerreichbare `ensureRoundPlayer`-Pfad wurde entfernt: Seine
Aufrufer akzeptierten bereits ausschließlich aktive, registrierte Spieler.
Zuschauerwechsel nach Tod brechen den Ausbruchstimer weiterhin explizit ab.

## Prüfung

`./gradlew build` führt die Regressionstests mit aus. Neben den bestehenden
Tests decken direkte Servicetests Portalrückkehr/-radien, Zuschauerregeln,
Zeitneuberechnung, Offline-Teilnehmer bei Eliminationssiegen, Reconnect,
Disqualifikation und die Reihenfolge beim Tod ab. Für das Zusammenspiel mit
einem echten Paper-Server gelten zusätzlich die manuellen Prüfschritte in
`manual-test-round-regressions.md` und `manual-test-dimension-portals.md`.
