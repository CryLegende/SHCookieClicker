package org.Cry_Legende.sHCookieClicker.game;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Événements du clicker. Ils sont lus même annulés (la zone du spawn interdit de casser et d'utiliser les blocs),
 * et chacun sort tout de suite quand il ne vise pas un clicker : une recherche dans un ensemble de positions.
 */
public final class ClickerListener implements Listener {
    private static final int ADVENTURE_REACH = 5;

    private final SHCookieClicker plugin;
    /** Tick du dernier clic gauche compté par joueur : un même coup en aventure n'est compté qu'une fois. */
    private final java.util.Map<java.util.UUID, Integer> lastLeftTick = new java.util.concurrent.ConcurrentHashMap<>();

    public ClickerListener(SHCookieClicker plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Action action = event.getAction();
        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.HAND && (action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK)
                && plugin.clicks().placing(player, block, action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            return;
        }
        if (block == null || !plugin.clickers().isClicker(block)) {
            return;
        }
        if (action == Action.LEFT_CLICK_BLOCK) {
            lastLeftTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
            plugin.clicks().leftClick(player, block);
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                ItemStack item = event.getItem();
                plugin.clicks().rightClick(player, item != null && item.getType().isBlock() && !item.getType().isAir());
            }
        }
    }

    // En aventure, le jeu n'envoie pas de coup sur un bloc qu'on ne peut pas casser : seul le geste du bras arrive
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING || player.getGameMode() != GameMode.ADVENTURE
                || !plugin.clickers().hasClickers(player.getWorld())) {
            return;
        }
        Integer tick = lastLeftTick.get(player.getUniqueId());
        if ((tick != null && tick == Bukkit.getCurrentTick()) || !plugin.clickers().near(player.getLocation(), ADVENTURE_REACH + 1)) {
            return;
        }
        Block target = player.getTargetBlockExact(ADVENTURE_REACH);
        if (target != null && plugin.clickers().isClicker(target)) {
            lastLeftTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
            plugin.clicks().leftClick(player, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(BlockDamageEvent event) {
        if (plugin.clickers().isClicker(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.clickers().isClicker(event.getBlock())) {
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

    // Le jeu oublie le bloc du design quand il reçoit à nouveau le tronçon du clicker
    @EventHandler
    public void onChunkSent(PlayerChunkLoadEvent event) {
        if (plugin.clickers().isClickerChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ())) {
            Session session = plugin.sessions().get(event.getPlayer());
            if (session != null && session.loaded) {
                plugin.look().show(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.clicks().join(event.getPlayer());
    }

    // Avant SHNetwork (MONITOR), qui lance alors les sauvegardes de départ et attend la nôtre
    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        lastLeftTick.remove(event.getPlayer().getUniqueId());
        plugin.clicks().leave(event.getPlayer(), true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Session session = plugin.sessions().get(event.getPlayer());
        if (session != null) {
            session.moved(System.currentTimeMillis());
            session.designSent = false;
        }
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
