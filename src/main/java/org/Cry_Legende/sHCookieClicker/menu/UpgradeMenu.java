package org.Cry_Legende.sHCookieClicker.menu;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.SoundSet;
import org.Cry_Legende.sHCookieClicker.config.Upgrade;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.Cry_Legende.sHCookieClicker.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/**
 * Boutique d'améliorations, normale ou VipElite : chaque achat ajoute des cookies par clic et fait monter le prix.
 * Clic : un achat ; shift-clic : dix d'un coup, ou autant que les cookies le permettent.
 */
public final class UpgradeMenu extends Menu {
    public static final String PREMIUM_PERMISSION = "shcookieclicker.premium";
    private static final int MULTI_BUY = 10;
    private final ShopKind kind;

    public UpgradeMenu(SHCookieClicker plugin, Player player, ShopKind kind) {
        super(plugin, player, kind == ShopKind.PREMIUM ? "vipelite" : "boutique");
        this.kind = kind;
    }

    @Override
    protected void draw() {
        stats();
        List<Integer> slots = plugin.menus().slots(section + ".articles");
        List<Upgrade> upgrades = new ArrayList<>(plugin.settings().upgrades(kind).values());
        for (int index = 0; index < slots.size() && index < upgrades.size(); index++) {
            Upgrade upgrade = upgrades.get(index);
            set(slots.get(index), icon(upgrade), type -> buy(upgrade, type));
        }
        if (kind == ShopKind.PREMIUM) {
            back(new UpgradeMenu(plugin, player, ShopKind.DEFAULT));
            return;
        }
        back(new HomeMenu(plugin, player));
        boolean allowed = player.hasPermission(PREMIUM_PERMISSION);
        button("boutique.vipelite", Material.NETHER_STAR, plugin.menus().lines(allowed ? "boutique.vipelite.lore" : "boutique.vipelite.lore-verrouille"), type -> {
            if (!player.hasPermission(PREMIUM_PERMISSION)) {
                plugin.settings().sounds.play(player, SoundSet.Action.VIPELITE_REFUSE);
                plugin.messages().send(player, "boutique.vipelite-requis");
                return;
            }
            plugin.settings().sounds.play(player, SoundSet.Action.VIPELITE);
            openLater(new UpgradeMenu(plugin, player, ShopKind.PREMIUM));
        });
    }

    private org.bukkit.inventory.ItemStack icon(Upgrade upgrade) {
        Profile profile = plugin.profiles().get(player.getUniqueId());
        long cookies = profile == null ? 0 : profile.cookies();
        long purchases = profile == null ? 0 : profile.purchases(kind, upgrade.id());
        long price = upgrade.priceAt(purchases);
        String[] values = {
                "gain", plugin.number(upgrade.gain()),
                "prix", plugin.number(price),
                "prix-x10", plugin.number(upgrade.priceOf(purchases, MULTI_BUY)),
                "achats", plugin.number(purchases),
                "manque", plugin.number(Math.max(0, price - cookies))};
        String name = Text.fill(plugin.menus().text("amelioration.nom"), values);
        List<String> lore = Text.fill(plugin.menus().lines(cookies >= price ? "amelioration.lore-achetable" : "amelioration.lore-trop-cher"), values);
        return item(upgrade.icon(), fill(name), fill(lore));
    }

    private void buy(Upgrade upgrade, ClickType type) {
        if (kind == ShopKind.PREMIUM && !player.hasPermission(PREMIUM_PERMISSION)) {
            plugin.settings().sounds.play(player, SoundSet.Action.VIPELITE_REFUSE);
            plugin.messages().send(player, "boutique.vipelite-requis");
            return;
        }
        Profile profile = plugin.profiles().getOrCreate(player.getUniqueId(), player.getName());
        int bought = profile.buyUpgrade(kind, upgrade, type.isShiftClick() ? MULTI_BUY : 1);
        if (bought == 0) {
            long missing = Math.max(0, upgrade.priceAt(profile.purchases(kind, upgrade.id())) - profile.cookies());
            plugin.settings().sounds.play(player, SoundSet.Action.ACHAT_REFUSE);
            plugin.messages().send(player, "boutique.trop-cher", "manque", plugin.number(missing));
            return;
        }
        plugin.ranking().markChanged();
        plugin.settings().sounds.play(player, SoundSet.Action.ACHAT);
        render();
    }
}
