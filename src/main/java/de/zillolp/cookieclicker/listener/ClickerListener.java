package de.zillolp.cookieclicker.listener;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.customevents.CookieClickerInteractEvent;
import de.zillolp.cookieclicker.utils.ReflectionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ClickerListener implements Listener {
    private final CookieClicker plugin;
    private final ReflectionUtil reflectionUtil;

    public ClickerListener(CookieClicker plugin) {
        this.plugin = plugin;
        reflectionUtil = plugin.getReflectionUtil();
    }

/*    @EventHandler
    public void onCookieClickerInteractEvent(CookieClickerInteractEvent event) {
        event.setCancelled(true);
    }*/

    @EventHandler
    public void onBlockBreakEvent(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.AIR) {
            return;
        }
        Location location = block.getLocation();
        if (!(plugin.getCookieClickerManager().getClickerLocations().contains(location))) {
            return;
        }
        CookieClickerInteractEvent cookieClickerInteractEvent = new CookieClickerInteractEvent(event.getPlayer(), location, CookieClickerInteractEvent.InteractType.LEFT_CLICK_BREAK, false);
        plugin.getServer().getPluginManager().callEvent(cookieClickerInteractEvent);
        if (cookieClickerInteractEvent.isCancelled()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() == Material.AIR) {
            return;
        }
        Location location = clickedBlock.getLocation();
        boolean locationFound = plugin.getCookieClickerManager().getClickerLocations().stream().anyMatch(location1 -> reflectionUtil.isSameLocation(location1, location));
        if (!(locationFound)) {
            return;
        }
        CookieClickerInteractEvent.InteractType interactType = CookieClickerInteractEvent.InteractType.HAND_RIGHT_CLICK;
        if (event.getHand() != EquipmentSlot.HAND) {
            interactType = CookieClickerInteractEvent.InteractType.OFF_HAND_RIGHT_CLICK;
        }
        Player player = event.getPlayer();
        // Version SH : ce gestionnaire reçoit tous les clics droits. L'original n'y voyait que ceux faits avec un bloc
        // en main ; les autres étaient interceptés en paquets (PacketReader) avec blockPlaced à false.
        ItemStack itemStack = event.getItem();
        boolean blockPlaced = itemStack != null && itemStack.getType() != Material.AIR && itemStack.getType().isBlock();
        CookieClickerInteractEvent cookieClickerInteractEvent = new CookieClickerInteractEvent(player, location, interactType, blockPlaced);
        plugin.getServer().getPluginManager().callEvent(cookieClickerInteractEvent);
        if (cookieClickerInteractEvent.isCancelled()) {
            return;
        }
        plugin.getClickerHandler().handleClickerInteract(cookieClickerInteractEvent);
        event.setCancelled(true);
    }
}
