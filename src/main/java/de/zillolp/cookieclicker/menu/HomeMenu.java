package de.zillolp.cookieclicker.menu;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.enums.ShopType;
import de.zillolp.cookieclicker.enums.SoundType;
import de.zillolp.cookieclicker.utils.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

/** Menu du clic droit sur le clicker : boutique et designs. */
public class HomeMenu extends Menu {

    public HomeMenu(CookieClicker plugin, Player player) {
        super(plugin, player, "home");
    }

    @Override
    protected void fill() {
        setStats();
        set(menuConfig.getInt("home.shop.slot", 13),
                Items.build(menuConfig.getMaterial("home.shop.material", Material.EMERALD), menuConfig.getString("home.shop.name"), replaced(menuConfig.getLines("home.shop.lore"), Map.of())),
                clickType -> {
                    soundManager.playSound(player, SoundType.SHOP);
                    new ShopMenu(plugin, player, ShopType.DEFAULT).open();
                });
        set(menuConfig.getInt("home.design.slot", 15),
                Items.build(menuConfig.getMaterial("home.design.material", Material.PAINTING), menuConfig.getString("home.design.name"), replaced(menuConfig.getLines("home.design.lore"), Map.of())),
                clickType -> {
                    soundManager.playSound(player, SoundType.DESIGN);
                    new DesignMenu(plugin, player).open();
                });
    }
}
