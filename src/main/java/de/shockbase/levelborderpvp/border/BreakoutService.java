package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns breakout grace periods and delayed disqualification. */
final class BreakoutService {
    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final Messages messages;
    private final RoundSession session;
    private final RoundPlayerTracker roundPlayers;
    private final BorderNotifier notifier;
    private final PersonalBorderGeometry geometry;
    private final PlayerStateService playerStates;
    private final RoundEndService endRules;
    private static final long DISQUALIFICATION_DEATH_DELAY_TICKS = 5L * 20L;
    private final Map<UUID, BukkitTask> breakoutTasks = new HashMap<>();
    BreakoutService(
            Plugin plugin,
            LevelBorderSettings settings,
            Messages messages,
            RoundSession session,
            RoundPlayerTracker roundPlayers,
            BorderNotifier notifier,
            PersonalBorderGeometry geometry,
            PlayerStateService playerStates,
            RoundEndService endRules
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.session = session;
        this.roundPlayers = roundPlayers;
        this.notifier = notifier;
        this.geometry = geometry;
        this.playerStates = playerStates;
        this.endRules = endRules;
    }

    void handlePotentialBreakout(Player player, Location location) {
        if (!geometry.isOutsideCurrentPersonalBorder(player, location)) {
            cancelBreakoutTask(player);
            return;
        }

        startBreakoutCountdown(player);
    }

    private void startBreakoutCountdown(Player player) {
        UUID playerId = player.getUniqueId();
        if (breakoutTasks.containsKey(playerId)) {
            return;
        }

        int graceSeconds = settings.breakoutGraceSeconds();
        if (graceSeconds <= 0) {
            disqualifyForBreakout(player);
            return;
        }

        BukkitRunnable countdown = new BukkitRunnable() {
            private int remainingSeconds = graceSeconds;

            @Override
            public void run() {
                if (!player.isOnline() || !geometry.isOutsideCurrentPersonalBorder(player, player.getLocation())) {
                    cancelBreakoutTask(player);
                    return;
                }

                if (remainingSeconds <= 0) {
                    breakoutTasks.remove(playerId);
                    disqualifyForBreakout(player);
                    cancel();
                    return;
                }

                notifier.showBreakoutWarning(player, remainingSeconds);
                remainingSeconds--;
            }
        };

        breakoutTasks.put(playerId, session.track(countdown.runTaskTimer(plugin, 0L, 20L)));
    }

    private void disqualifyForBreakout(Player player) {
        cancelBreakoutTask(player);
        if (!isActive(player)) {
            return;
        }

        roundPlayers.recordDeath(player);
        roundPlayers.markSpectator(player);
        playerStates.applySpectator(player, BorderNotification.NONE);
        notifier.showDisqualified(player);
        plugin.getServer().broadcastMessage(messages.text(
                "service.player-disqualified",
                Messages.placeholder("player", player.getName())
        ));

        UUID playerId = player.getUniqueId();
        session.later(() -> finishDisqualification(playerId), DISQUALIFICATION_DEATH_DELAY_TICKS);
    }

    private void finishDisqualification(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && roundPlayers.isSpectator(player)) {
            Location location = player.getLocation();
            if (location.getWorld() != null) {
                location.getWorld().strikeLightningEffect(location);
            }
            notifier.playDisqualificationLightning(player);
            if (!player.isDead() && player.getHealth() > 0.0D) {
                player.setHealth(0.0D);
            }
        }

        endRules.checkRoundEndAfterActivePlayerRemoval("service.end-reason-all-disqualified");
    }

    void cancelBreakoutTask(Player player) {
        BukkitTask task = breakoutTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    void cancelAllBreakoutTasks() {
        for (BukkitTask task : breakoutTasks.values()) {
            task.cancel();
        }
        breakoutTasks.clear();
    }

    private boolean isActive(Player player) { return roundPlayers.isActive(session.state(), player); }
}
