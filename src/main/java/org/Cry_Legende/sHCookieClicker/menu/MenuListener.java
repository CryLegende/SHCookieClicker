package org.Cry_Legende.sHCookieClicker.menu;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.game.Session;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/** Menus du clicker : rien ne bouge, la case cliquée exécute son action. */
public final class MenuListener implements Listener {
    private final SHCookieClicker plugin;

    public MenuListener(SHCookieClicker plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != top || event.getClick() == ClickType.DOUBLE_CLICK || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Session session = plugin.sessions().get(player);
        if (session == null || session.menuThrottled(System.currentTimeMillis())) {
            return;
        }
        menu.click(event.getRawSlot(), event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    // Fermer un menu du clicker compte comme un mouvement pour l'anti-AFK
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu) {
            Session session = plugin.sessions().get(event.getPlayer().getUniqueId());
            if (session != null) {
                session.moved(System.currentTimeMillis());
            }
        }
    }
}
