package org.Cry_Legende.sHCookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Réglages du cookie mobile (section « cookie-mobile » de config.yml) : le temps d'un tournoi, l'objet de chaque
 * joueur change de place après quelques clics qui comptent, avec une zone de clic plus petite.
 *
 * @param clicksPerPlace clics qui comptent avant que l'objet change de place
 * @param hitSize        largeur et hauteur de la zone de clic, en blocs
 * @param side           écart maximal de chaque côté de la place habituelle, en blocs
 * @param down           écart maximal en dessous, en blocs
 * @param up             écart maximal au-dessus, en blocs
 * @param minDistance    distance minimale entre deux places de suite, en blocs
 * @param slideTicks     durée du déplacement d'une place à l'autre, en ticks
 */
public record MovingCookie(int clicksPerPlace, float hitSize, double side, double down, double up, double minDistance, int slideTicks) {

    static MovingCookie parse(ConfigurationSection section) {
        if (section == null) {
            section = new org.bukkit.configuration.file.YamlConfiguration();
        }
        return new MovingCookie(
                Math.max(1, Math.min(50, section.getInt("clics-par-place", 3))),
                (float) clamp(section.getDouble("zone-de-clic", 0.8), 0.3, 4),
                clamp(section.getDouble("ecart-cote", 1.0), 0, 3),
                clamp(section.getDouble("ecart-bas", 0.25), 0, 3),
                clamp(section.getDouble("ecart-haut", 0.75), 0, 3),
                clamp(section.getDouble("distance-minimale", 1.0), 0, 6),
                Math.max(1, Math.min(20, section.getInt("deplacement-ticks", 2))));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
