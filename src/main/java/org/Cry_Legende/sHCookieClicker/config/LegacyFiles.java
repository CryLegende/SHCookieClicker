package org.Cry_Legende.sHCookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Passage des fichiers de SHCookieClicker 1.x au format 2.0, une seule fois, au premier démarrage :
 * <ul>
 *   <li>config.yml : les réglages (boutiques, prix, sons, protections, événements, base MySQL, mode réseau) sont
 *       repris dans le nouveau fichier (sauf les fonds de menu, retirés) ; s'ils sont ceux de la 1.x, le fichier du jar
 *       est écrit tel quel ;</li>
 *   <li>mysql.yml : ses identifiants passent dans config.yml (s'ils servaient), puis le fichier est supprimé ;</li>
 *   <li>messages.yml et menus.yml : remplacés par ceux du jar (les textes et la mise en page ont changé).</li>
 * </ul>
 * La sauvegarde régulière n'est pas reprise : la 2.0 écrit par lots, toutes les 60 secondes.
 */
public final class LegacyFiles {
    private static final Map<String, String> SOUNDS = new LinkedHashMap<>();
    private static final Map<String, String> ANIMATIONS = Map.of(
            "SPIRAL", "SPIRALE", "CIRCLE_RINGS", "ANNEAUX", "EXPLOSION_WAVE", "ONDE",
            "STAR_BURST", "ETOILE", "MAGICAL_WHIRLWIND", "TOURBILLON");

    static {
        SOUNDS.put("click_allow", "clic");
        SOUNDS.put("click_deny", "clic-refuse");
        SOUNDS.put("open_inventory", "ouverture");
        SOUNDS.put("shop", "navigation");
        SOUNDS.put("back", "retour");
        SOUNDS.put("premium_allow", "vipelite");
        SOUNDS.put("premium_deny", "vipelite-refuse");
        SOUNDS.put("buy_allow", "achat");
        SOUNDS.put("buy_deny", "achat-refuse");
        SOUNDS.put("select_design", "design-choisi");
        SOUNDS.put("remove_design", "design-retire");
        SOUNDS.put("stats_info", "stats");
        SOUNDS.put("golden_cookie", "cookie-dore");
        SOUNDS.put("cookie_explosion", "explosion");
    }

    private LegacyFiles() {
    }

    /**
     * @param jarConfig   texte du config.yml du jar
     * @param jarMessages texte du messages.yml du jar
     * @param jarMenus    texte du menus.yml du jar
     * @return ce qui a été fait, une ligne par fichier (vide : rien à faire)
     */
    public static List<String> migrate(File folder, String jarConfig, String jarMessages, String jarMenus) throws IOException, InvalidConfigurationException {
        List<String> done = new ArrayList<>();
        File configFile = new File(folder, "config.yml");
        File mysqlFile = new File(folder, "mysql.yml");
        if (configFile.isFile()) {
            YamlConfiguration old = YamlConfiguration.loadConfiguration(configFile);
            if (isOldConfig(old)) {
                YamlConfiguration mysql = mysqlFile.isFile() ? YamlConfiguration.loadConfiguration(mysqlFile) : new YamlConfiguration();
                boolean customized = convert(old, mysql, jarConfig, configFile);
                done.add(customized
                        ? "config.yml de la 1.x converti au format 2.0, avec ses réglages (boutiques, prix, sons, événements, protections)"
                        : "config.yml de la 1.x remplacé par celui de la 2.0 (mêmes boutiques, prix, sons et réglages)");
                if (mysqlFile.isFile()) {
                    Files.delete(mysqlFile.toPath());
                    done.add("mysql.yml supprimé : " + (old.getBoolean("MySQL", false)
                            ? "ses identifiants sont dans config.yml (base-de-donnees.mysql)" : "il ne servait pas (MySQL: false)"));
                }
            }
        }
        replaceIfOld(new File(folder, "messages.yml"), jarMessages, "CLICK_MESSAGE", "PLAYER_STATS_INFO", done);
        replaceIfOld(new File(folder, "menus.yml"), jarMenus, "design-item", "upgrade", done);
        return done;
    }

    static boolean isOldConfig(YamlConfiguration config) {
        return config.contains("default_shop") || config.contains("MySQL") || config.contains("Cache synchronization");
    }

    private static void replaceIfOld(File file, String jarText, String markerA, String markerB, List<String> done) throws IOException {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        if (current.contains(markerA) || current.contains(markerB)) {
            Files.writeString(file.toPath(), jarText, StandardCharsets.UTF_8);
            done.add(file.getName() + " de la 1.x remplacé par celui de la 2.0");
        }
    }

    /** @return vrai si des réglages diffèrent de ceux du jar (fichier réécrit avec eux) */
    static boolean convert(YamlConfiguration old, YamlConfiguration mysql, String jarText, File target) throws IOException, InvalidConfigurationException {
        YamlConfiguration out = new YamlConfiguration();
        out.loadFromString(jarText);
        Change change = new Change(out);

        change.value(old, "Clicker holograms", "hologrammes.clicker");
        change.value(old, "Sounds", "sons.actif");
        change.value(old, "Maximum CPS", "clics.max-par-seconde");
        change.value(old, "AFK cooldown seconds", "clics.anti-afk-secondes");
        change.value(old, "Cookie amount", "explosion.cookies");
        change.value(old, "Explosion duration seconds", "explosion.duree-secondes");
        change.value(old, "Cookies per cookie multiplier", "explosion.multiplicateur");
        change.value(old, "Golden duration seconds", "cookie-dore.duree-secondes");
        change.value(old, "Per click multiplier", "cookie-dore.multiplicateur");
        // La 1.x tirait nextInt(range) <= 1 : deux chances sur « range »
        if (old.contains("Cookie explosion range")) {
            change.set("explosion.chance", Math.max(1, old.getInt("Cookie explosion range") / 2));
        }
        if (old.contains("Golden cookie range")) {
            change.set("cookie-dore.chance", Math.max(1, old.getInt("Golden cookie range") / 2));
        }
        if (old.getBoolean("MySQL", false)) {
            change.set("base-de-donnees.type", "mysql");
            change.set("base-de-donnees.mysql.hote", mysql.getString("Host", "localhost"));
            change.set("base-de-donnees.mysql.port", Integer.parseInt(String.valueOf(mysql.get("Port", "3306")).trim()));
            change.set("base-de-donnees.mysql.nom", mysql.getString("Database", "CookieClicker"));
            change.set("base-de-donnees.mysql.utilisateur", mysql.getString("User", "root"));
            change.set("base-de-donnees.mysql.mot-de-passe", String.valueOf(mysql.get("Password", "")));
        }

        change.section("boutique.ameliorations", upgrades(old.getConfigurationSection("default_shop")));
        change.section("boutique.vipelite", upgrades(old.getConfigurationSection("premium_shop")));
        change.section("designs.blocs", blocks(old.getConfigurationSection("block_design_shop")));
        change.section("designs.effets", effects(old.getConfigurationSection("hit_particle_design_shop")));

        ConfigurationSection sounds = old.getConfigurationSection("sounds");
        if (sounds != null) {
            for (Map.Entry<String, String> sound : SOUNDS.entrySet()) {
                ConfigurationSection entry = sounds.getConfigurationSection(sound.getKey());
                if (entry == null) {
                    continue;
                }
                String path = "sons." + sound.getValue();
                change.set(path + ".son", soundKey(entry.getString("type", "")));
                change.set(path + ".volume", entry.getDouble("volume", 1.0));
                change.set(path + ".hauteur", entry.getDouble("pitch", 1.0));
            }
        }
        ConfigurationSection network = old.getConfigurationSection("reseau");
        if (network != null) {
            for (Map.Entry<String, Object> entry : network.getValues(true).entrySet()) {
                if (!(entry.getValue() instanceof ConfigurationSection)) {
                    change.set("reseau." + entry.getKey(), entry.getValue());
                }
            }
        }
        if (change.changed) {
            out.save(target);
        } else {
            Files.writeString(target.toPath(), jarText, StandardCharsets.UTF_8);
        }
        return change.changed;
    }

    // ENTITY_ITEM_PICKUP (énumération de la 4.8) vers entity.item.pickup ; une clé de son reste telle quelle
    private static String soundKey(String type) {
        String trimmed = type == null ? "" : type.trim();
        if (trimmed.isEmpty() || trimmed.contains(".") || trimmed.contains(":")) {
            return trimmed;
        }
        return trimmed.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    private static Map<String, Object> upgrades(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (section == null) {
            return null;
        }
        for (String id : section.getKeys(false)) {
            String p = id + ".";
            long gain = section.getLong(p + "add_clicks");
            result.put(p + "materiau", section.getString(p + "item_type", "COOKIE"));
            result.put(p + "prix", section.getLong(p + "base_price"));
            result.put(p + "hausse", section.getLong(p + "price_step", 115 * gain));
            result.put(p + "gain", gain);
        }
        return result;
    }

    private static Map<String, Object> blocks(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            String p = id + ".";
            String block = section.getString(p + "block_type", section.getString(p + "item_type", "STONE"));
            String texture = section.getString(p + "skull_texture", "");
            // La tête de cookie est maintenant ce que voit tout joueur sans design : elle n'est plus à vendre
            if (texture != null && texture.trim().equals(Appearance.DEFAULT_TEXTURE)) {
                continue;
            }
            result.put(p + "nom", section.getString(p + "display_name", "?"));
            if (block.equalsIgnoreCase("REAL_PLAYER_HEAD")) {
                result.put(p + "bloc", "TETE_JOUEUR");
            } else {
                result.put(p + "bloc", block.equalsIgnoreCase("COOKIE_HEAD") ? "PLAYER_HEAD" : block);
                if (texture != null && !texture.isBlank()) {
                    result.put(p + "texture", texture);
                }
            }
            result.put(p + "prix", section.getLong(p + "base_price"));
        }
        return result;
    }

    private static Map<String, Object> effects(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            String p = id + ".";
            String first = section.getString(p + "particle_type", "CLOUD");
            String second = section.getString(p + "second_particle_type", first);
            String animation = section.getString(p + "animation_type", "SPIRAL").toUpperCase(Locale.ROOT);
            result.put(p + "nom", section.getString(p + "display_name", "?"));
            result.put(p + "icone", section.getString(p + "item_type", "BLAZE_POWDER"));
            result.put(p + "animation", ANIMATIONS.getOrDefault(animation, "SPIRALE"));
            result.put(p + "particule", first);
            if (!Objects.equals(first, second)) {
                result.put(p + "particule-secondaire", second);
            }
            if (section.getBoolean(p + "colored_dust", false)) {
                result.put(p + "couleur", String.format("#%02X%02X%02X", section.getInt(p + "color.red") & 0xFF,
                        section.getInt(p + "color.green") & 0xFF, section.getInt(p + "color.blue") & 0xFF));
            }
            result.put(p + "prix", section.getLong(p + "base_price"));
        }
        return result;
    }

    /** Écrit une valeur dans le nouveau fichier seulement si elle diffère de celle du jar, et le note. */
    private static final class Change {
        private final YamlConfiguration out;
        private boolean changed;

        Change(YamlConfiguration out) {
            this.out = out;
        }

        void value(YamlConfiguration old, String oldPath, String newPath) {
            if (old.contains(oldPath)) {
                set(newPath, old.get(oldPath));
            }
        }

        void set(String path, Object value) {
            if (!same(out.get(path), value)) {
                out.set(path, value);
                changed = true;
            }
        }

        void section(String path, Map<String, Object> values) {
            if (values == null) {
                return;
            }
            ConfigurationSection current = out.getConfigurationSection(path);
            Map<String, Object> existing = new LinkedHashMap<>();
            if (current != null) {
                current.getValues(true).forEach((key, value) -> {
                    if (!(value instanceof ConfigurationSection)) {
                        existing.put(key, value);
                    }
                });
            }
            boolean equal = existing.size() == values.size()
                    && values.entrySet().stream().allMatch(entry -> same(existing.get(entry.getKey()), entry.getValue()));
            if (equal) {
                return;
            }
            out.set(path, null);
            values.forEach((key, value) -> out.set(path + "." + key, value));
            changed = true;
        }

        private static boolean same(Object a, Object b) {
            if (a instanceof Number x && b instanceof Number y) {
                return Double.compare(x.doubleValue(), y.doubleValue()) == 0;
            }
            return Objects.equals(a == null ? null : a.toString(), b == null ? null : b.toString());
        }
    }
}
