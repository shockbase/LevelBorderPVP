package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.EliminationDisconnectPolicy;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.RoundEndCondition;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns reconnect grace periods and offline elimination. */
final class DisconnectService {
    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final Messages messages;
    private final RoundSession session;
    private final RoundPlayerTracker roundPlayers;
    private final RoundEndService endRules;
    private final Map<UUID, PendingDisconnect> pendingDisconnects = new HashMap<>();
    private record PendingDisconnect(BukkitTask task, String playerName) {}
    DisconnectService(
            Plugin plugin,
            LevelBorderSettings settings,
            Messages messages,
            RoundSession session,
            RoundPlayerTracker roundPlayers,
            RoundEndService endRules
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.session = session;
        this.roundPlayers = roundPlayers;
        this.endRules = endRules;
    }

    void handlePlayerQuit(Player player) {
        if (session.state() != RoundState.ACTIVE
                || settings.endCondition() != RoundEndCondition.ELIMINATION
                || !isActive(player)) {
            return;
        }

        int graceSeconds = settings.eliminationReconnectGraceSeconds();
        if (settings.eliminationDisconnectPolicy() == EliminationDisconnectPolicy.GRACE_PERIOD
                && graceSeconds > 0) {
            scheduleDisconnectElimination(player, graceSeconds);
            return;
        }

        eliminateDisconnectedPlayer(player.getUniqueId(), player.getName());
    }

    private void scheduleDisconnectElimination(Player player, int graceSeconds) {
        UUID playerId = player.getUniqueId();
        PendingDisconnect previous = pendingDisconnects.remove(playerId);
        if (previous != null) {
            previous.task().cancel();
        }

        String playerName = player.getName();
        BukkitTask task = session.later(
                () -> expireDisconnectGracePeriod(playerId),
                graceSeconds * 20L
        );
        pendingDisconnects.put(playerId, new PendingDisconnect(task, playerName));
        plugin.getServer().broadcastMessage(messages.text(
                "service.player-disconnected-grace",
                Messages.placeholder("player", playerName),
                Messages.placeholder("seconds", graceSeconds)
        ));
    }

    private void expireDisconnectGracePeriod(UUID playerId) {
        PendingDisconnect pendingDisconnect = pendingDisconnects.remove(playerId);
        if (pendingDisconnect == null) {
            return;
        }

        eliminateDisconnectedPlayer(playerId, pendingDisconnect.playerName());
    }

    private void eliminateDisconnectedPlayer(UUID playerId, String playerName) {
        if (!roundPlayers.isActive(session.state(), playerId)
                || settings.endCondition() != RoundEndCondition.ELIMINATION) {
            return;
        }

        roundPlayers.recordDeath(playerId);
        roundPlayers.markSpectator(playerId);
        plugin.getServer().broadcastMessage(messages.text(
                "service.player-eliminated-disconnect",
                Messages.placeholder("player", playerName)
        ));
        endRules.checkRoundEndAfterActivePlayerRemoval("service.end-reason-no-active-players");
    }

    void refreshPendingDisconnects() {
        if (settings.endCondition() != RoundEndCondition.ELIMINATION) {
            cancelAllPendingDisconnects();
            return;
        }
        if (settings.eliminationDisconnectPolicy() == EliminationDisconnectPolicy.GRACE_PERIOD
                && settings.eliminationReconnectGraceSeconds() > 0) {
            return;
        }

        for (UUID playerId : List.copyOf(pendingDisconnects.keySet())) {
            PendingDisconnect pendingDisconnect = pendingDisconnects.remove(playerId);
            if (pendingDisconnect == null) {
                continue;
            }
            pendingDisconnect.task().cancel();
            eliminateDisconnectedPlayer(playerId, pendingDisconnect.playerName());
        }
    }

    void cancelAllPendingDisconnects() {
        for (PendingDisconnect pendingDisconnect : pendingDisconnects.values()) {
            pendingDisconnect.task().cancel();
        }
        pendingDisconnects.clear();
    }

    boolean cancelPending(Player player) {
        PendingDisconnect pending = pendingDisconnects.remove(player.getUniqueId());
        if (pending == null) return false;
        pending.task().cancel();
        return true;
    }
    void announceReconnect(Player player) {
        if (!isActive(player)) return;
        plugin.getServer().broadcastMessage(messages.text("service.player-reconnected", Messages.placeholder("player", player.getName())));
        endRules.checkEliminationWinner();
    }
    private boolean isActive(Player player) { return roundPlayers.isActive(session.state(), player); }
}
