package de.zillolp.cookieclicker.menu;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.enums.SoundType;
import de.zillolp.cookieclicker.utils.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Menu qui range ses articles sur les cases « slots » de menus.yml, page par page. */
abstract class PagedMenu extends Menu {
    private int page;

    PagedMenu(CookieClicker plugin, Player player, String path) {
        super(plugin, player, path);
    }

    /** Numéros des articles, dans l'ordre d'affichage. */
    protected abstract List<Integer> itemIds();

    /** Objet et action d'un article. */
    protected abstract void setItem(int slot, int id);

    private int pageSize() {
        return Math.max(1, menuConfig.getSlots(path + ".slots").size());
    }

    private int pages() {
        return Math.max(1, (itemIds().size() + pageSize() - 1) / pageSize());
    }

    @Override
    protected String title() {
        return menuConfig.getString(path + ".title").replace("%page%", String.valueOf(page + 1)).replace("%pages%", String.valueOf(pages()));
    }

    protected void fillPage() {
        List<Integer> slots = menuConfig.getSlots(path + ".slots");
        List<Integer> ids = itemIds();
        page = Math.max(0, Math.min(page, pages() - 1));
        for (int index = 0; index < slots.size(); index++) {
            int itemIndex = page * pageSize() + index;
            if (itemIndex >= ids.size()) {
                break;
            }
            setItem(slots.get(index), ids.get(itemIndex));
        }
        int backSlot = menuConfig.getInt(path + ".back-slot", 0);
        if (page > 0) {
            set(menuConfig.getInt(path + ".previous-page-slot", backSlot - 1), arrow("previous-page"), clickType -> turn(-1));
        }
        if (page < pages() - 1) {
            set(menuConfig.getInt(path + ".next-page-slot", backSlot + 1), arrow("next-page"), clickType -> turn(1));
        }
    }

    private ItemStack arrow(String section) {
        return Items.build(menuConfig.getMaterial(section + ".material", Material.ARROW), menuConfig.getString(section + ".name"), menuConfig.getLines(section + ".lore"));
    }

    private void turn(int direction) {
        soundManager.playSound(player, direction > 0 ? SoundType.NEXT : SoundType.BACK);
        page += direction;
        render();
        if (menuConfig.getString(path + ".title").contains("%page%")) {
            player.getOpenInventory().setTitle(languageConfig.replaceValues(title(), uuid));
        }
    }

    /** Valeurs communes d'un article pour les textes de menus.yml. */
    protected Map<String, String> values(String displayName, long price, long purchases, long addClicks) {
        long missing = Math.max(0, price - plugin.getClickerPlayerManager().getStatsProfile(uuid).getCookies());
        return Map.of("%displayname%", displayName, "%price%", number(price), "%purchases%", number(purchases),
                "%addclick%", number(addClicks), "%missing%", number(missing));
    }

    /** Un clic de menu : ignoré s'il suit le précédent de trop près. */
    protected boolean throttled() {
        var game = plugin.getClickerPlayerManager().getGameProfile(uuid);
        if (game.isOverLastMenuInteraction(80)) {
            return true;
        }
        game.updateLastMenuInteraction();
        return false;
    }

    protected static boolean isLeft(ClickType clickType) {
        return clickType == ClickType.LEFT || clickType == ClickType.SHIFT_LEFT;
    }

    protected static boolean isRight(ClickType clickType) {
        return clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT;
    }
}
