package de.zillolp.cookieclicker.menu;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.enums.ShopType;
import de.zillolp.cookieclicker.enums.SoundType;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import de.zillolp.cookieclicker.utils.Items;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.profile.PlayerProfile;

import java.util.List;
import java.util.Map;

/**
 * Boutique de designs (bloc du clicker, effet de coup, fond des menus) : un design s'achète une fois, puis se
 * sélectionne gratuitement (clic gauche) et se désactive (clic droit sur le design sélectionné).
 */
public class DesignShopMenu extends PagedMenu {
    private final ShopType shopType;

    public DesignShopMenu(CookieClicker plugin, Player player, ShopType shopType) {
        super(plugin, player, switch (shopType) {
            case BLOCK_DESIGN -> "block-design";
            case HIT_PARTICLE_DESIGN -> "hit-particle-design";
            default -> "menu-design";
        });
        this.shopType = shopType;
    }

    @Override
    protected List<Integer> itemIds() {
        return pluginConfig.getShopIds(shopType);
    }

    @Override
    protected void fill() {
        setStats();
        fillPage();
        setBack(() -> new DesignMenu(plugin, player).open());
    }

    @Override
    protected void setItem(int slot, int id) {
        ClickerStatsProfile stats = plugin.getClickerPlayerManager().getStatsProfile(uuid);
        long price = pluginConfig.getBasePrice(shopType, id);
        Map<String, String> values = values(pluginConfig.getDisplayName(shopType, id), price, 0, 0);
        boolean owned = stats.owns(shopType, id);
        boolean selected = owned && stats.getSelectedDesign(shopType) == id;
        String state = selected ? "selected" : owned ? "owned" : stats.getCookies() >= price ? "buyable" : "locked";
        set(slot, Items.build(pluginConfig.getType(shopType, id), replaced(menuConfig.getString("design-item.name-" + state), values),
                replaced(menuConfig.getLines("design-item.lore-" + state), values), selected, headProfile(id)), clickType -> onItemClick(id, clickType));
    }

    /** Profil de la tête d'un design de bloc : tête à texture, ou tête du joueur. */
    private PlayerProfile headProfile(int id) {
        if (shopType != ShopType.BLOCK_DESIGN) {
            return null;
        }
        String typeName = pluginConfig.getTypeName(shopType, id);
        if (typeName.equalsIgnoreCase("PLAYER_HEAD")) {
            return Items.textureProfile(pluginConfig.getSkullTexture(shopType, id));
        }
        if (typeName.equalsIgnoreCase("REAL_PLAYER_HEAD")) {
            return plugin.getReflectionUtil().getRealPlayerProfile(player);
        }
        return null;
    }

    private void onItemClick(int id, ClickType clickType) {
        if (throttled()) {
            return;
        }
        ClickerStatsProfile stats = plugin.getClickerPlayerManager().getStatsProfile(uuid);
        if (stats.owns(shopType, id)) {
            boolean selected = stats.getSelectedDesign(shopType) == id;
            if (selected && isRight(clickType)) {
                stats.setSelectedDesign(shopType, 0);
                soundManager.playSound(player, SoundType.REMOVE_DESIGN);
                if (shopType == ShopType.BLOCK_DESIGN) {
                    plugin.getDesignManager().resetClickerBlocks(player);
                }
                render();
            } else if (!selected && isLeft(clickType)) {
                select(stats, id, SoundType.SELECT_DESIGN);
            }
            return;
        }
        if (!(isLeft(clickType))) {
            return;
        }
        long price = pluginConfig.getBasePrice(shopType, id);
        if (stats.getCookies() < price) {
            soundManager.playSound(player, SoundType.BUY_DENY);
            player.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.MESSAGE_NOT_BUYABLE).replace("%missing%", number(price - stats.getCookies())));
            return;
        }
        stats.removeCookies(price);
        stats.addPurchase(shopType, id);
        select(stats, id, SoundType.BUY_ALLOW);
    }

    private void select(ClickerStatsProfile stats, int id, SoundType soundType) {
        stats.setSelectedDesign(shopType, id);
        soundManager.playSound(player, soundType);
        if (shopType == ShopType.BLOCK_DESIGN) {
            plugin.getDesignManager().sendClickerBlockDesign(player);
        }
        render();
    }
}
