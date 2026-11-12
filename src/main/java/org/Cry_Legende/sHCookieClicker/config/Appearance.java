package org.Cry_Legende.sHCookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Réglages de l'objet qui flotte au-dessus du clicker (section « apparence » de config.yml).
 *
 * @param texture        tête affichée quand le joueur n'a choisi aucun design
 * @param radius         distance d'affichage, en blocs
 * @param height         hauteur du centre de l'objet au-dessus du centre du bloc du clicker, en blocs
 * @param headSize       taille d'une tête (1 : la taille d'une tête portée par un mannequin)
 * @param blockSize      taille d'un bloc, en blocs
 * @param revolutionTicks durée d'un tour sur lui-même, en ticks
 * @param hitWidth       largeur de la zone de clic, en blocs
 * @param hitHeight      hauteur de la zone de clic, en blocs
 * @param bob            amplitude de la montée et de la descente, en blocs
 * @param bobTicks       durée d'une montée et d'une descente, en ticks
 * @param stepTicks      un envoi tous les {@code stepTicks} ticks ; le client lisse le mouvement entre deux envois
 * @param popGrow        taille gagnée au sommet d'un bond (0,2 : 20 % de plus)
 * @param popJump        hauteur gagnée au sommet d'un bond, en blocs
 */
public record Appearance(String texture, int radius, double height, float headSize, float blockSize,
                         float hitWidth, float hitHeight, int revolutionTicks, double bob, int bobTicks, int stepTicks, double popGrow, double popJump) {

    static final String DEFAULT_TEXTURE = "http://textures.minecraft.net/texture/605ef8819fb6a92148249ff48c3914e92c273e7874523ff7930b9f5cc93ceaae";

    static Appearance parse(ConfigurationSection section, List<String> warn) {
        if (section == null) {
            section = new org.bukkit.configuration.file.YamlConfiguration();
        }
        String texture = section.getString("tete-par-defaut", DEFAULT_TEXTURE);
        if (texture == null || texture.isBlank()) {
            warn.add("apparence.tete-par-defaut : vide, la tête du jar s'applique");
            texture = DEFAULT_TEXTURE;
        }
        return new Appearance(
                texture.trim(),
                Math.max(8, section.getInt("rayon", 40)),
                clamp(section.getDouble("hauteur", 0.5), -2, 6),
                (float) clamp(section.getDouble("taille-tete", 1.0), 0.1, 6),
                (float) clamp(section.getDouble("taille-bloc", 0.75), 0.1, 3),
                (float) clamp(section.getDouble("zone-de-clic.largeur", 1.6), 0.3, 8),
                (float) clamp(section.getDouble("zone-de-clic.hauteur", 1.6), 0.3, 8),
                ticks(section.getDouble("repos.tour-secondes", 8), 160),
                clamp(section.getDouble("repos.flottement", 0.08), 0, 1),
                ticks(section.getDouble("repos.flottement-secondes", 3), 60),
                Math.max(1, Math.min(20, section.getInt("repos.fluidite-ticks", 5))),
                clamp(section.getDouble("coup.gonflement", 0.2), 0, 1.5),
                clamp(section.getDouble("coup.bond", 0.12), 0, 1));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // Secondes lues dans le fichier, au moins un tick
    private static int ticks(double seconds, int fallback) {
        return seconds <= 0 ? fallback : Math.max(1, (int) Math.round(seconds * 20));
    }
}
