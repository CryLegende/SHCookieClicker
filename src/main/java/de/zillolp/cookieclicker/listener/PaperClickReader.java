package de.zillolp.cookieclicker.listener;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.customevents.CookieClickerInteractEvent;
import de.zillolp.cookieclicker.interfaces.PacketReader;
import de.zillolp.cookieclicker.manager.CookieClickerManager;
import de.zillolp.cookieclicker.profiles.ClickerGameProfile;
import de.zillolp.cookieclicker.utils.ReflectionUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Version SH : remplace PacketReader_v1_20_R4, qui lisait les paquets entrants par une injection netty et du NMS.
 * Mêmes trois cas, avec les événements Bukkit :
 * - début de minage du bloc clicker (START_DESTROY_BLOCK) : clic gauche, et le minage est annulé (ABORT_DESTROY_BLOCK) ;
 * - coup de bras en aventure visant le bloc clicker : clic gauche aventure, avec le délai adventureClickDelay ;
 * - le clic droit (UseItemOn) est traité par ClickerListener.
 * Les événements sont lus même s'ils ont été annulés (protection de zone), comme les paquets l'étaient.
 */
public class PaperClickReader implements PacketReader, Listener {
    private final CookieClicker plugin;
    private final ReflectionUtil reflectionUtil;
    private final CookieClickerManager cookieClickerManager;

    public PaperClickReader(CookieClicker plugin) {
        this.plugin = plugin;
        reflectionUtil = plugin.getReflectionUtil();
        cookieClickerManager = plugin.getCookieClickerManager();
    }

    @Override
    public void inject(Player player) {
        // rien à injecter : les clics arrivent par les événements Bukkit
    }

    @Override
    public void unInject(Player player) {
    }

    private boolean isClicker(Location location) {
        return cookieClickerManager.getClickerLocations().stream().anyMatch(location1 -> reflectionUtil.isSameLocation(location1, location));
    }

    private void execute(Player player, Location location, CookieClickerInteractEvent.InteractType interactType) {
        CookieClickerInteractEvent cookieClickerInteractEvent = new CookieClickerInteractEvent(player, location, interactType, false);
        plugin.getServer().getPluginManager().callEvent(cookieClickerInteractEvent);
        if (!(cookieClickerInteractEvent.isCancelled())) {
            plugin.getClickerHandler().handleClickerInteract(cookieClickerInteractEvent);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() == Material.AIR || !(isClicker(block.getLocation()))) {
            return;
        }
        execute(event.getPlayer(), block.getLocation(), CookieClickerInteractEvent.InteractType.LEFT_CLICK);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamageEvent(BlockDamageEvent event) {
        if (isClicker(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerAnimationEvent(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING || player.getGameMode() != GameMode.ADVENTURE) {
            return;
        }
        Block clickedBlock = player.getTargetBlockExact(5);
        if (clickedBlock == null || clickedBlock.getType() == Material.AIR || !(isClicker(clickedBlock.getLocation()))) {
            return;
        }
        ClickerGameProfile clickerGameProfile = plugin.getClickerPlayerManager().getGameProfile(player.getUniqueId());
        if (clickerGameProfile == null || clickerGameProfile.isOverLastClickerInteraction(cookieClickerManager.adventureClickDelay)) {
            return;
        }
        clickerGameProfile.updateLastClickerInteraction();
        execute(player, clickedBlock.getLocation(), CookieClickerInteractEvent.InteractType.ADVENTURE_LEFT_CLICK);
    }
}
