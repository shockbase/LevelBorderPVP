package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import de.shockbase.levelborderpvp.integration.AdvancementSnapshotService;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

/** Handles XP progression and advancement bonuses. */
final class PlayerProgressionService {
    private final LevelBorderSettings settings;
    private final RoundSession session;
    private final RoundPlayerTracker roundPlayers;
    private final PlayerBorderDataService playerBorderDataService;
    private final PersonalBorderService personalBorders;
    private final RoundEndService endRules;
    private final AdvancementSnapshotService advancementSnapshotService;

    PlayerProgressionService(
            LevelBorderSettings settings,
            RoundSession session,
            RoundPlayerTracker roundPlayers,
            PlayerBorderDataService playerBorderDataService,
            PersonalBorderService personalBorders,
            RoundEndService endRules,
            AdvancementSnapshotService advancementSnapshotService
    ) {
        this.settings = settings;
        this.session = session;
        this.roundPlayers = roundPlayers;
        this.playerBorderDataService = playerBorderDataService;
        this.personalBorders = personalBorders;
        this.endRules = endRules;
        this.advancementSnapshotService = advancementSnapshotService;
    }

    void handleLevelChange(Player player, int newLevel) {
        if (!isActive(player)) {
            return;
        }

        PlayerBorderData data = playerBorderDataService.getOrCreate(player);
        int normalizedLevel = Math.max(0, newLevel);
        boolean reachedNewHighestLevel = normalizedLevel > data.maxReachedLevel();

        if (reachedNewHighestLevel) {
            data = data.withMaxReachedLevel(normalizedLevel);
            playerBorderDataService.save(data);
        }

        if (settings.usesCurrentLevelMode()) {
            personalBorders.apply(player, data, normalizedLevel, BorderNotification.LEVEL_CHANGED);
        } else if (reachedNewHighestLevel) {
            personalBorders.apply(player, data, normalizedLevel, BorderNotification.LEVEL_UP);
        }

        int reachedLevel = settings.usesCurrentLevelMode()
                ? normalizedLevel
                : Math.max(data.maxReachedLevel(), normalizedLevel);
        endRules.checkTargetLevel(player, reachedLevel);
    }


    void handleAdvancementDone(Player player, Advancement advancement) {
        if (!settings.advancementBonusEnabled()
                || advancement == null
                || advancementSnapshotService.isRestoring(player)
                || !isActive(player)
                || !advancementSnapshotService.isManagedAdvancement(advancement)
                || !roundPlayers.claimAdvancementBonus(player, advancement.getKey().toString())) {
            return;
        }

        int bonusLevels = settings.advancementBonusLevels();
        if (bonusLevels <= 0) {
            return;
        }

        PlayerBorderData data = playerBorderDataService.updateMaxReachedLevel(player, playerBorderDataService.getOrCreate(player));
        int newBonusLevels = playerBorderDataService.addLevels(data.killBonusLevels(), bonusLevels);
        if (newBonusLevels == data.killBonusLevels()) {
            return;
        }

        data = data.withKillBonusLevels(newBonusLevels);
        playerBorderDataService.save(data);
        personalBorders.apply(player, data, Math.max(0, player.getLevel()), BorderNotification.ADVANCEMENT_BONUS);
    }



    private boolean isActive(Player player) { return roundPlayers.isActive(session.state(), player); }
}
