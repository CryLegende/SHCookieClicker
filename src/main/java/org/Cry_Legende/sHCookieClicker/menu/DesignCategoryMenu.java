package org.Cry_Legende.sHCookieClicker.menu;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.SoundSet;
import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Choix de la sorte de design : bloc du clicker, effet de coup, fond des menus. */
public final class DesignCategoryMenu extends Menu {

    public DesignCategoryMenu(SHCookieClicker plugin, Player player) {
        super(plugin, player, "designs");
    }

    @Override
    protected void draw() {
        category("designs.blocs", Material.PRISMARINE, ShopKind.BLOCK_DESIGN);
        category("designs.effets", Material.DRAGON_BREATH, ShopKind.HIT_PARTICLE_DESIGN);
        category("designs.fonds", Material.PAINTING, ShopKind.MENU_DESIGN);
        back(new HomeMenu(plugin, player));
    }

    private void category(String path, Material fallback, ShopKind kind) {
        button(path, fallback, plugin.menus().lines(path + ".lore"), type -> {
            plugin.settings().sounds.play(player, SoundSet.Action.NAVIGATION);
            openLater(new DesignMenu(plugin, player, kind, 0));
        });
    }
}
