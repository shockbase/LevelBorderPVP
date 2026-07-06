package de.shockbase.levelborderpvp.listener;

import de.shockbase.levelborderpvp.border.BorderNotification;
import de.shockbase.levelborderpvp.border.BorderService;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class PlayerBorderListener implements Listener {

    private final LevelBorderSettings settings;
    private final BorderService borderService;

    public PlayerBorderListener(LevelBorderSettings settings, BorderService borderService) {
        this.settings = settings;
        this.borderService = borderService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        borderService.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        borderService.handleAdvancementDone(event.getPlayer(), event.getAdvancement());
    }

    @EventHandler
    public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
        borderService.handleLevelChange(event.getPlayer(), event.getNewLevel());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            borderService.handlePlayerKill(killer, event.getEntity());
        }
        borderService.handlePlayerDeath(event.getEntity(), killer);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (settings.reapplyOnWorldChange()) {
            borderService.handleWorldChange(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (movedHorizontally(event.getFrom(), event.getTo())) {
            borderService.handlePotentialBreakout(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        borderService.handlePotentialBreakout(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (!borderService.shouldApplyPortalRules(player)) {
            return;
        }

        if (isOverworldToSecondaryDimension(event.getFrom(), event.getTo())) {
            borderService.rememberFirstOverworldPortal(player, event.getFrom());
            return;
        }

        if (!isSecondaryDimensionToOverworld(event.getFrom(), event.getTo())) {
            return;
        }

        Location originalTarget = event.getTo();
        Location targetLocation = borderService.resolveOverworldPortalReturn(player, originalTarget);
        if (targetLocation == null) {
            event.setCanCreatePortal(false);
            event.setCancelled(true);
            borderService.showPortalMissing(player);
            return;
        }

        event.setTo(targetLocation);
        event.setSearchRadius(borderService.limitPortalRadiusInsidePersonalBorder(player, targetLocation, event.getSearchRadius()));
        event.setCreationRadius(borderService.limitPortalRadiusInsidePersonalBorder(player, targetLocation, event.getCreationRadius()));
        if (originalTarget == null || !sameBlockLocation(originalTarget, targetLocation)) {
            event.setCanCreatePortal(false);
            borderService.showPortalReturnRedirected(player);
        }
    }

    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !borderService.shouldApplyPortalRules(player)
                || event.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        for (BlockState block : event.getBlocks()) {
            if (!borderService.isInsidePersonalBorder(player, block.getLocation())) {
                event.setCancelled(true);
                borderService.showPortalBlocked(player);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.DEATH) {
            Location safeRespawnLocation = borderService.resolveSafeRespawnLocation(
                    event.getPlayer(),
                    event.getRespawnLocation(),
                    event.isBedSpawn() || event.isAnchorSpawn(),
                    event.isMissingRespawnBlock()
            );
            if (safeRespawnLocation != null) {
                event.setRespawnLocation(safeRespawnLocation);
            }
        }
        if (settings.reapplyOnRespawn()) {
            borderService.applyLater(event.getPlayer(), BorderNotification.RESPAWN, 1L);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (borderService.isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (borderService.isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (borderService.isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (borderService.isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (borderService.isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (borderService.isSpectator(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        if (borderService.isSpectator(event.getPlayer())) {
            event.setAmount(0);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && borderService.isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && borderService.isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && borderService.isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && borderService.isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && borderService.isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (isSpectatorActor(event.getDamager()) || isSpectatorTarget(event.getEntity())) {
            event.setCancelled(true);
            return;
        }

        if (shouldCancelDimensionPvp(event.getDamager(), event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean isSpectatorTarget(Entity entity) {
        return entity instanceof Player player && borderService.isSpectator(player);
    }

    private boolean isSpectatorActor(Entity entity) {
        Player player = playerActor(entity);
        return player != null && borderService.isSpectator(player);
    }

    private boolean shouldCancelDimensionPvp(Entity damager, Entity target) {
        if (!(target instanceof Player targetPlayer)) {
            return false;
        }

        Player actor = playerActor(damager);
        if (actor == null) {
            return false;
        }

        return isActiveInSecondaryDimension(actor) || isActiveInSecondaryDimension(targetPlayer);
    }

    private Player playerActor(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (!(entity instanceof Projectile projectile)) {
            return null;
        }

        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Player player ? player : null;
    }

    private boolean isActiveInSecondaryDimension(Player player) {
        return borderService.shouldApplyDimensionPvpRules(player) && isSecondaryDimension(player.getWorld());
    }

    private boolean isOverworldToSecondaryDimension(Location from, Location to) {
        if (from.getWorld() == null || to == null || to.getWorld() == null) {
            return false;
        }

        World.Environment fromEnvironment = from.getWorld().getEnvironment();
        World.Environment toEnvironment = to.getWorld().getEnvironment();
        return fromEnvironment == World.Environment.NORMAL
                && (toEnvironment == World.Environment.NETHER || toEnvironment == World.Environment.THE_END);
    }

    private boolean isSecondaryDimensionToOverworld(Location from, Location to) {
        if (from.getWorld() == null) {
            return false;
        }

        World.Environment fromEnvironment = from.getWorld().getEnvironment();
        if (fromEnvironment != World.Environment.NETHER && fromEnvironment != World.Environment.THE_END) {
            return false;
        }
        return to == null || (to.getWorld() != null && to.getWorld().getEnvironment() == World.Environment.NORMAL);
    }

    private boolean isSecondaryDimension(World world) {
        if (world == null) {
            return false;
        }

        World.Environment environment = world.getEnvironment();
        return environment == World.Environment.NETHER || environment == World.Environment.THE_END;
    }

    private boolean sameBlockLocation(Location first, Location second) {
        return first != null
                && second != null
                && first.getWorld() != null
                && second.getWorld() != null
                && first.getWorld().getUID().equals(second.getWorld().getUID())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private boolean movedHorizontally(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return true;
        }
        return !first.getWorld().getUID().equals(second.getWorld().getUID())
                || Double.compare(first.getX(), second.getX()) != 0
                || Double.compare(first.getZ(), second.getZ()) != 0;
    }
}
