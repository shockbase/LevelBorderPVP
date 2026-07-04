package de.oehme.personallevelborder.listener;

import de.oehme.personallevelborder.border.BorderNotification;
import de.oehme.personallevelborder.border.BorderService;
import de.oehme.personallevelborder.config.LevelBorderSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

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
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (settings.reapplyOnWorldChange()) {
            borderService.applyNextTick(event.getPlayer(), BorderNotification.NONE);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (settings.reapplyOnRespawn()) {
            borderService.applyLater(event.getPlayer(), BorderNotification.RESPAWN, 1L);
        }
    }
}
