# LevelBorderPvP

## Version target

- Plugin version: `1.0.0`
- WorldBorderAPI: `26.2.0.0:dev`
- Minecraft/Paper API version: `26.2`
- Paper API: `26.2.build.+` (derived from WorldBorderAPI)
- Java toolchain: `25` (derived from WorldBorderAPI)

WorldBorderAPI must also be installed as a server plugin. This plugin only compiles against the API and declares `depend: [WorldBorderAPI]`.

## Was ist LevelBorderPvP?

LevelBorderPvP macht aus XP-Leveln echten Spielraum.

Vor dem Start sitzen alle Spieler in einer kleinen Lobby-Border am Spawn. Sobald der Admin `/levelborder start [seconds]` ausführt, fällt diese Grenze weg: Alle können sich während des Countdowns frei auf der Map verteilen. Erst wenn der Countdown endet, wird für jeden Spieler die aktuelle Position als persönliches Border-Zentrum gespeichert.

Danach zählt jedes Level. Wer XP sammelt, erweitert sein Gebiet. Wer PvP gewinnt, kann optional zusätzlichen Raum erobern. Das Ergebnis ist ein klares Survival- oder PvP-Format mit sichtbarem Fortschritt, echten Entscheidungen und sofort verständlichem Druck.

## Warum ausprobieren?

- **Besserer Rundenstart:** Spieler werden erst festgesetzt, nachdem sie sich verteilt haben.
- **Lobby ohne Extra-System:** Vor dem Start hält eine kleine WorldBorderAPI-Border alle am Spawn.
- **Persönliche Borders statt globalem Chaos:** Jeder Spieler sieht seine eigene Grenze.
- **Sofort verständliche Progression:** Level 0 startet klein, jedes XP-Level schafft mehr Platz.
- **PvP mit echtem Einsatz:** Kills können die erreichten Level des Gegners als Border-Bonus übertragen.
- **Serverfreundlich steuerbar:** Start-Countdown, Lobby-Radius, Level-Modus, Sprache und Obergrenzen sind konfigurierbar.

## Rundenablauf

1. Server startet: Spieler sind im Lobby-Modus am Welt-Spawn eingeschlossen.
2. Admin führt `/levelborder start [seconds]` aus.
3. Während des Countdowns werden die persönlichen Borders entfernt.
4. Nach Ablauf wird die aktuelle Position jedes Online-Spielers als Center gespeichert.
5. Ab dann wächst die persönliche Border mit XP-Leveln und optionalen PvP-Boni.

Das Plugin setzt keine eigenen Permissions. Wer den Startbefehl nutzen darf, kann sauber über LuckPerms oder das vorhandene Server-Setup geregelt werden.

## Gameplay

Standardmäßig nutzt LevelBorderPvP den Modus `highest`: Die Border wächst mit dem höchsten XP-Level, das ein Spieler je erreicht hat, und schrumpft nicht wieder durch Tod oder XP-Verlust.

Die Standardformel lautet:

```text
size = 3.0 + highestLevel * 2.0
```

Das bedeutet:

```text
level 0 = 3x3
level 1 = 5x5
level 2 = 7x7
```

WorldBorderAPI und Paper behandeln die Border-Größe als Durchmesser. Deshalb entspricht `growth-per-level-blocks: 2.0` einem zusätzlichen Block Radius pro Level.

## Spielmodi

### Highest

```yaml
level-mode: highest
```

Der empfohlene Standardmodus. Spieler behalten ihren höchsten Fortschritt, auch wenn sie sterben oder XP verlieren. Das macht LevelBorderPvP planbarer und weniger frustrierend.

Optional kann PvP stärker belohnt werden:

```yaml
highest-kill-bonus-enabled: true
highest-kill-bonus-inherits-victim-bonus: false
```

Wenn aktiviert, erhält der Killer die höchste erreichte Stufe des Opfers als separaten Bonus. Mit `highest-kill-bonus-inherits-victim-bonus: true` wird zusätzlich der bereits gespeicherte Kill-Bonus des Opfers übernommen.

### Current

```yaml
level-mode: current
```

Die Border folgt direkt dem aktuellen XP-Level. Sie wächst und schrumpft also mit dem momentanen Levelstand. Dieser Modus ist härter und eignet sich für Server, die mehr Druck durch XP-Verlust wollen.

## Befehle

```text
/levelborder start [seconds]
/levelborder stop
/levelborder reset
```

- `start` startet global die Runde. Während des Countdowns sind Spieler frei, danach werden ihre aktuellen Positionen als Center gesetzt.
- `stop` entfernt die persönliche Border für den ausführenden Spieler und stellt die globale World Border wieder her.
- `reset` setzt Mittelpunkt, höchstes Level und Kill-Bonus des ausführenden Spielers auf den aktuellen Zustand zurück.

## Konfiguration

Beim ersten Start wird `src/main/resources/config.yml` nach `plugins/LevelBorderPvP/config.yml` kopiert.

Die wichtigsten Einstellungen:

```yaml
initial-size-blocks: 3.0
growth-per-level-blocks: 2.0
level-mode: highest
lobby-radius-blocks: 8.0
max-size-blocks: 0.0
border-transition-milliseconds: 1200
start-countdown-seconds: 10
max-start-countdown-seconds: 3600
```

`lobby-radius-blocks: 8.0` bedeutet: vor dem Start bleiben Spieler in einem Radius von 8 Blöcken um den Spawn.

`max-size-blocks: 0.0` bedeutet: keine Obergrenze. Setze hier einen Wert, wenn die Border nie größer als ein bestimmter Durchmesser werden soll.

## Sprachen

Die Standardsprache ist Deutsch:

```yaml
language: de
```

Mitgeliefert werden:

```text
de.yml
en.yml
ru.yml
```

Die Dateien werden beim Start nach `plugins/LevelBorderPvP/lang/` kopiert und können dort angepasst werden.

## Technische Notizen

- Die echte Server-World-Border wird nicht verändert.
- Spieler-Daten werden in `plugins/LevelBorderPvP/players.yml` gespeichert.
- Der persönliche Border-Mittelpunkt wird beim Ende des Startcountdowns aus der aktuellen Spielerposition gesetzt.
- Während des Startcountdowns wird die individuelle Border auf die globale World Border zurückgesetzt.
- Bei schrumpfenden Borders werden Spieler, die außerhalb der Zielgröße stehen, knapp innerhalb der neuen Border platziert.
- Die individuelle Border wird bei Join, Respawn und Weltwechsel erneut passend zum aktuellen Rundenstatus angewendet.
