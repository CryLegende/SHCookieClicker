package org.Cry_Legende.sHCookieClicker.config;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Sons du plugin (section sons de config.yml), chacun lié à une action du joueur. */
public final class SoundSet {

    public enum Action {
        CLIC("clic"),
        CLIC_REFUSE("clic-refuse"),
        OUVERTURE("ouverture"),
        NAVIGATION("navigation"),
        RETOUR("retour"),
        VIPELITE("vipelite"),
        VIPELITE_REFUSE("vipelite-refuse"),
        ACHAT("achat"),
        ACHAT_REFUSE("achat-refuse"),
        DESIGN_CHOISI("design-choisi"),
        DESIGN_RETIRE("design-retire"),
        STATS("stats"),
        COOKIE_DORE("cookie-dore"),
        EXPLOSION("explosion");

        private final String key;

        Action(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    private final boolean enabled;
    private final Map<Action, Sound> sounds;

    private SoundSet(boolean enabled, Map<Action, Sound> sounds) {
        this.enabled = enabled;
        this.sounds = sounds;
    }

    static SoundSet parse(ConfigurationSection section, List<String> warnings) {
        Map<Action, Sound> sounds = new EnumMap<>(Action.class);
        boolean enabled = section == null || section.getBoolean("actif", true);
        for (Action action : Action.values()) {
            String name = section == null ? "" : section.getString(action.key() + ".son", "");
            Key key = resolve(name);
            if (key == null) {
                if (name != null && !name.isBlank()) {
                    warnings.add("son « " + name + " » (sons." + action.key() + ") inconnu : muet");
                }
                continue;
            }
            float volume = (float) section.getDouble(action.key() + ".volume", 1.0);
            float pitch = (float) section.getDouble(action.key() + ".hauteur", 1.0);
            sounds.put(action, Sound.sound(key, Sound.Source.MASTER, volume, pitch));
        }
        return new SoundSet(enabled, sounds);
    }

    /** Clé d'un son du jeu ou d'un pack de ressources ; les anciens noms (ENTITY_ITEM_PICKUP) sont reconnus. */
    static Key resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = lower.contains(":") ? NamespacedKey.fromString(lower) : NamespacedKey.minecraft(lower);
        if (key == null) {
            return null;
        }
        if (!key.getNamespace().equals(NamespacedKey.MINECRAFT) || Registry.SOUNDS.get(key) != null) {
            return key;
        }
        String flat = key.getKey().replace('.', '_');
        for (org.bukkit.Sound candidate : Registry.SOUNDS) {
            NamespacedKey candidateKey = candidate.getKey();
            if (candidateKey.getKey().replace('.', '_').equals(flat)) {
                return candidateKey;
            }
        }
        return null;
    }

    public void play(Player player, Action action) {
        if (!enabled || player == null) {
            return;
        }
        Sound sound = sounds.get(action);
        if (sound != null) {
            player.playSound(sound);
        }
    }
}
