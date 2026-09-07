package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import de.shockbase.levelborderpvp.integration.LuckPermsRoleService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Applies player roles and borders across joins, worlds and round phases. */
final class PlayerStateService {
    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final RoundSession session;
    private final RoundPlayerTracker roundPlayers;
    private final PlayerBorderDataService playerBorderDataService;
    private final PersonalBorderService personalBorders;
    private final LobbyService lobbyService;
    private final LuckPermsRoleService luckPermsRoleService;
    private final BorderRenderer borderRenderer;
    private final Set<UUID> pendingExperienceDisplaySyncs = new HashSet<>();
    PlayerStateService(
            Plugin plugin,
            LevelBorderSettings settings,
            RoundSession session,
            RoundPlayerTracker roundPlayers,
            PlayerBorderDataService playerBorderDataService,
            PersonalBorderService personalBorders,
            LobbyService lobbyService,
            LuckPermsRoleService luckPermsRoleService,
            BorderRenderer borderRenderer
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.session = session;
        this.roundPlayers = roundPlayers;
        this.playerBorderDataService = playerBorderDataService;
        this.personalBorders = personalBorders;
        this.lobbyService = lobbyService;
        this.luckPermsRoleService = luckPermsRoleService;
        this.borderRenderer = borderRenderer;
    }

    void applyNextTick(Player player, BorderNotification notification) {
        session.later(() -> applyCurrentState(player, notification), 1L);
    }

    void applyLater(Player player, BorderNotification notification, long delayTicks) {
        session.later(() -> applyCurrentState(player, notification), delayTicks);
    }

    void handleWorldChange(Player player) {
        session.later(() -> applyWorldChangeState(player), 1L);
    }

    void syncExperienceDisplayLater(Player player) {
        UUID playerId = player.getUniqueId();
        if (!isActive(player) || !pendingExperienceDisplaySyncs.add(playerId)) {
            return;
        }

        session.later(() -> {
            pendingExperienceDisplaySyncs.remove(playerId);
            if (player.isOnline() && isActive(player)) {
                player.sendExperienceChange(player.getExp(), player.getLevel());
            }
        }, 1L);
    }

    void reapplyOnlinePlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            applyNextTick(player, BorderNotification.NONE);
        }
    }

    private void applyCurrentState(Player player, BorderNotification notification) {
        if (!player.isOnline()) return;
        if (session.state() == RoundState.IDLE) {
            luckPermsRoleService.clear(player);
            borderRenderer.resetToGlobal(player);
            return;
        }

        if (session.state() == RoundState.LOBBY) {
            luckPermsRoleService.clear(player);
            borderRenderer.applyLobbyBorder(player);
            return;
        }

        if (session.state() == RoundState.COUNTDOWN) {
            if (!lobbyService.isCandidate(player)) {
                enterSpectator(player, notification);
                return;
            }
            luckPermsRoleService.clear(player);
            lobbyService.applyCountdownBorder(player);
            return;
        }

        if (roundPlayers.isSpectator(player)) {
            applySpectator(player, notification);
            return;
        }

        if (!roundPlayers.isRoundPlayer(player)) {
            enterSpectator(player, notification);
            return;
        }

        luckPermsRoleService.markActive(player);
        personalBorders.apply(player, notification);
    }

    private void applyWorldChangeState(Player player) {
        if (!player.isOnline()) return;
        if (roundPlayers.isSpectator(player)) {
            applySpectator(player, BorderNotification.NONE);
            return;
        }
        if (!isActive(player)) {
            return;
        }

        luckPermsRoleService.markActive(player);
        personalBorders.apply(player, BorderNotification.NONE);
    }

    void applySpectator(Player player, BorderNotification notification) {
        borderRenderer.applySpectator(player, notification);
        luckPermsRoleService.markSpectator(player);
    }

    void enterSpectator(Player player, BorderNotification notification) {
        roundPlayers.markSpectator(player);
        applySpectator(player, notification);
    }

    void preparePlayerForRoundStart(Player player) {
        if (settings.resetXpOnStart()) {
            player.setExp(0.0F);
            player.setLevel(0);
            player.setTotalExperience(0);
        }

        if (settings.clearInventoryOnStart()) {
            player.getInventory().clear();
        }
    }

    void activatePlayerFromCurrentPosition(Player player, BorderNotification notification) {
        roundPlayers.activate(player);
        luckPermsRoleService.markActive(player);
        PlayerBorderData data = playerBorderDataService.createInitial(player, Math.max(0, player.getLevel()));
        playerBorderDataService.save(data);
        personalBorders.apply(player, data, Math.max(0, player.getLevel()), notification);
    }

    void clearPendingExperienceSyncs() { pendingExperienceDisplaySyncs.clear(); }
    private boolean isActive(Player player) { return roundPlayers.isActive(session.state(), player); }
}
