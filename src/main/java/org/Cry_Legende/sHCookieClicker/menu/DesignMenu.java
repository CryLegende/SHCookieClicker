package org.Cry_Legende.sHCookieClicker.menu;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.Designs;
import org.Cry_Legende.sHCookieClicker.config.MenuLayout;
import org.Cry_Legende.sHCookieClicker.config.SoundSet;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.Cry_Legende.sHCookieClicker.hook.Heads;
import org.Cry_Legende.sHCookieClicker.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/**
 * Designs d'une sorte : un design s'achète une fois, puis se sélectionne gratuitement (clic gauche) et se retire
 * (clic droit sur le design sélectionné). Les articles se rangent page par page sur les cases « articles ».
 */
public final class DesignMenu extends Menu {
    private final ShopKind kind;
    private final int page;

    public DesignMenu(SHCookieClicker plugin, Player player, ShopKind kind, int page) {
        super(plugin, player, switch (kind) {
            case BLOCK_DESIGN -> "designs-blocs";
            default -> "designs-effets";
        });
        this.kind = kind;
        this.page = page;
    }

    private List<Designs.Design> designs() {
        return new ArrayList<>(plugin.settings().designs(kind).values());
    }

    private int pageSize() {
        return Math.max(1, plugin.menus().slots(section + ".articles").size());
    }

    private int pages() {
        return Math.max(1, (designs().size() + pageSize() - 1) / pageSize());
    }

    private int currentPage() {
        return Math.max(0, Math.min(page, pages() - 1));
    }

    @Override
    protected String title() {
        return Text.fill(super.title(), "page", String.valueOf(currentPage() + 1), "pages", String.valueOf(pages()));
    }

    @Override
    protected void draw() {
        stats();
        MenuLayout layout = plugin.menus();
        List<Integer> slots = layout.slots(section + ".articles");
        List<Designs.Design> designs = designs();
        int first = currentPage() * pageSize();
        for (int index = 0; index < slots.size() && first + index < designs.size(); index++) {
            Designs.Design design = designs.get(first + index);
            set(slots.get(index), icon(design), type -> clicked(design, type));
        }
        back(new DesignCategoryMenu(plugin, player));
        int backSlot = layout.slot(section + ".retour");
        if (currentPage() > 0) {
            int slot = layout.slot(section + ".page-precedente");
            set(slot >= 0 ? slot : backSlot - 1, arrow("page-precedente"), type -> turn(-1));
        }
        if (currentPage() < pages() - 1) {
            int slot = layout.slot(section + ".page-suivante");
            set(slot >= 0 ? slot : backSlot + 1, arrow("page-suivante"), type -> turn(1));
        }
    }

    private org.bukkit.inventory.ItemStack arrow(String path) {
        MenuLayout layout = plugin.menus();
        return item(layout.material(path + ".materiau", Material.ARROW), layout.text(path + ".nom"), layout.lines(path + ".lore"));
    }

    private void turn(int direction) {
        plugin.settings().sounds.play(player, direction > 0 ? SoundSet.Action.NAVIGATION : SoundSet.Action.RETOUR);
        openLater(new DesignMenu(plugin, player, kind, currentPage() + direction));
    }

    private org.bukkit.inventory.ItemStack icon(Designs.Design design) {
        Profile profile = plugin.profiles().get(player.getUniqueId());
        long cookies = profile == null ? 0 : profile.cookies();
        boolean owned = profile != null && profile.owns(kind, design.id());
        boolean selected = owned && profile.selected(kind) == design.id();
        String state = selected ? "selectionne" : owned ? "possede" : cookies >= design.price() ? "achetable" : "trop-cher";
        String[] values = {
                "nom", design.name(),
                "prix", plugin.number(design.price()),
                "manque", plugin.number(Math.max(0, design.price() - cookies))};
        MenuLayout layout = plugin.menus();
        String name = fill(Text.fill(layout.text("design.nom-" + state), values));
        List<String> lore = fill(Text.fill(layout.lines("design.lore-" + state), values));
        PlayerProfile head = null;
        Material material;
        switch (design) {
            case Designs.Block block -> {
                material = block.isHead() ? Material.PLAYER_HEAD : block.block();
                if (block.playerHead()) {
                    head = Heads.of(player);
                } else if (block.texture() != null) {
                    head = Heads.texture(block.texture());
                }
                if (!material.isItem()) {
                    material = Material.STONE;
                }
            }
            case Designs.Effect effect -> material = effect.icon();
        }
        return item(material, name, lore, selected, head);
    }

    private void clicked(Designs.Design design, ClickType type) {
        Profile profile = plugin.profiles().getOrCreate(player.getUniqueId(), player.getName());
        if (profile.owns(kind, design.id())) {
            boolean selected = profile.selected(kind) == design.id();
            if (selected && type.isRightClick()) {
                profile.select(kind, 0);
                plugin.settings().sounds.play(player, SoundSet.Action.DESIGN_RETIRE);
                if (kind == ShopKind.BLOCK_DESIGN) {
                    plugin.look().reset(player);
                }
                render();
            } else if (!selected && type.isLeftClick()) {
                profile.select(kind, design.id());
                selected(SoundSet.Action.DESIGN_CHOISI);
            }
            return;
        }
        if (!type.isLeftClick()) {
            return;
        }
        if (!profile.buyDesign(kind, design.id(), design.price())) {
            plugin.settings().sounds.play(player, SoundSet.Action.ACHAT_REFUSE);
            plugin.messages().send(player, "boutique.trop-cher", "manque", plugin.number(Math.max(0, design.price() - profile.cookies())));
            return;
        }
        plugin.ranking().markChanged();
        selected(SoundSet.Action.ACHAT);
    }

    private void selected(SoundSet.Action sound) {
        plugin.settings().sounds.play(player, sound);
        if (kind == ShopKind.BLOCK_DESIGN) {
            plugin.look().show(player);
        }
        render();
    }
}
