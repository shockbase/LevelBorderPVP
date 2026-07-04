# LevelBorderPvP

Paper plugin for individual per-player borders via WorldBorderAPI.

## Version target

- Plugin version: `1.0.0`
- WorldBorderAPI: `26.2.0.0:dev`
- Minecraft/Paper API version: `26.2`
- Paper API: `26.2.build.+` (derived from WorldBorderAPI)
- Java toolchain: `25` (derived from WorldBorderAPI)

WorldBorderAPI must also be installed as a server plugin. This plugin only compiles against the API and declares `depend: [WorldBorderAPI]`.

## Behavior

- On the player's first handled spawn/join, the plugin stores the block the player is standing on.
- The border center is stored in `plugins/LevelBorderPvP/players.yml` and is never recalculated afterward.
- The real server world border is not modified.
- The WorldBorderAPI border size can use either the highest XP level the player has ever reached or the player's current XP level.
- Level-based size changes are animated over `border-transition-milliseconds` by default.
- Optionally, in `level-mode: highest`, player kills can add the victim's highest reached level as a separate bonus.
- Players can use `/levelborder start [seconds]`, `/levelborder stop`, and `/levelborder reset` to control their personal border.
- By default `level-mode: highest` is used and the formula is `size = 3.0 + highestLevel * 2.0`, so:
  - level 0 = 3x3
  - level 1 = 5x5
  - level 2 = 7x7

WorldBorderAPI/Paper use the border size as diameter. That is why one extra block of radius per level needs `growth-per-level-blocks: 2.0`. If you want only one extra block of total width per level, set it to `1.0`.

## Level modes

```yaml
level-mode: highest
```

Uses the highest XP level the player has ever reached. The border grows, but does not shrink when the player loses XP or dies.

Optional kill bonus for this mode:

```yaml
highest-kill-bonus-enabled: true
highest-kill-bonus-inherits-victim-bonus: false
```

When enabled, killing a player adds the victim's highest reached level to the killer's stored kill bonus. With `highest-kill-bonus-inherits-victim-bonus: true`, the victim's stored kill bonus is added too. The killer's own `max-reached-level` stays separate, so later personal highests still increase the border normally.

```yaml
level-mode: current
```

Uses the player's current XP level. The border grows and shrinks directly with the current level.

## Build

Open the folder in IntelliJ IDEA as a Gradle project, then run:

```bash
gradle build
```

The plugin jar is created in:

```text
build/libs/LevelBorderPvP-1.0.0.jar
```

Install both jars on the server:

```text
plugins/worldborderapiplugin-26.2.0.0.jar
plugins/LevelBorderPvP-1.0.0.jar
```

## Configuration

`src/main/resources/config.yml` is copied to `plugins/LevelBorderPvP/config.yml` on first start.

Message language defaults to German:

```yaml
language: de
```

Bundled language files are copied to `plugins/LevelBorderPvP/lang/`: `de.yml`, `en.yml`, and `ru.yml`.

Commands:

```text
/levelborder start [seconds]  Starts the personal border. Without seconds, start-countdown-seconds is used.
/levelborder stop             Stops the personal border and restores the global world border for the player.
/levelborder reset            Resets the stored center, highest level, and kill bonus to the player's current state.
```

```yaml
border-transition-milliseconds: 1200
```

Animates level-based border size changes over 1.2 seconds. Set to `0` to disable the transition. When the border shrinks, players standing outside the target size are moved just inside it.

```yaml
auto-start-on-join: true
start-countdown-seconds: 10
max-start-countdown-seconds: 3600
```

Set `auto-start-on-join: false` if players should have no personal border until they run `/levelborder start`.
