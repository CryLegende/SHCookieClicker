package de.zillolp.cookieclicker.manager;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.config.customconfigs.PluginConfig;
import de.zillolp.cookieclicker.enums.SoundType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Sons du plugin. Ils sont lus dans config.yml (section sounds) sous forme de clés de son (entity.item.pickup,
 * minecraft:entity.item.pickup ou la clé d'un son de pack de ressources) ; les anciens noms d'énumération
 * (ENTITY_ITEM_PICKUP) sont reconnus. Un son inconnu est signalé au chargement et reste muet, jamais remplacé.
 */
public class SoundManager {
    private final CookieClicker plugin;
    private final PluginConfig pluginConfig;
    private volatile boolean useSounds;

    public SoundManager(CookieClicker plugin) {
        pluginConfig = plugin.getPluginConfig();
        this.plugin = plugin;
        load();
    }

    public void load() {
        FileConfiguration fileConfiguration = pluginConfig.getFileConfiguration();
        useSounds = fileConfiguration.getBoolean("Sounds", true);
        for (SoundType soundType : SoundType.values()) {
            String root = "sounds." + soundType.name().toLowerCase(Locale.ROOT);
            String name = fileConfiguration.getString(root + ".type", "");
            Key key = resolve(name);
            if (key == null) {
                if (!(name.isBlank())) {
                    plugin.getLogger().warning("config.yml : le son « " + name + " » (" + root + ".type) n'existe pas, il reste muet.");
                }
                soundType.setSound(null);
                continue;
            }
            float volume = (float) fileConfiguration.getDouble(root + ".volume", 1.0);
            float pitch = (float) fileConfiguration.getDouble(root + ".pitch", 1.0);
            soundType.setSound(Sound.sound(key, Sound.Source.MASTER, volume, pitch));
        }
    }

    /** Clé d'un son vanilla ou de pack de ressources, ou null si le nom est inconnu. */
    static Key resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = lower.contains(":") ? NamespacedKey.fromString(lower) : NamespacedKey.minecraft(lower);
        if (key == null) {
            return null;
        }
        if (Registry.SOUNDS.get(key) != null) {
            return key;
        }
        if (!(key.getNamespace().equals(NamespacedKey.MINECRAFT))) {
            // Son d'un pack de ressources : le serveur ne peut pas le vérifier
            return key;
        }
        // Ancien nom d'énumération : ENTITY_ITEM_PICKUP, BLOCK_NOTE_BLOCK_BASS (les points et les soulignés diffèrent)
        String flat = key.getKey().replace('.', '_');
        for (org.bukkit.Sound candidate : Registry.SOUNDS) {
            if (candidate.getKey().getKey().replace('.', '_').equals(flat)) {
                return candidate.getKey();
            }
        }
        return null;
    }

    public void playSound(Player player, SoundType soundType) {
        Sound sound = soundType.getSound();
        if (!(useSounds) || sound == null || player == null) {
            return;
        }
        player.playSound(sound);
    }
}
