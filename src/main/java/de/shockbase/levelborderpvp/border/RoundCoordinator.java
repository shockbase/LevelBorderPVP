package de.shockbase.levelborderpvp.border;

import com.github.yannicklamprecht.worldborder.api.WorldBorderApi;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.StartPlacementMode;
import de.shockbase.levelborderpvp.data.PlayerBorderRepository;
import de.shockbase.levelborderpvp.i18n.Messages;
import de.shockbase.levelborderpvp.integration.AdvancementSnapshotService;
import de.shockbase.levelborderpvp.integration.LuckPermsRoleService;
import de.shockbase.levelborderpvp.integration.PlayerRollbackService;
import de.shockbase.levelborderpvp.starter.StarterProvisionService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** Wires round services and coordinates lifecycle transitions; rules live in dedicated services. */
final class RoundCoordinator {
    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final Messages messages;
    private final LuckPermsRoleService luckPermsRoleService;
    private final PlayerRollbackService rollbackService;
    private final BorderRenderer borderRenderer;
    private final OtherPlayerBorderRenderer otherPlayerBorderRenderer;
    private final StarterProvisionService starterProvisionService;
    private final AdvancementSnapshotService advancementSnapshotService;
    private final StartPlacementService startPlacement;
    private final RoundSession session;
    private final RoundPlayerTracker roundPlayers = new RoundPlayerTracker();

    private final StartCountdownService countdown;

    final PortalRuleService portalRules;
    final RespawnService respawns;
    final PlayerStateService playerStates;
    final PersonalBorderGeometry geometry;
    final PlayerProgressionService progression;
    final PlayerCombatService combat;
    final PlayerConnectionService connections;
    final BreakoutService breakouts;
    private final DisconnectService disconnects;
    private final LobbyService lobbyService;
    private final RoundResultService results;
    private final RoundEndService endRules;
    private final RoundHudService hud;
    RoundCoordinator(
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
        this.plugin = plugin;
        this.session = new RoundSession(plugin);
        this.countdown = new StartCountdownService(plugin, session);
        this.settings = settings;
        this.messages = messages;
        this.luckPermsRoleService = luckPermsRoleService;
        this.rollbackService = rollbackService;
        this.borderRenderer = new BorderRenderer(worldBorderApi, playerBorderRepository, settings, sizeCalculator, notifier);
        PlayerBorderDataService playerBorderDataService = new PlayerBorderDataService(playerBorderRepository, settings, sizeCalculator);
        SafeLocationService safeLocations = new SafeLocationService(playerBorderDataService, sizeCalculator);
        this.startPlacement = new StartPlacementService(settings, borderRenderer, safeLocations);
        this.otherPlayerBorderRenderer = new OtherPlayerBorderRenderer(
                plugin,
                settings,
                playerBorderDataService,
                sizeCalculator,
                roundPlayers
        );
        this.starterProvisionService = new StarterProvisionService(settings);
        this.advancementSnapshotService = new AdvancementSnapshotService(plugin, settings, messages);
        RoundScoreTracker roundScores = new RoundScoreTracker(settings, sizeCalculator, playerBorderDataService, roundPlayers);
        this.lobbyService = new LobbyService(plugin, settings, borderRenderer);
        this.results = new RoundResultService(plugin, messages, notifier, roundPlayers, roundScores, this::finishRound);
        this.endRules = new RoundEndService(plugin, settings, session, roundPlayers, roundScores, results);
        PersonalBorderService personalBorders = new PersonalBorderService(settings, session, roundPlayers, playerBorderDataService, sizeCalculator, endRules, borderRenderer);
        this.geometry = new PersonalBorderGeometry(settings, roundPlayers, session, playerBorderDataService, sizeCalculator);
        this.portalRules = new PortalRuleService(settings, messages, roundPlayers, session, playerBorderDataService, geometry);
        this.respawns = new RespawnService(settings, roundPlayers, session, playerBorderDataService, geometry, safeLocations);
        this.playerStates = new PlayerStateService(plugin, settings, session, roundPlayers, playerBorderDataService, personalBorders, lobbyService, luckPermsRoleService, borderRenderer);
        this.hud = new RoundHudService(messages, plugin, settings, session, roundPlayers, roundScores, notifier, lobbyService);
        this.breakouts = new BreakoutService(plugin, settings, messages, session, roundPlayers, notifier, geometry, playerStates, endRules);
        this.disconnects = new DisconnectService(plugin, settings, messages, session, roundPlayers, endRules);
        this.progression = new PlayerProgressionService(settings, session, roundPlayers, playerBorderDataService, personalBorders, endRules, advancementSnapshotService);
        KillBonusService killBonuses = new KillBonusService(settings, roundPlayers, playerBorderDataService, roundScores, geometry, personalBorders);
        this.combat = new PlayerCombatService(settings, session, roundPlayers, notifier, killBonuses, playerStates, endRules, breakouts);
        this.connections = new PlayerConnectionService(playerStates, disconnects, breakouts, hud, advancementSnapshotService);
    }

