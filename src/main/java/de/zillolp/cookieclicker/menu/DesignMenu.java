package de.zillolp.cookieclicker.menu;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.enums.ShopType;
import de.zillolp.cookieclicker.enums.SoundType;
import de.zillolp.cookieclicker.utils.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

/** Choix de la catégorie de design : bloc du clicker, effet de coup, fond des menus. */
public class DesignMenu extends Menu {

    public DesignMenu(CookieClicker plugin, Player player) {
        super(plugin, player, "design");
    }

    @Override
    protected void fill() {
        category("block", Material.PRISMARINE, ShopType.BLOCK_DESIGN, SoundType.BLOCK_DESIGN);
        category("hit-particle", Material.DRAGON_BREATH, ShopType.HIT_PARTICLE_DESIGN, SoundType.HIT_PARTICLE_DESIGN);
        category("menu", Material.PAINTING, ShopType.MENU_DESIGN, SoundType.MENU_DESIGN);
        setBack(() -> new HomeMenu(plugin, player).open());
    }

    private void category(String key, Material defaultMaterial, ShopType shopType, SoundType soundType) {
        String section = "design." + key;
        set(menuConfig.getInt(section + ".slot", 0),
                Items.build(menuConfig.getMaterial(section + ".material", defaultMaterial), menuConfig.getString(section + ".name"), replaced(menuConfig.getLines(section + ".lore"), Map.of())),
                clickType -> {
                    soundManager.playSound(player, soundType);
                    new DesignShopMenu(plugin, player, shopType).open();
                });
    }
}
