package de.zillolp.cookieclicker.menu;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.enums.ShopType;
import de.zillolp.cookieclicker.enums.SoundType;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import de.zillolp.cookieclicker.utils.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** Boutique d'améliorations (normale ou VipElite) : chaque achat ajoute des cookies par clic et fait monter le prix. */
public class ShopMenu extends PagedMenu {
    private final ShopType shopType;

    public ShopMenu(CookieClicker plugin, Player player, ShopType shopType) {
        super(plugin, player, shopType == ShopType.PREMIUM ? "premium-shop" : "shop");
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
        if (shopType == ShopType.PREMIUM) {
            setBack(() -> new ShopMenu(plugin, player, ShopType.DEFAULT).open());
            return;
        }
        setBack(() -> new HomeMenu(plugin, player).open());
        setPremiumButton();
    }

    @Override
    protected void setItem(int slot, int id) {
        ClickerStatsProfile stats = plugin.getClickerPlayerManager().getStatsProfile(uuid);
        long purchases = stats.getPurchases(shopType, id);
        long price = pluginConfig.getPrice(shopType, id, purchases);
        Map<String, String> values = values(pluginConfig.getDisplayName(shopType, id), price, purchases, pluginConfig.getAddClicks(shopType, id));
        List<String> lore = menuConfig.getLines(stats.getCookies() >= price ? "upgrade.lore-buyable" : "upgrade.lore-locked");
        set(slot, Items.build(pluginConfig.getType(shopType, id), replaced(menuConfig.getString("upgrade.name"), values), replaced(lore, values)), clickType -> buy(id));
    }

    private void buy(int id) {
        if (throttled()) {
            return;
        }
        if (shopType == ShopType.PREMIUM && !(player.hasPermission("shcookieclicker.premium"))) {
            soundManager.playSound(player, SoundType.PREMIUM_DENY);
            player.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.PREMIUM_REQUIRED));
            return;
        }
        ClickerStatsProfile stats = plugin.getClickerPlayerManager().getStatsProfile(uuid);
        long price = pluginConfig.getPrice(shopType, id, stats.getPurchases(shopType, id));
        if (stats.getCookies() < price) {
            soundManager.playSound(player, SoundType.BUY_DENY);
            player.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.MESSAGE_NOT_BUYABLE).replace("%missing%", number(price - stats.getCookies())));
            return;
        }
        long addClicks = pluginConfig.getAddClicks(shopType, id);
        stats.removeCookies(price);
        stats.addPerClick(addClicks);
        stats.addPurchase(shopType, id);
        plugin.getStatsWallUpdater().addTimedGain(stats.getName(), addClicks);
        soundManager.playSound(player, SoundType.BUY_ALLOW);
        render();
    }

    private void setPremiumButton() {
        boolean allowed = player.hasPermission("shcookieclicker.premium");
        set(menuConfig.getInt("shop.premium.slot", 33),
                Items.build(menuConfig.getMaterial("shop.premium.material", Material.NETHER_STAR), menuConfig.getString("shop.premium.name"),
                        replaced(menuConfig.getLines(allowed ? "shop.premium.lore" : "shop.premium.lore-locked"), Map.of())),
                clickType -> {
                    if (!allowed) {
                        soundManager.playSound(player, SoundType.PREMIUM_DENY);
                        player.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.PREMIUM_REQUIRED));
                        return;
                    }
                    soundManager.playSound(player, SoundType.PREMIUM_ALLOW);
                    new ShopMenu(plugin, player, ShopType.PREMIUM).open();
                });
    }
}
