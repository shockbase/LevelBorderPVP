package de.shockbase.levelborderpvp.border;

import com.github.yannicklamprecht.worldborder.api.WorldBorderApi;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderRepository;
import de.shockbase.levelborderpvp.i18n.Messages;
import de.shockbase.levelborderpvp.integration.LuckPermsRoleService;
import de.shockbase.levelborderpvp.integration.PlayerRollbackService;
import org.bukkit.Location;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Stable entry point for commands and listeners; delegates to focused round services. */
public final class BorderService {
    private final RoundCoordinator round;

    public record StartResult(boolean started, int eligiblePlayers, int requiredPlayers, int countdownSeconds, String failureKey) {
        public StartResult(boolean started, int eligiblePlayers, int requiredPlayers, int countdownSeconds) {
            this(started, eligiblePlayers, requiredPlayers, countdownSeconds, "command.not-enough-players");
        }
    }

    public BorderService(
            Plugin plugin,
            WorldBorderApi worldBorderApi,
            PlayerBorderRepository playerBorderRepository,
            LevelBorderSettings settings,
            BorderSizeCalculator sizeCalculator,
            BorderNotifier notifier,
            Messages messages,
            LuckPermsRoleService luckPermsRoleService,
            PlayerRollbackService rollbackService
    ) {
        this.round = new RoundCoordinator(plugin, worldBorderApi, playerBorderRepository, settings,
                sizeCalculator, notifier, messages, luckPermsRoleService, rollbackService);
    }

    public void handlePlayerJoin(Player player) {
        round.connections.handlePlayerJoin(player);
    }

    public void handlePlayerQuit(Player player) {
        round.connections.handlePlayerQuit(player);
    }

    public void applyNextTick(Player player, BorderNotification notification) {
        round.playerStates.applyNextTick(player, notification);
    }

    public void applyLater(Player player, BorderNotification notification, long delayTicks) {
        round.playerStates.applyLater(player, notification, delayTicks);
    }

    public void handleWorldChange(Player player) {
        round.playerStates.handleWorldChange(player);
    }

    public void syncExperienceDisplayLater(Player player) {
        round.playerStates.syncExperienceDisplayLater(player);
    }

    public void reapplyOnlinePlayers() {
        round.playerStates.reapplyOnlinePlayers();
    }

    public void refreshRuntimeSettings() {
        round.refreshRuntimeSettings();
    }

    public boolean isSpectator(Player player) {
        return round.isSpectator(player);
    }

    public boolean isActiveRoundPlayer(Player player) {
        return round.isActiveRoundPlayer(player);
    }

    public boolean shouldApplyPortalRules(Player player) {
        return round.portalRules.shouldApplyPortalRules(player);
    }

    public boolean shouldApplyDimensionPvpRules(Player player) {
        return round.portalRules.shouldApplyDimensionPvpRules(player);
    }

    public boolean isInsidePersonalBorder(Player player, Location location) {
        return round.geometry.isInsidePersonalBorder(player, location);
    }

    public Location resolveSafeRespawnLocation(
            Player player,
            Location vanillaRespawnLocation,
            boolean usesPlayerRespawnBlock,
            boolean missingRespawnBlock
    ) {
        return round.respawns.resolveSafeRespawnLocation(player, vanillaRespawnLocation, usesPlayerRespawnBlock, missingRespawnBlock);
    }

    public void handlePotentialBreakout(Player player, Location location) {
        round.breakouts.handlePotentialBreakout(player, location);
    }

    public boolean rememberFirstOverworldPortal(Player player, Location portalLocation) {
        return round.portalRules.rememberFirstOverworldPortal(player, portalLocation);
    }

    public Location resolveOverworldPortalReturn(Player player, Location targetLocation) {
        return round.portalRules.resolveOverworldPortalReturn(player, targetLocation);
    }

    public int limitPortalRadiusInsidePersonalBorder(Player player, Location center, int requestedRadius) {
        return round.portalRules.limitPortalRadiusInsidePersonalBorder(player, center, requestedRadius);
    }

    public void showPortalBlocked(Player player) {
        round.portalRules.showPortalBlocked(player);
    }

    public void showPortalReturnRedirected(Player player) {
        round.portalRules.showPortalReturnRedirected(player);
    }

    public void showPortalMissing(Player player) {
        round.portalRules.showPortalMissing(player);
    }

    public void handleLevelChange(Player player, int newLevel) {
        round.progression.handleLevelChange(player, newLevel);
    }

    public void handlePlayerKill(Player killer, Player killed) {
        round.combat.handlePlayerKill(killer, killed);
    }

    public void handlePlayerDeath(Player player, Player killer) {
        round.combat.handlePlayerDeath(player, killer);
    }

    public void handleAdvancementDone(Player player, Advancement advancement) {
        round.progression.handleAdvancementDone(player, advancement);
    }

    public StartResult start(int countdownSeconds) {
        return round.start(countdownSeconds);
    }

    public void lobby() {
        round.lobby();
    }

    public void stop() {
        round.stop();
    }

    public PlayerRollbackService.RollbackResult rollbackRoundChanges(String requestedProvider) {
        return round.rollbackRoundChanges(requestedProvider);
    }

    public void shutdown() {
        round.shutdown();
    }

}