    public void refreshRuntimeSettings() {
        otherPlayerBorderRenderer.refresh(session.state() == RoundState.ACTIVE);
        playerStates.reapplyOnlinePlayers();
        if (session.state() != RoundState.ACTIVE) {
            return;
        }

        endRules.cancelRoundEndTask();
        breakouts.cancelAllBreakoutTasks();
        disconnects.refreshPendingDisconnects();
        if (session.state() != RoundState.ACTIVE) {
            return;
        }
        endRules.scheduleRoundEnd();
        hud.refreshRound();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isActive(player)) {
                continue;
            }

            endRules.checkPlayerTargets(player);
            breakouts.handlePotentialBreakout(player, player.getLocation());
        }

        endRules.checkEliminationWinner();
    }

    public BorderService.StartResult start(int countdownSeconds) {
        List<Player> selectedStartPlayers = lobbyService.findStartCandidates();
        int minimumStartPlayers = settings.minimumStartPlayers();
        if (selectedStartPlayers.size() < minimumStartPlayers) {
            return new BorderService.StartResult(false, selectedStartPlayers.size(), minimumStartPlayers, 0);
        }

        markRoundEndedIfActive();
        session.reset();
        playerStates.clearPendingExperienceSyncs();
        advancementSnapshotService.restoreOnlinePlayers();
        starterProvisionService.cleanupPlacedBlocks();
        countdown.cancel();
        endRules.cancelRoundEndTask();
        breakouts.cancelAllBreakoutTasks();
        disconnects.cancelAllPendingDisconnects();
        otherPlayerBorderRenderer.stop();

        int boundedCountdownSeconds = Math.max(0, Math.min(countdownSeconds, settings.maxStartCountdownSeconds()));
        session.transition(RoundState.COUNTDOWN);
        roundPlayers.clearRound();
        lobbyService.select(selectedStartPlayers);

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            luckPermsRoleService.clear(player);
            if (lobbyService.isCandidate(player)) {
                lobbyService.applyCountdownBorder(player);
            } else {
                playerStates.enterSpectator(player, BorderNotification.SPECTATOR);
            }
        }

        if (boundedCountdownSeconds <= 0) {
            boolean activated = activateRound();
            return new BorderService.StartResult(activated, selectedStartPlayers.size(), minimumStartPlayers, 0, "service.start-aborted");
        }

        hud.startCountdown(boundedCountdownSeconds);

        if (settings.startPlacementMode() != StartPlacementMode.GRID) {
            hud.showSpreadOutToStartCandidates(boundedCountdownSeconds);
        } else {
            hud.showRoundRulesToStartCandidates(boundedCountdownSeconds);
        }

        countdown.start(boundedCountdownSeconds, hud::broadcastCountdown, this::activateRound);
        return new BorderService.StartResult(true, selectedStartPlayers.size(), minimumStartPlayers, boundedCountdownSeconds);
    }

    public void lobby() {
        enterLobby(true);
    }

    public void stop() {
        if (session.state() == RoundState.ACTIVE && settings.rollbackOnRoundEnd()) {
            rollbackService.markRoundEnded();
            results.announceAutomaticRollback(rollbackService.rollbackConfiguredProvider());
        }
        enterIdle();
    }

    public PlayerRollbackService.RollbackResult rollbackRoundChanges(String requestedProvider) {
        return rollbackService.rollback(requestedProvider);
    }

    public void shutdown() {
        cleanupRoundResources();
    }

    private boolean activateRound() {
        List<Player> activeStartPlayers = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (lobbyService.isCandidate(player)) {
                activeStartPlayers.add(player);
            }
        }
        if (activeStartPlayers.size() < settings.minimumStartPlayers()
                || !startPlacement.placeStartPlayers(activeStartPlayers)
                || !advancementSnapshotService.beginRound(activeStartPlayers)) {
            enterLobby(true);
            plugin.getServer().broadcastMessage(messages.text("service.start-aborted"));
            return false;
        }
        session.transition(RoundState.ACTIVE);
        roundPlayers.clearPlayerStates();
        rollbackService.beginRound(activeStartPlayers);
        hud.startRound();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (session.state() != RoundState.ACTIVE) {
                break;
            }
            if (!lobbyService.isCandidate(player)) {
                playerStates.enterSpectator(player, BorderNotification.SPECTATOR);
                continue;
            }
            playerStates.preparePlayerForRoundStart(player);
            starterProvisionService.provide(player);
            playerStates.activatePlayerFromCurrentPosition(player, BorderNotification.JOIN);
        }

        lobbyService.clearCandidates();

        if (session.state() == RoundState.ACTIVE) {
            otherPlayerBorderRenderer.start();
            endRules.scheduleRoundEnd();
            endRules.checkRoundEndAfterActivePlayerRemoval("service.end-reason-no-active-players");
        }
        return true;
    }

    private void finishRound() {
        rollbackService.markRoundEnded();
        if (settings.rollbackOnRoundEnd()) {
            results.announceAutomaticRollback(rollbackService.rollbackConfiguredProvider());
        }
        advancementSnapshotService.restoreOnlinePlayers();
        enterIdle();
    }

    private void enterIdle() {
        cleanupRoundResources();
        session.transition(RoundState.IDLE);
        lobbyService.clearCandidates();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            luckPermsRoleService.clear(player);
            borderRenderer.resetToGlobal(player);
        }

        roundPlayers.clearRound();
    }

    private void enterLobby(boolean teleportPlayers) {
        cleanupRoundResources();
        session.transition(RoundState.LOBBY);
        lobbyService.clearCandidates();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            luckPermsRoleService.clear(player);
            lobbyService.applyLobby(player, teleportPlayers);
        }

        roundPlayers.clearRound();
    }

    private void cleanupRoundResources() {
        session.reset();
        playerStates.clearPendingExperienceSyncs();
        markRoundEndedIfActive();
        otherPlayerBorderRenderer.stop();
        advancementSnapshotService.restoreOnlinePlayers();
        countdown.cancel();
        endRules.cancelRoundEndTask();
        breakouts.cancelAllBreakoutTasks();
        disconnects.cancelAllPendingDisconnects();
        starterProvisionService.cleanupPlacedBlocks();
        hud.hide();
    }

    private void markRoundEndedIfActive() {
        if (session.state() == RoundState.ACTIVE) {
            rollbackService.markRoundEnded();
        }
    }

    private boolean isActive(Player player) { return roundPlayers.isActive(session.state(), player); }
    boolean isSpectator(Player player) { return roundPlayers.isSpectator(player); }
    boolean isActiveRoundPlayer(Player player) { return isActive(player); }
}
