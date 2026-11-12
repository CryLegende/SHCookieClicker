package de.zillolp.cookieclicker.menu;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.config.customconfigs.LanguageConfig;
import de.zillolp.cookieclicker.config.customconfigs.MenuConfig;
import de.zillolp.cookieclicker.config.customconfigs.PluginConfig;
import de.zillolp.cookieclicker.enums.ShopType;
import de.zillolp.cookieclicker.enums.SoundType;
import de.zillolp.cookieclicker.manager.SoundManager;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import de.zillolp.cookieclicker.utils.Items;
import de.zillolp.cookieclicker.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Un menu du clicker, propre à un joueur. Chaque case porte son action : un clic ne lit jamais le nom ni le matériau
 * de l'objet cliqué. La mise en page vient de menus.yml.
 */
public abstract class Menu implements InventoryHolder {
    protected final CookieClicker plugin;
    protected final Player player;
    protected final UUID uuid;
    protected final PluginConfig pluginConfig;
    protected final MenuConfig menuConfig;
    protected final LanguageConfig languageConfig;
    protected final SoundManager soundManager;
    /** Section de menus.yml de ce menu. */
    protected final String path;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
    private Inventory inventory;

    protected Menu(CookieClicker plugin, Player player, String path) {
        this.plugin = plugin;
        this.player = player;
        this.path = path;
        uuid = player.getUniqueId();
        pluginConfig = plugin.getPluginConfig();
        menuConfig = plugin.getMenuConfig();
        languageConfig = plugin.getLanguageConfig();
        soundManager = plugin.getSoundManager();
    }

    /** Pose les objets et leurs actions. Les cases restées vides reçoivent l'accent puis le fond. */
    protected abstract void fill();

    protected String title() {
        return menuConfig.getString(path + ".title");
    }

    public void open() {
        inventory = Bukkit.createInventory(this, menuConfig.getSize(path), Text.component(languageConfig.replaceValues(title(), uuid)));
        render();
        player.openInventory(inventory);
    }

    /** Redessine le menu ouvert : cookies, prix, sélection, fond. */
    public void render() {
        actions.clear();
        inventory.clear();
        fill();
        ItemStack accent = Items.build(menuConfig.getMaterial("accent.material", Material.GRAY_STAINED_GLASS_PANE), menuConfig.getString("accent.name"), null);
        for (int slot : menuConfig.getSlots(path + ".accent-slots")) {
            if (isFree(slot)) {
                inventory.setItem(slot, accent);
            }
        }
        ItemStack filler = Items.build(fillerMaterial(), menuConfig.getString("filler.name"), null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    /** Vitre de fond : le design de menu choisi par le joueur, sinon celle de menus.yml. */
    private Material fillerMaterial() {
        ClickerStatsProfile stats = plugin.getClickerPlayerManager().getStatsProfile(uuid);
        long design = stats.getMenuDesign();
        if (design > 0 && pluginConfig.hasShopItem(ShopType.MENU_DESIGN, (int) design)) {
            return pluginConfig.getBlockType(ShopType.MENU_DESIGN, design);
        }
        return menuConfig.getMaterial("filler.material", Material.BLACK_STAINED_GLASS_PANE);
    }

    private boolean isFree(int slot) {
        return slot >= 0 && slot < inventory.getSize() && inventory.getItem(slot) == null;
    }

    protected void set(int slot, ItemStack itemStack, Consumer<ClickType> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, itemStack);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    /** Clic du joueur sur une case du menu. */
    void click(int slot, ClickType clickType) {
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) {
            action.accept(clickType);
        }
    }

    protected ItemStack statsItem() {
        return Items.build(menuConfig.getMaterial("stats.material", Material.COOKIE), languageConfig.replaceValues(menuConfig.getString("stats.name"), uuid),
                replaced(menuConfig.getLines("stats.lore"), Map.of()));
    }

    protected void setStats() {
        int slot = menuConfig.getInt(path + ".stats-slot", -1);
        if (slot >= 0) {
            set(slot, statsItem(), null);
        }
    }

    /** Bouton « Retour » : joue le son de retour puis exécute l'action. */
    protected void setBack(Runnable target) {
        set(menuConfig.getInt(path + ".back-slot", 0),
                Items.build(menuConfig.getMaterial("back.material", Material.PAPER), menuConfig.getString("back.name"), menuConfig.getLines("back.lore")),
                clickType -> {
                    soundManager.playSound(player, SoundType.BACK);
                    target.run();
                });
    }

    /** Lignes d'un texte de menu : placeholders du joueur, puis ceux de la boutique (%price%...). */
    protected List<String> replaced(List<String> lines, Map<String, String> values) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(replaced(line, values));
        }
        return result;
    }

    protected String replaced(String line, Map<String, String> values) {
        String result = line;
        for (Map.Entry<String, String> value : values.entrySet()) {
            result = result.replace(value.getKey(), value.getValue());
        }
        return languageConfig.replaceValues(result, uuid);
    }

    protected String number(long value) {
        return languageConfig.formatNumber(value);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
