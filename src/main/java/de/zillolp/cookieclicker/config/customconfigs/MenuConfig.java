package de.zillolp.cookieclicker.config.customconfigs;

import de.zillolp.cookieclicker.CookieClicker;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/** menus.yml : mise en page et textes des menus. */
public class MenuConfig extends CustomConfig {

    public MenuConfig(CookieClicker plugin, String name) {
        super(plugin, name);
    }

    public String getString(String path) {
        return fileConfiguration.getString(path, "");
    }

    public List<String> getLines(String path) {
        return fileConfiguration.getStringList(path);
    }

    public int getInt(String path, int defaultValue) {
        return fileConfiguration.getInt(path, defaultValue);
    }

    /** Taille d'un menu : un multiple de 9 entre 9 et 54. */
    public int getSize(String path) {
        int size = fileConfiguration.getInt(path + ".size", 27);
        return Math.max(9, Math.min(54, (size / 9) * 9));
    }

    public Material getMaterial(String path, Material defaultMaterial) {
        Material material = Material.matchMaterial(fileConfiguration.getString(path, ""));
        if (material == null) {
            plugin.getLogger().warning("menus.yml : matériau inconnu à " + path + ", " + defaultMaterial.name() + " utilisé.");
            return defaultMaterial;
        }
        return material;
    }

    /** Cases données par un numéro, un intervalle (10-16) ou une liste « 10-16, 19-25 ». */
    public List<Integer> getSlots(String path) {
        List<Integer> slots = new ArrayList<>();
        Object value = fileConfiguration.get(path);
        if (value instanceof List<?> list) {
            for (Object element : list) {
                parseSlots(String.valueOf(element), slots, path);
            }
        } else if (value != null) {
            parseSlots(String.valueOf(value), slots, path);
        }
        return slots;
    }

    private void parseSlots(String text, List<Integer> slots, String path) {
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int dash = trimmed.indexOf('-', 1);
                if (dash < 0) {
                    slots.add(Integer.parseInt(trimmed));
                    continue;
                }
                int from = Integer.parseInt(trimmed.substring(0, dash).trim());
                int to = Integer.parseInt(trimmed.substring(dash + 1).trim());
                for (int slot = Math.min(from, to); slot <= Math.max(from, to); slot++) {
                    slots.add(slot);
                }
            } catch (NumberFormatException exception) {
                plugin.getLogger().warning("menus.yml : cases illisibles à " + path + " : " + trimmed);
            }
        }
    }
}
