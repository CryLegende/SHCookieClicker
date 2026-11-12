package org.Cry_Legende.sHCookieClicker.menu;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.Designs;
import org.Cry_Legende.sHCookieClicker.config.MenuLayout;
import org.Cry_Legende.sHCookieClicker.config.SoundSet;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.Cry_Legende.sHCookieClicker.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Un menu du clicker, propre à un joueur. Chaque case porte son action : un clic ne lit jamais le nom ou le
 * matériau de l'objet. La mise en page vient de menus.yml (section {@link #section}).
 */
public abstract class Menu implements InventoryHolder {
    protected final SHCookieClicker plugin;
    protected final Player player;
    protected final String section;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    private Inventory inventory;

    protected Menu(SHCookieClicker plugin, Player player, String section) {
        this.plugin = plugin;
        this.player = player;
        this.section = section;
    }

    /** Pose les objets et leurs actions ; les cases restées vides prennent l'accent puis le fond. */
    protected abstract void draw();

    protected String title() {
        return plugin.menus().text(section + ".titre");
    }

    public void open() {
        inventory = Bukkit.createInventory(this, plugin.menus().size(section), Text.of(fill(title())));
        render();
        player.openInventory(inventory);
    }

    /** Ouvre un autre menu au tick suivant (ouvrir un inventaire pendant un clic d'inventaire n'est pas sûr). */
    protected void openLater(Menu next) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                next.open();
            }
        });
    }

    /** Redessine le menu ouvert (cookies, prix, sélection, fond). */
    public void render() {
        actions.clear();
        inventory.clear();
        draw();
        MenuLayout layout = plugin.menus();
        ItemStack accent = pane(layout.material("accent.materiau", Material.GRAY_STAINED_GLASS_PANE));
        for (int slot : layout.slots(section + ".accents")) {
            if (slot >= 0 && slot < inventory.getSize() && inventory.getItem(slot) == null) {
                inventory.setItem(slot, accent);
            }
        }
        ItemStack background = pane(backgroundPane());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, background);
            }
        }
    }

    /** Vitre du fond : le design « fond » choisi par le joueur, sinon celle de menus.yml. */
    private Material backgroundPane() {
        Profile profile = plugin.profiles().get(player.getUniqueId());
        long id = profile == null ? 0 : profile.selected(ShopKind.MENU_DESIGN);
        Designs.Background background = id > 0 ? plugin.settings().background(id) : null;
        return background != null ? background.pane() : plugin.menus().material("fond.materiau", Material.BLACK_STAINED_GLASS_PANE);
    }

    protected void set(int slot, ItemStack item, Consumer<ClickType> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    void click(int slot, ClickType type) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            action.accept(type);
        }
    }

    /** Bouton défini dans menus.yml (case, materiau, nom, lore). */
    protected void button(String path, Material fallback, List<String> lore, Consumer<ClickType> action) {
        MenuLayout layout = plugin.menus();
        set(layout.slot(path + ".case"), item(layout.material(path + ".materiau", fallback), fill(layout.text(path + ".nom")), fill(lore)), action);
    }

    protected void stats() {
        MenuLayout layout = plugin.menus();
        int slot = layout.slot(section + ".fiche");
        if (slot >= 0) {
            set(slot, item(layout.material("fiche.materiau", Material.COOKIE), fill(layout.text("fiche.nom")), fill(layout.lines("fiche.lore"))), null);
        }
    }

    /** « Retour » : son de retour, puis le menu précédent. */
    protected void back(Menu previous) {
        MenuLayout layout = plugin.menus();
        set(layout.slot(section + ".retour"), item(layout.material("retour.materiau", Material.PAPER), layout.text("retour.nom"), layout.lines("retour.lore")),
                type -> {
                    plugin.settings().sounds.play(player, SoundSet.Action.RETOUR);
                    openLater(previous);
                });
    }

    /** Valeurs du joueur dans un texte de menu : {cookies} {par-clic} {clics} {place}. */
    protected String fill(String text) {
        Profile profile = plugin.profiles().get(player.getUniqueId());
        int place = plugin.ranking().place(player.getUniqueId());
        return Text.fill(text,
                "cookies", plugin.number(profile == null ? 0 : profile.cookies()),
                "par-clic", plugin.number(profile == null ? 1 : profile.perClick()),
                "clics", plugin.number(profile == null ? 0 : profile.clickerClicks()),
                "place", place > 0 ? "#" + plugin.number(place) : "-");
    }

    protected List<String> fill(List<String> lines) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(fill(line));
        }
        return result;
    }

    /** Vitre de décor : aucune infobulle au survol. */
    private static ItemStack pane(Material material) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> meta.setHideTooltip(true));
        return stack;
    }

    /** Objet de menu : nom et description sans italique, infobulle réduite au nom et à la description. */
    protected static ItemStack item(Material material, String name, List<String> lore) {
        return item(material, name, lore, false, null);
    }

    protected static ItemStack item(Material material, String name, List<String> lore, boolean glint, PlayerProfile head) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            if (name != null) {
                meta.displayName(Text.item(name));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.lore(Text.items(lore));
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            if (head != null && meta instanceof SkullMeta skull) {
                skull.setPlayerProfile(head);
            }
        });
        return stack;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
