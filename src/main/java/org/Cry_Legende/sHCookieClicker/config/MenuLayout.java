package org.Cry_Legende.sHCookieClicker.config;

import org.Cry_Legende.sHCookieClicker.util.Slots;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/** menus.yml, avec le fichier du jar en valeurs par défaut. */
public final class MenuLayout {
    private final YamlConfiguration file;

    public MenuLayout(YamlConfiguration file) {
        this.file = file;
    }

    public String text(String path) {
        String text = file.getString(path);
        return text == null ? "" : text;
    }

    public List<String> lines(String path) {
        return file.getStringList(path);
    }

    public Material material(String path, Material fallback) {
        String name = file.getString(path);
        Material material = name == null ? null : Material.matchMaterial(name.trim());
        return material == null || !material.isItem() ? fallback : material;
    }

    /** Une case, ou -1 si elle n'est pas donnée. */
    public int slot(String path) {
        Object value = file.get(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        List<Integer> slots = Slots.parse(value);
        return slots.isEmpty() ? -1 : slots.getFirst();
    }

    public List<Integer> slots(String path) {
        return Slots.parse(file.get(path));
    }

    /** Taille de l'inventaire d'un menu (lignes × 9, de 1 à 6 lignes). */
    public int size(String menu) {
        int rows = file.getInt(menu + ".lignes");
        return (rows <= 0 ? 3 : Math.min(6, rows)) * 9;
    }
}
