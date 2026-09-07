package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import org.bukkit.entity.Player;

/** Awards each victim’s transferable border bonus at most once per round. */
final class KillBonusService {
    private final LevelBorderSettings settings;
    private final RoundPlayerTracker roundPlayers;
    private final PlayerBorderDataService playerBorderDataService;
    private final RoundScoreTracker roundScores;
    private final PersonalBorderGeometry geometry;
    private final PersonalBorderService personalBorders;

    KillBonusService(
            LevelBorderSettings settings,
            RoundPlayerTracker roundPlayers,
            PlayerBorderDataService playerBorderDataService,
            RoundScoreTracker roundScores,
            PersonalBorderGeometry geometry,
            PersonalBorderService personalBorders
    ) {
        this.settings = settings;
        this.roundPlayers = roundPlayers;
        this.playerBorderDataService = playerBorderDataService;
        this.roundScores = roundScores;
        this.geometry = geometry;
        this.personalBorders = personalBorders;
    }

    double applyPlayerKillBonus(Player killer, Player killed) {
        if (settings.usesCurrentLevelMode() || !settings.highestKillBonusEnabled()) {
            return 0.0D;
        }
        if (!roundPlayers.claimKillBonus(killed)) {
            return 0.0D;
        }

        double previousBorderSize = currentBorderSize(killer);
        PlayerBorderData killedData = playerBorderDataService.updateMaxReachedLevel(killed, playerBorderDataService.getOrCreate(killed));
        int bonusLevels = Math.max(0, killedData.maxReachedLevel());
        if (settings.highestKillBonusInheritsVictimBonus()) {
            bonusLevels = playerBorderDataService.addLevels(bonusLevels, killedData.killBonusLevels());
        }
        if (bonusLevels <= 0) {
            return 0.0D;
        }

        PlayerBorderData killerData = playerBorderDataService.updateMaxReachedLevel(killer, playerBorderDataService.getOrCreate(killer));
        int newKillBonusLevels = playerBorderDataService.addLevels(killerData.killBonusLevels(), bonusLevels);
        if (newKillBonusLevels == killerData.killBonusLevels()) {
            return 0.0D;
        }

        killerData = killerData.withKillBonusLevels(newKillBonusLevels);
        playerBorderDataService.save(killerData);

        int currentLevel = Math.max(0, killer.getLevel());
        double newBorderSize = geometry.borderSize(killerData, currentLevel);
        personalBorders.apply(killer, killerData, currentLevel, BorderNotification.PLAYER_KILL);
        return Math.max(0.0D, (newBorderSize - previousBorderSize) / 2.0D);
    }

    private double currentBorderSize(Player player) {
        return roundScores.score(player).borderSize();
    }

}
