package de.shockbase.levelborderpvp.border;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RoundPlayerTracker {

    private final Map<UUID, Integer> roundKills = new HashMap<>();
    private final Map<UUID, Integer> roundDeaths = new HashMap<>();
    private final Set<UUID> roundPlayers = new HashSet<>();
    private final Set<UUID> spectatorPlayers = new HashSet<>();
    private final Set<UUID> killBonusClaimedVictims = new HashSet<>();
    private final Set<String> advancementBonusClaims = new HashSet<>();

    void clearRound() {
        clearPlayerStates();
        roundKills.clear();
        roundDeaths.clear();
        killBonusClaimedVictims.clear();
        advancementBonusClaims.clear();
    }

    void clearPlayerStates() {
        roundPlayers.clear();
        spectatorPlayers.clear();
    }

    void activate(Player player) {
        UUID playerId = player.getUniqueId();
        roundPlayers.add(playerId);
        spectatorPlayers.remove(playerId);
    }

    void markSpectator(Player player) {
        markSpectator(player.getUniqueId());
    }

    void markSpectator(UUID playerId) {
        spectatorPlayers.add(playerId);
    }

    boolean isActive(RoundState roundState, Player player) {
        return isActive(roundState, player.getUniqueId());
    }

    boolean isActive(RoundState roundState, UUID playerId) {
        return roundState == RoundState.ACTIVE
                && roundPlayers.contains(playerId)
                && !spectatorPlayers.contains(playerId);
    }

    boolean isSpectator(Player player) {
        return spectatorPlayers.contains(player.getUniqueId());
    }

    boolean isRoundPlayer(Player player) {
        return roundPlayers.contains(player.getUniqueId());
    }

    int activeCount(RoundState roundState) {
        if (roundState != RoundState.ACTIVE) {
            return 0;
        }

        int activePlayers = 0;
        for (UUID playerId : roundPlayers) {
            if (!spectatorPlayers.contains(playerId)) {
                activePlayers++;
            }
        }
        return activePlayers;
    }

    int roundPlayerCount() {
        return roundPlayers.size();
    }

    void recordKill(Player player) {
        roundKills.merge(player.getUniqueId(), 1, Integer::sum);
    }

    void recordDeath(Player player) {
        recordDeath(player.getUniqueId());
    }

    void recordDeath(UUID playerId) {
        roundDeaths.merge(playerId, 1, Integer::sum);
    }

    boolean claimKillBonus(Player player) {
        return killBonusClaimedVictims.add(player.getUniqueId());
    }

    boolean claimAdvancementBonus(Player player, String advancementKey) {
        return advancementBonusClaims.add(player.getUniqueId() + ":" + advancementKey);
    }

    int kills(Player player) {
        return roundKills.getOrDefault(player.getUniqueId(), 0);
    }

    int deaths(Player player) {
        return roundDeaths.getOrDefault(player.getUniqueId(), 0);
    }
}
