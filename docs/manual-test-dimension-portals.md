# Manuelle Pruefliste: Dimensionen und Portale

Voraussetzung:

- Paper/WorldBorderAPI-Server mit diesem Plugin starten.
- `dimension-policy: safe-pve`
- Eine Runde ueber `/levelborder lobby` und `/levelborder start 0` starten.

## Aktiver Spieler

1. Aktiver Spieler steht in der Overworld innerhalb seiner Border.
2. Nether-Portal betreten.
3. Erwartung: Wechsel in den Nether klappt, persoenliche Border ist dort nicht sichtbar.

## Zuschauer

1. Spieler ausserhalb der Lobby-Border zum Zuschauer machen oder nach Rundenstart joinen.
2. Portal nutzen oder Welt wechseln.
3. Erwartung: keine aktive Portal-Redirect-Regel; Zuschauer-Sperren bleiben aktiv.

## Nicht-Rundenspieler

1. Kein aktiver Rundenspieler, z. B. vor Start oder nach Rundenende.
2. Portal nutzen.
3. Erwartung: Plugin greift nicht in Portal-Linking ein.

## Nether-Rueckkehr ausserhalb Border

1. Aktiver Spieler betritt Nether aus gueltigem Overworld-Portal.
2. Im Nether weit laufen.
3. Portal zurueck in die Overworld nutzen, sodass Vanilla ausserhalb der persoenlichen Border landen wuerde.
4. Erwartung: Rueckkehr wird zum gespeicherten Overworld-Einstiegsportal umgeleitet.
5. Erwartung: Nachricht `Rueckkehr wurde zu deinem Overworld-Einstiegsportal umgeleitet.`
6. Erwartung: kein neues Overworld-Portal ausserhalb der Border entsteht.

## Fehlendes gespeichertes Portal

1. Aktiver Spieler befindet sich in Nether/End ohne gespeichertes gueltiges Overworld-Portal.
2. Portal zur Overworld nutzen.
3. Erwartung: Portal-Teleport wird blockiert.
4. Erwartung: Nachricht `Kein gueltiges Overworld-Portal gefunden. Rueckkehr blockiert.`

## PvP in Nether und End

1. Zwei aktive Spieler in den Nether oder ins End bringen.
2. Direkten Nahkampfschaden testen.
3. Projektilschaden testen.
4. Erwartung: Schaden wird abgebrochen.
5. In der Overworld denselben PvP-Test wiederholen.
6. Erwartung: Overworld-PvP bleibt unveraendert.

## Build-Pruefung

```text
.\gradlew.bat build
```
