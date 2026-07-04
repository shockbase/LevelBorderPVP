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

Vor dem Start sitzen alle Spieler in einer kleinen Lobby-Border am Spawn. Nach `/levelborder start [seconds]` faellt diese Grenze fuer den Countdown weg. Wenn der Countdown endet, wird die aktuelle Position jedes Online-Spielers als persoenliches Border-Zentrum gespeichert.

Danach zaehlt jedes Level. XP erweitert die eigene Border, PvP kann optional Bonus-Level uebertragen, und die Runde kann automatisch ueber Zeitwertung, Ziel-Level, Ziel-Border oder Elimination enden.

## Rundenablauf

1. Server startet: Spieler sind im Lobby-Modus am Welt-Spawn eingeschlossen.
2. Admin fuehrt `/levelborder start [seconds]` aus.
3. Waehrend des Countdowns haben Spieler keine persoenliche Border.
4. Nach Ablauf wird jeder aktuelle Online-Spieler als aktiver Border-Spieler registriert.
5. Spaetere Joiner werden bei aktivem Zuschauermodus zu Zuschauern.
6. Getoetete aktive Spieler werden ebenfalls Zuschauer und koennen die Runde verfolgen.

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
/levelborder start [seconds]
/levelborder stop
/levelborder reset
/levelborder config list
/levelborder config get <key>
/levelborder config set <key> <value>
```

- `start` startet global die Runde.
- `stop` entfernt die persoenliche Border des ausfuehrenden Spielers.
- `reset` setzt Mittelpunkt, hoechstes Level und Kill-Bonus des ausfuehrenden Spielers zurueck.
- `config` kann auch aus der Server-Konsole genutzt werden und speichert Werte direkt in `config.yml`.

Beispiele:

```text
/levelborder config set end-condition elimination
/levelborder config set round-duration-minutes 45
/levelborder config set spectator-mode-enabled true
```

## LuckPerms

LuckPerms ist optional und wird ohne harte API-Abhaengigkeit ueber Console-Kommandos angebunden.

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
max-size-blocks: 0.0
border-transition-milliseconds: 1200
start-countdown-seconds: 10
max-start-countdown-seconds: 3600
spectator-mode-enabled: true
```

`max-size-blocks: 0.0` bedeutet: keine Obergrenze.

## Technische Notizen

- Die echte Server-World-Border wird nicht veraendert.
- Spieler-Daten werden in `plugins/LevelBorderPvP/players.yml` gespeichert.
- Bei schrumpfenden Borders werden Spieler knapp innerhalb der neuen Border platziert.
- Die individuelle Border wird bei Join, Respawn und Weltwechsel passend zum Rundenstatus erneut angewendet.
