package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.integration.AdvancementSnapshotService;
import org.bukkit.entity.Player;

/** Coordinates player connection events with restoration and role application. */
final class PlayerConnectionService {
    private final PlayerStateService playerStates;
    private final DisconnectService disconnects;
    private final BreakoutService breakouts;
    private final RoundHudService hud;
    private final AdvancementSnapshotService advancementSnapshotService;

    PlayerConnectionService(
            PlayerStateService playerStates,
            DisconnectService disconnects,
            BreakoutService breakouts,
            RoundHudService hud,
            AdvancementSnapshotService advancementSnapshotService
    ) {
        this.playerStates = playerStates;
        this.disconnects = disconnects;
        this.breakouts = breakouts;
        this.hud = hud;
        this.advancementSnapshotService = advancementSnapshotService;
    }

    void handlePlayerJoin(Player player) {
        boolean reconnected = disconnects.cancelPending(player);

        advancementSnapshotService.restoreIfPending(player);
        playerStates.applyNextTick(player, BorderNotification.JOIN);
        hud.showCurrent(player);

        if (reconnected) disconnects.announceReconnect(player);
    }

    void handlePlayerQuit(Player player) {
        breakouts.cancelBreakoutTask(player);
        disconnects.handlePlayerQuit(player);
    }
}
