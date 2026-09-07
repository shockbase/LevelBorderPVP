package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.DimensionPolicy;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import org.bukkit.entity.Player;

/** Applies personal borders and reports resulting sizes to the end rules. */
final class PersonalBorderService {
    private final LevelBorderSettings settings;
    private final RoundSession session;
    private final RoundPlayerTracker roundPlayers;
    private final PlayerBorderDataService playerBorderDataService;
    private final BorderSizeCalculator sizeCalculator;
    private final RoundEndService endRules;
    private final BorderRenderer borderRenderer;

    PersonalBorderService(
            LevelBorderSettings settings,
            RoundSession session,
            RoundPlayerTracker roundPlayers,
            PlayerBorderDataService playerBorderDataService,
            BorderSizeCalculator sizeCalculator,
            RoundEndService endRules,
            BorderRenderer borderRenderer
    ) {
        this.settings = settings;
        this.session = session;
        this.roundPlayers = roundPlayers;
        this.playerBorderDataService = playerBorderDataService;
        this.sizeCalculator = sizeCalculator;
        this.endRules = endRules;
        this.borderRenderer = borderRenderer;
    }

    void apply(Player player, BorderNotification notification) {
        if (!isActive(player)) {
            return;
        }

        PlayerBorderData data = playerBorderDataService.updateMaxReachedLevel(player, playerBorderDataService.getOrCreate(player));
        apply(player, data, Math.max(0, player.getLevel()), notification);
    }

    void apply(Player player, PlayerBorderData data, int currentLevel, BorderNotification notification) {
        int borderLevel = playerBorderDataService.resolveLevelForBorder(data, currentLevel);
        double size = sizeCalculator.calculate(borderLevel);
        if (allowsPersonalBorder(player)) {
            size = borderRenderer.apply(player, data, borderLevel, notification);
        } else {
            borderRenderer.resetToGlobal(player);
            playerBorderDataService.save(data.withLastAppliedBorderSize(size));
        }
        if (!Double.isNaN(size)) {
            endRules.checkTargetBorder(player, size);
        }
    }

    private boolean allowsPersonalBorder(Player player) {
        DimensionPolicy policy = settings.dimensionPolicy();
        return policy.allowsPersonalBorder(player.getWorld());
    }

    private boolean isActive(Player player) { return roundPlayers.isActive(session.state(), player); }
}
