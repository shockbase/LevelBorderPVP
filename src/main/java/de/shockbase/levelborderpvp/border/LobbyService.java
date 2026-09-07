package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.config.StartPlacementMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owns start candidates, lobby placement and countdown borders. */
final class LobbyService {
    private final Plugin plugin;
    private final LevelBorderSettings settings;
    private final BorderRenderer borderRenderer;
    private final Set<UUID> startCandidateIds = new HashSet<>();
    LobbyService(Plugin plugin, LevelBorderSettings settings, BorderRenderer borderRenderer) {
        this.plugin = plugin;
        this.settings = settings;
        this.borderRenderer = borderRenderer;
    }

    void applyCountdownBorder(Player player) {
        if (settings.startPlacementMode() == StartPlacementMode.GRID) {
            borderRenderer.applyLobbyBorder(player);
            return;
        }

        borderRenderer.resetToGlobal(player);
    }

    private boolean isInsideLobbyBorder(Player player) {
        double lobbyRadius = settings.lobbyRadiusBlocks();
        if (lobbyRadius <= 0.0D) {
            return true;
        }

        Location playerLocation = player.getLocation();
        Location lobbyCenter = player.getWorld().getSpawnLocation();
        return Math.abs(playerLocation.getX() - lobbyCenter.getX()) <= lobbyRadius
                && Math.abs(playerLocation.getZ() - lobbyCenter.getZ()) <= lobbyRadius;
    }

    List<Player> findStartCandidates() {
        List<Player> players = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isInsideLobbyBorder(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private void teleportToWorldSpawn(Player player) {
        Location spawnLocation = player.getWorld().getSpawnLocation();
        player.teleport(spawnLocation);
    }

    void select(List<Player> players) {
        startCandidateIds.clear();
        for (Player player : players) startCandidateIds.add(player.getUniqueId());
    }
    boolean isCandidate(Player player) { return startCandidateIds.contains(player.getUniqueId()); }
    int candidateCount() { return startCandidateIds.size(); }
    void clearCandidates() { startCandidateIds.clear(); }
    void applyLobby(Player player, boolean teleport) {
        if (teleport && settings.teleportPlayersToLobbySpawn()) teleportToWorldSpawn(player);
        borderRenderer.applyLobbyBorder(player);
    }
}
