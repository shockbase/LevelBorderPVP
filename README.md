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

Mit `/levelborder lobby` werden alle Online-Spieler optional zum Worldspawn teleportiert und in einer kleinen Lobby-Border gesperrt. Bei `/levelborder start [seconds]` werden nur Spieler innerhalb dieser Lobby-Border fuer die Runde vorgemerkt. Nach dem Countdown wird ihre aktuelle Position als persoenliches Border-Zentrum gespeichert.

Danach zaehlt jedes Level. XP erweitert die eigene Border, PvP kann optional Bonus-Level uebertragen, und die Runde kann automatisch ueber Zeitwertung, Ziel-Level, Ziel-Border oder Elimination enden.

## Rundenablauf

1. Admin fuehrt `/levelborder lobby` aus.
2. Spieler werden optional zum Worldspawn teleportiert und in der Lobby-Border gehalten.
3. Admin fuehrt `/levelborder start [seconds]` aus.
4. Nur Spieler innerhalb der Lobby-Border werden aktive Spieler; alle anderen werden Zuschauer.
5. Waehrend des Countdowns haben aktive Spieler keine persoenliche Border und koennen sich verteilen.
6. Nach Ablauf werden XP und Inventar der aktiven Startspieler optional zurueckgesetzt.
7. Spaetere Joiner werden fuer die laufende Runde Zuschauer.
8. Getoetete aktive Spieler werden bei aktivem Zuschauermodus ebenfalls Zuschauer und koennen die Runde verfolgen.

Der Zuschauerstatus ist kein echter Minecraft-Spectator. Zuschauer haben keine Border und koennen sich frei bewegen, aber das Plugin blockiert Bauen, Interaktion, Inventare, XP, Item-Handling und Kampf.

## Spielmodi

### Highest

```yaml
level-mode: highest
```

Die Border basiert auf dem hoechsten Level, das ein Spieler je erreicht hat. Das ist der empfohlene Standard.

Optional kann PvP Bonus-Level vergeben:

```yaml
highest-kill-bonus-enabled: true
highest-kill-bonus-inherits-victim-bonus: false
```

### Current

```yaml
level-mode: current
```

Die Border folgt direkt dem aktuellen XP-Level und kann schrumpfen.

## Spielregeln / Verhalten

- PvP-Kills zaehlen weiter fuer Score und Tie-Breaker, solange Killer und Opfer aktive Rundenspieler sind.
- Wenn `highest-kill-bonus-enabled` aktiv ist, kann jeder Spieler nur einmal pro Runde als Bonusquelle dienen. Weitere Kills desselben Opfers geben keinen Border-Bonus, zaehlen aber weiter als Kill.
- Bei `timed-score` ist ein Gleichstand nach allen Tie-Breakern ein geteilter Sieg.

## Spielende

```yaml
end-condition: timed-score
round-duration-minutes: 60
score-tiebreakers:
  - kills
  - highest-level
  - deaths-ascending
win-target-level: 30
win-target-border-size-blocks: 63.0
```

Moegliche Werte fuer `end-condition`:

- `timed-score`: Nach Ablauf gewinnt die groesste aktive Border. Tie-Breaker: Kills, hoechstes Level, wenigste Tode.
- `target-level`: Wer zuerst `win-target-level` erreicht, gewinnt.
- `target-border`: Wer zuerst `win-target-border-size-blocks` erreicht, gewinnt.
- `elimination`: Getoetete aktive Spieler scheiden aus. Der letzte aktive Spieler gewinnt.
- `disabled`: Kein automatisches Spielende.

## Befehle

```text
/levelborder lobby
/levelborder start [seconds]
/levelborder stop
/levelborder config list
/levelborder config get <key>
/levelborder config set <key> <value>
```

- `lobby` bereitet die Lobby vor.
- `start` startet global Countdown und Runde.
- `stop` beendet die laufende Runde global und entfernt alle Plugin-Borders.
- `config` kann auch aus der Server-Konsole genutzt werden und speichert Werte direkt in `config.yml`.
- Alle `/levelborder`-Befehle sind geschuetzt. Mit LuckPerms braucht der Nutzer `levelborderpvp.admin`; ohne LuckPerms duerfen nur OP-Spieler und die Server-Konsole steuern.

Beispiele:

```text
/levelborder config set end-condition elimination
/levelborder config set round-duration-minutes 45
/levelborder config set spectator-mode-enabled true
```

## Admin-Rechte

```yaml
command-permission: levelborderpvp.admin
```

Wenn LuckPerms installiert ist, prueft LevelBorderPvP diese Permission ueber Bukkit/LuckPerms:

```text
lp group admin permission set levelborderpvp.admin true
```

Der Permission-Name kann in `config.yml` geaendert werden. Ohne LuckPerms greift der interne Fallback: Nur OP-Spieler und die Server-Konsole koennen `/levelborder` verwenden.

## LuckPerms

LuckPerms ist optional und wird ohne harte API-Abhaengigkeit ueber Console-Kommandos angebunden.

Fuer Admins:

```text
lp group admin permission set levelborderpvp.admin true
```

Empfohlene Gruppen:

```text
levelborder_active
levelborder_spectator
```

Beispiel-Setup:

```text
lp creategroup levelborder_active
lp creategroup levelborder_spectator
lp group levelborder_spectator permission set essentials.build false
lp group levelborder_spectator permission set essentials.kit false
lp group levelborder_spectator permission set minecraft.command.gamemode false
```

Plugin-Config:

```yaml
luckperms-integration-enabled: false
luckperms-active-group: levelborder_active
luckperms-spectator-group: levelborder_spectator
luckperms-clear-groups-on-round-end: true
luckperms-command-add-active: "lp user {player} parent add {active_group}"
luckperms-command-remove-active: "lp user {player} parent remove {active_group}"
luckperms-command-add-spectator: "lp user {player} parent add {spectator_group}"
luckperms-command-remove-spectator: "lp user {player} parent remove {spectator_group}"
```

Platzhalter: `{player}`, `{uuid}`, `{active_group}`, `{spectator_group}`.

Die Plugin-eigenen Zuschauer-Sperren bleiben aktiv. LuckPerms ist zusaetzlich fuer Serverrechte, Commands und andere Plugins gedacht.

## Wichtige Konfiguration

```yaml
initial-size-blocks: 3.0
growth-per-level-blocks: 2.0
level-mode: highest
lobby-radius-blocks: 8.0
teleport-players-to-lobby-spawn: true
max-size-blocks: 0.0
border-transition-seconds: 1
start-countdown-seconds: 10
max-start-countdown-seconds: 3600
reset-xp-on-start: true
clear-inventory-on-start: true
command-permission: levelborderpvp.admin
spectator-mode-enabled: true
```

`max-size-blocks: 0.0` bedeutet: keine Obergrenze.

## Technische Notizen

- Die echte Server-World-Border wird nicht veraendert.
- Spieler-Daten werden in `plugins/LevelBorderPvP/players.yml` gespeichert.
- Bei schrumpfenden Borders werden Spieler knapp innerhalb der neuen Border platziert.
- Die individuelle Border wird bei Join, Respawn und Weltwechsel passend zum Rundenstatus erneut angewendet.
