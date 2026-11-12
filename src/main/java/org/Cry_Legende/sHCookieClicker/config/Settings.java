package org.Cry_Legende.sHCookieClicker.config;

import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.Cry_Legende.sHCookieClicker.game.Animation;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** config.yml, lu une fois par chargement : rien n'y est relu pendant le jeu. */
public final class Settings {
    public final boolean mysql;
    public final String mysqlHost;
    public final int mysqlPort;
    public final String mysqlName;
    public final String mysqlUser;
    public final String mysqlPassword;
    public final int saveSeconds;

    public final int maxClicksPerSecond;
    public final long afkMillis;

    public final int goldenChance;
    public final long goldenMillis;
    public final long goldenMultiplier;

    public final int explosionChance;
    public final int explosionCookies;
    public final int explosionSeconds;
    public final long explosionMultiplier;

    public final int rankingSeconds;
    public final boolean clickerHologram;
    public final boolean gainHolograms;
    public final Appearance appearance;
    public final String thousandsSeparator;
    public final SoundSet sounds;

    private final Map<Integer, Upgrade> upgrades;
    private final Map<Integer, Upgrade> premium;
    private final Map<Integer, Designs.Block> blocks;
    private final Map<Integer, Designs.Effect> effects;

    public final boolean networkEnabled;
    public final int networkWaitSeconds;
    public final String networkHost;
    public final int networkPort;
    public final String networkName;
    public final String networkUser;
    public final String networkPassword;

    /** Avertissements du chargement (son inconnu, matériau inconnu...). */
    public final List<String> warnings;

    private Settings(YamlConfiguration file, YamlConfiguration jar) {
        List<String> warn = new ArrayList<>();
        mysql = "mysql".equalsIgnoreCase(file.getString("base-de-donnees.type", "sqlite").trim());
        mysqlHost = file.getString("base-de-donnees.mysql.hote", "localhost");
        mysqlPort = file.getInt("base-de-donnees.mysql.port", 3306);
        mysqlName = file.getString("base-de-donnees.mysql.nom", "CookieClicker");
        mysqlUser = file.getString("base-de-donnees.mysql.utilisateur", "root");
        mysqlPassword = String.valueOf(file.get("base-de-donnees.mysql.mot-de-passe", ""));
        saveSeconds = Math.max(5, file.getInt("base-de-donnees.sauvegarde-secondes", 60));

        maxClicksPerSecond = Math.max(1, file.getInt("clics.max-par-seconde", 12));
        afkMillis = Math.max(0, file.getLong("clics.anti-afk-secondes", 15)) * 1000L;

        goldenChance = Math.max(1, file.getInt("cookie-dore.chance", 5000));
        goldenMillis = Math.max(1, file.getLong("cookie-dore.duree-secondes", 60)) * 1000L;
        goldenMultiplier = Math.max(1, file.getLong("cookie-dore.multiplicateur", 2));

        explosionChance = Math.max(1, file.getInt("explosion.chance", 2500));
        explosionCookies = Math.max(1, Math.min(200, file.getInt("explosion.cookies", 20)));
        explosionSeconds = Math.max(1, file.getInt("explosion.duree-secondes", 30));
        explosionMultiplier = Math.max(0, file.getLong("explosion.multiplicateur", 10));

        rankingSeconds = Math.max(5, file.getInt("classement.rafraichissement-secondes", 30));
        clickerHologram = file.getBoolean("hologrammes.clicker", false);
        gainHolograms = file.getBoolean("hologrammes.gains", true);
        appearance = Appearance.parse(file.getConfigurationSection("apparence"), warn);
        thousandsSeparator = file.getString("affichage.separateur-milliers", " ");
        sounds = SoundSet.parse(file.getConfigurationSection("sons"), warn);

        upgrades = upgrades(section(file, jar, "boutique.ameliorations"), "boutique.ameliorations", warn);
        premium = upgrades(section(file, jar, "boutique.vipelite"), "boutique.vipelite", warn);
        blocks = byPrice(blocks(section(file, jar, "designs.blocs"), warn), Designs.Block::price);
        effects = byPrice(effects(section(file, jar, "designs.effets"), warn), Designs.Effect::price);

        networkEnabled = file.getBoolean("reseau.actif", true);
        networkWaitSeconds = Math.max(1, file.getInt("reseau.attente-max-secondes", 10));
        networkHost = file.getString("reseau.base-de-donnees.hote", "127.0.0.1");
        networkPort = file.getInt("reseau.base-de-donnees.port", 3306);
        networkName = file.getString("reseau.base-de-donnees.nom", "bo_net");
        networkUser = file.getString("reseau.base-de-donnees.utilisateur", "");
        networkPassword = String.valueOf(file.get("reseau.base-de-donnees.mot-de-passe", ""));
        warnings = Collections.unmodifiableList(warn);
    }

    public static Settings parse(YamlConfiguration file, YamlConfiguration jar) {
        return new Settings(file, jar);
    }

    // Une boutique écrite dans le fichier du serveur remplace entièrement celle du jar : un article retiré ne revient pas.
    private static ConfigurationSection section(YamlConfiguration file, YamlConfiguration jar, String path) {
        if (file.contains(path, true) && file.isConfigurationSection(path)) {
            return file.getConfigurationSection(path);
        }
        return jar.getConfigurationSection(path);
    }

