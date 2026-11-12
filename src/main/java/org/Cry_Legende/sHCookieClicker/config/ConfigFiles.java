package org.Cry_Legende.sHCookieClicker.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Fichiers de configuration du dossier du plugin, chacun complété par celui du jar. */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    /** Fichier du serveur et fichier du jar (ses valeurs par défaut). */
    public record Loaded(YamlConfiguration file, YamlConfiguration jar) {
    }

    public static String jarText(JavaPlugin plugin, String name) throws IOException {
        try (InputStream input = plugin.getResource(name)) {
            if (input == null) {
                throw new IOException(name + " absent du jar");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static YamlConfiguration jar(JavaPlugin plugin, String name) throws IOException, InvalidConfigurationException {
        YamlConfiguration jar = new YamlConfiguration();
        jar.loadFromString(jarText(plugin, name));
        return jar;
    }

    /** Lit un fichier (créé depuis le jar s'il manque). Une erreur de syntaxe remonte telle quelle. */
    public static Loaded load(JavaPlugin plugin, String name) throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration jar = jar(plugin, name);
        YamlConfiguration config = new YamlConfiguration();
        config.load(file);
        config.setDefaults(jar);
        return new Loaded(config, jar);
    }
}
