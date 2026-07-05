package de.shockbase.levelborderpvp.listener;

import de.shockbase.levelborderpvp.border.BorderNotification;
import de.shockbase.levelborderpvp.border.BorderService;
import de.shockbase.levelborderpvp.config.LevelBorderSettings;
import org.bukkit.Location;
import org.bukkit.World;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
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
        borderService.applyNextTick(event.getPlayer(), BorderNotification.JOIN);
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
            borderService.applyNextTick(event.getPlayer(), BorderNotification.NONE);
        }
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

        Location targetLocation = borderService.resolveOverworldPortalReturn(player, event.getTo());
        if (targetLocation == null) {
            event.setCancelled(true);
            return;
        }

        event.setTo(targetLocation);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
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
        }
    }

    private boolean isSpectatorTarget(Entity entity) {
        return entity instanceof Player player && borderService.isSpectator(player);
    }

    private boolean isSpectatorActor(Entity entity) {
        if (entity instanceof Player player) {
            return borderService.isSpectator(player);
        }

        if (!(entity instanceof Projectile projectile)) {
            return false;
        }

        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Player player && borderService.isSpectator(player);
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
}