    /** Les designs dans l'ordre de leur prix, à prix égal dans l'ordre de leur numéro : c'est l'ordre du menu. */
    static <T> Map<Integer, T> byPrice(Map<Integer, T> designs, java.util.function.ToLongFunction<T> price) {
        List<Map.Entry<Integer, T>> entries = new ArrayList<>(designs.entrySet());
        entries.sort(java.util.Comparator.<Map.Entry<Integer, T>>comparingLong(entry -> price.applyAsLong(entry.getValue()))
                .thenComparing(Map.Entry::getKey));
        Map<Integer, T> sorted = new LinkedHashMap<>();
        entries.forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(sorted);
    }

    private static List<Integer> ids(ConfigurationSection section, String path, List<String> warn) {
        List<Integer> ids = new ArrayList<>();
        if (section == null) {
            return ids;
        }
        for (String key : section.getKeys(false)) {
            try {
                ids.add(Integer.parseInt(key));
            } catch (NumberFormatException exception) {
                warn.add(path + "." + key + " : le numéro doit être un nombre");
            }
        }
        Collections.sort(ids);
        return ids;
    }

    private static Material material(String name, Material fallback, String path, List<String> warn) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        if (material == null) {
            warn.add(path + " : matériau « " + name + " » inconnu");
            return fallback;
        }
        return material;
    }

    private static Map<Integer, Upgrade> upgrades(ConfigurationSection section, String path, List<String> warn) {
        Map<Integer, Upgrade> result = new LinkedHashMap<>();
        for (int id : ids(section, path, warn)) {
            ConfigurationSection item = section.getConfigurationSection(String.valueOf(id));
            if (item == null) {
                continue;
            }
            result.put(id, new Upgrade(id, material(item.getString("materiau"), Material.COOKIE, path + "." + id, warn),
                    Math.max(0, item.getLong("prix")), Math.max(0, item.getLong("hausse")), Math.max(0, item.getLong("gain"))));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<Integer, Designs.Block> blocks(ConfigurationSection section, List<String> warn) {
        Map<Integer, Designs.Block> result = new LinkedHashMap<>();
        for (int id : ids(section, "designs.blocs", warn)) {
            ConfigurationSection item = section.getConfigurationSection(String.valueOf(id));
            if (item == null) {
                continue;
            }
            String blockName = item.getString("bloc", "STONE").trim();
            boolean playerHead = blockName.equalsIgnoreCase("TETE_JOUEUR");
            Material block = playerHead ? Material.PLAYER_HEAD : material(blockName, Material.STONE, "designs.blocs." + id, warn);
            if (!block.isBlock()) {
                warn.add("designs.blocs." + id + " : « " + blockName + " » n'est pas un bloc");
                block = Material.STONE;
            }
            String texture = item.getString("texture", "");
            result.put(id, new Designs.Block(id, item.getString("nom", "?"), Math.max(0, item.getLong("prix")), block,
                    texture == null || texture.isBlank() ? null : texture.trim(), playerHead));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<Integer, Designs.Effect> effects(ConfigurationSection section, List<String> warn) {
        Map<Integer, Designs.Effect> result = new LinkedHashMap<>();
        for (int id : ids(section, "designs.effets", warn)) {
            ConfigurationSection item = section.getConfigurationSection(String.valueOf(id));
            if (item == null) {
                continue;
            }
            String path = "designs.effets." + id;
            Animation animation = Animation.parse(item.getString("animation"));
            if (animation == null) {
                warn.add(path + " : animation « " + item.getString("animation") + " » inconnue, SPIRALE");
                animation = Animation.SPIRALE;
            }
            Particle particle = particle(item.getString("particule", "CLOUD"), path + ".particule", warn);
            Particle second = item.contains("particule-secondaire") ? particle(item.getString("particule-secondaire"), path + ".particule-secondaire", warn) : particle;
            result.put(id, new Designs.Effect(id, item.getString("nom", "?"), Math.max(0, item.getLong("prix")),
                    material(item.getString("icone"), Material.BLAZE_POWDER, path, warn), animation, particle, second,
                    color(item.getString("couleur", ""), path, warn)));
        }
        return Collections.unmodifiableMap(result);
    }

    // Particules envoyées sans donnée, ou avec une donnée que l'effet sait fournir (couleur, puissance)
    private static Particle particle(String name, String path, List<String> warn) {
        try {
            Particle particle = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
            Class<?> data = particle.getDataType();
            if (data == Void.class || data == Float.class || data == Color.class || data == Particle.DustOptions.class) {
                return particle;
            }
            warn.add(path + " : la particule « " + name + " » demande une donnée, CLOUD à la place");
        } catch (IllegalArgumentException | NullPointerException exception) {
            warn.add(path + " : particule « " + name + " » inconnue, CLOUD à la place");
        }
        return Particle.CLOUD;
    }

    private static Color color(String hex, String path, List<String> warn) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        String digits = hex.trim().replace("#", "");
        try {
            return Color.fromRGB(Integer.parseInt(digits, 16) & 0xFFFFFF);
        } catch (NumberFormatException exception) {
            warn.add(path + ".couleur : « " + hex + " » n'est pas une couleur #RRGGBB");
            return null;
        }
    }

    // ---- boutiques

    public Map<Integer, Upgrade> upgrades(ShopKind kind) {
        return kind == ShopKind.PREMIUM ? premium : upgrades;
    }

    public Upgrade upgrade(ShopKind kind, int id) {
        return upgrades(kind).get(id);
    }

    public Map<Integer, ? extends Designs.Design> designs(ShopKind kind) {
        return switch (kind) {
            case BLOCK_DESIGN -> blocks;
            case HIT_PARTICLE_DESIGN -> effects;
            default -> Map.of();
        };
    }

    public Designs.Block block(long id) {
        return blocks.get((int) id);
    }

    public Designs.Effect effect(long id) {
        return effects.get((int) id);
    }
}
