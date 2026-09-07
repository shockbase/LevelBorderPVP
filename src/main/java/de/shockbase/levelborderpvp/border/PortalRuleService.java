package de.shockbase.levelborderpvp.border;

import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import de.shockbase.levelborderpvp.data.PlayerBorderData;
import de.shockbase.levelborderpvp.i18n.Messages;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Owns dimension policy, portal memory, return routing and portal feedback. */
final class PortalRuleService {
    private final LevelBorderSettings settings;
    private final Messages messages;
    private final RoundPlayerTracker roundPlayers;
    private final RoundSession session;
    private final PlayerBorderDataService playerBorderDataService;
    private final PersonalBorderGeometry geometry;

    PortalRuleService(
            LevelBorderSettings settings,
            Messages messages,
            RoundPlayerTracker roundPlayers,
            RoundSession session,
            PlayerBorderDataService playerBorderDataService,
            PersonalBorderGeometry geometry
    ) {
        this.settings = settings;
        this.messages = messages;
        this.roundPlayers = roundPlayers;
        this.session = session;
        this.playerBorderDataService = playerBorderDataService;
        this.geometry = geometry;
    }

    boolean shouldApplyPortalRules(Player player) {
        return settings.dimensionPolicy().usesSafePveDimensionRules() && isActive(player);
    }

    boolean shouldApplyDimensionPvpRules(Player player) {
        return settings.dimensionPolicy().usesSafePveDimensionRules() && isActive(player);
    }

    boolean rememberFirstOverworldPortal(Player player, Location portalLocation) {
        if (portalLocation == null
                || portalLocation.getWorld() == null
                || portalLocation.getWorld().getEnvironment() != World.Environment.NORMAL
                || !shouldApplyPortalRules(player)
                || !geometry.isInsidePersonalBorder(player, portalLocation)) {
            return false;
        }

        return playerBorderDataService.saveFirstOverworldPortal(player, portalLocation);
    }

    Location resolveOverworldPortalReturn(Player player, Location targetLocation) {
        if (targetLocation == null
                || targetLocation.getWorld() == null
                || targetLocation.getWorld().getEnvironment() != World.Environment.NORMAL
                || !shouldApplyPortalRules(player)) {
            return null;
        }

        if (geometry.isInsidePersonalBorder(player, targetLocation)) {
            return targetLocation;
        }

        Location storedPortal = playerBorderDataService.findOverworldPortal(player, targetLocation.getWorld());
        if (storedPortal == null || storedPortal.getWorld() == null) {
            return null;
        }
        if (storedPortal.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return null;
        }
        if (!geometry.isInsidePersonalBorder(player, storedPortal)) {
            return null;
        }

        return storedPortal;
    }

    int limitPortalRadiusInsidePersonalBorder(Player player, Location center, int requestedRadius) {
        if (requestedRadius <= 0 || center == null || center.getWorld() == null || !isActive(player)) {
            return 0;
        }

        PlayerBorderData data = playerBorderDataService.findExisting(player);
        if (data == null || !geometry.isSameWorld(data, center.getWorld())) {
            return 0;
        }

        double borderSize = geometry.borderSize(data, Math.max(0, player.getLevel()));
        double halfSize = Math.max(0.0D, borderSize / 2.0D);
        double maxXRadius = halfSize - Math.abs(center.getX() - data.x());
        double maxZRadius = halfSize - Math.abs(center.getZ() - data.z());
        int allowedRadius = (int) Math.floor(Math.max(0.0D, Math.min(maxXRadius, maxZRadius)));
        return Math.max(0, Math.min(requestedRadius, allowedRadius));
    }

    void showPortalBlocked(Player player) {
        player.sendMessage(messages.text("service.portal-blocked"));
    }

    void showPortalReturnRedirected(Player player) {
        player.sendMessage(messages.text("service.portal-return-redirected"));
    }

    void showPortalMissing(Player player) {
        player.sendMessage(messages.text("service.portal-missing"));
    }

    private boolean isActive(Player player) { return roundPlayers.isActive(session.state(), player); }
}
