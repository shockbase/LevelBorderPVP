package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.RoundEndCondition;
import org.bukkit.entity.Player;

/** Handles kill/death scoring and death elimination. */
final class PlayerCombatService {
    private final LevelBorderSettings settings;
    private final RoundSession session;
    private final RoundPlayerTracker roundPlayers;
    private final BorderNotifier notifier;
    private final KillBonusService killBonuses;
    private final PlayerStateService playerStates;
    private final RoundEndService endRules;
    private final BreakoutService breakouts;

    PlayerCombatService(
            LevelBorderSettings settings,
            RoundSession session,
            RoundPlayerTracker roundPlayers,
            BorderNotifier notifier,
            KillBonusService killBonuses,
            PlayerStateService playerStates,
            RoundEndService endRules,
            BreakoutService breakouts
    ) {
        this.settings = settings;
        this.session = session;
        this.roundPlayers = roundPlayers;
        this.notifier = notifier;
        this.killBonuses = killBonuses;
        this.playerStates = playerStates;
        this.endRules = endRules;
        this.breakouts = breakouts;
    }

    void handlePlayerKill(Player killer, Player killed) {
        if (!isActive(killer)) {
            return;
        }
        if (killer.getUniqueId().equals(killed.getUniqueId())) {
            return;
        }
        if (!isActive(killed)) {
            return;
        }

        roundPlayers.recordKill(killer);

        double radiusGainedBlocks = killBonuses.applyPlayerKillBonus(killer, killed);
        notifier.showPlayerKill(killer, killed.getName(), radiusGainedBlocks);
    }

    void handlePlayerDeath(Player player, Player killer) {
        if (!isActive(player)) {
            return;
        }

        roundPlayers.recordDeath(player);
        if (settings.endCondition() != RoundEndCondition.ELIMINATION) {
            return;
        }

        boolean eliminatedByPlayer = isActivePlayerKill(killer, player);
        breakouts.cancelBreakoutTask(player);
        playerStates.enterSpectator(player, eliminatedByPlayer ? BorderNotification.NONE : BorderNotification.SPECTATOR);
        if (eliminatedByPlayer) {
            notifier.showEliminated(player, killer.getName());
        }
        endRules.checkRoundEndAfterActivePlayerRemoval("service.end-reason-no-active-players");
    }

    private boolean isActivePlayerKill(Player killer, Player killed) {
        return killer != null
                && !killer.getUniqueId().equals(killed.getUniqueId())
                && isActive(killer);
    }

    private boolean isActive(Player player) { return roundPlayers.isActive(session.state(), player); }
}
