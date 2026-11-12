package org.Cry_Legende.sHCookieClicker.game;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Événements autour du clicker : pose d'un clicker par un administrateur, anti-AFK, arrivée et départ des
 * joueurs. Les clics sur le clicker lui-même arrivent en paquets (ClickerInput) : le clicker n'a pas de bloc.
 */
public final class ClickerListener implements Listener {
    private final SHCookieClicker plugin;

    public ClickerListener(SHCookieClicker plugin) {
        this.plugin = plugin;
    }

    // Pose d'un clicker (/shcookieclicker set clicker) : le bloc cliqué en devient le repère
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (event.getHand() == EquipmentSlot.HAND && (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK)
                && plugin.clicks().placing(event.getPlayer(), event.getClickedBlock(), action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
        }
    }

    // Anti-AFK : seulement les changements de bloc (x ou z) dans un monde qui a un clicker
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!plugin.clickers().hasClickers(to.getWorld())) {
            return;
        }
        Session session = plugin.sessions().get(event.getPlayer());
        if (session != null) {
            session.moved(System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.clicks().join(event.getPlayer());
    }

    // Avant SHNetwork (MONITOR), qui lance alors les sauvegardes de départ et attend la nôtre
    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        plugin.display().forget(event.getPlayer());
        plugin.clicks().leave(event.getPlayer(), true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Session session = plugin.sessions().get(event.getPlayer());
        if (session != null) {
            session.moved(System.currentTimeMillis());
        }
        // Le client a oublié les entités de l'ancien monde
        plugin.display().forget(event.getPlayer());
    }

    // Réapparition : le client reçoit un nouveau monde, sans les entités d'avant
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.display().forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        plugin.explosions().pickup(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (plugin.explosions().isExplosionItem(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
