package org.Cry_Legende.sHCookieClicker.menu;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.SoundSet;
import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Clic droit sur le clicker : la fiche du joueur, la boutique et les designs. */
public final class HomeMenu extends Menu {

    public HomeMenu(SHCookieClicker plugin, Player player) {
        super(plugin, player, "accueil");
    }

    @Override
    protected void draw() {
        stats();
        button("accueil.boutique", Material.EMERALD, plugin.menus().lines("accueil.boutique.lore"), type -> {
            plugin.settings().sounds.play(player, SoundSet.Action.NAVIGATION);
            openLater(new UpgradeMenu(plugin, player, ShopKind.DEFAULT));
        });
        button("accueil.designs", Material.PAINTING, plugin.menus().lines("accueil.designs.lore"), type -> {
            plugin.settings().sounds.play(player, SoundSet.Action.NAVIGATION);
            openLater(new DesignCategoryMenu(plugin, player));
        });
    }
}
