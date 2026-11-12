package de.zillolp.cookieclicker.config.customconfigs;

import de.zillolp.cookieclicker.CookieClicker;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class CustomConfig {
    protected final CookieClicker plugin;
    private final String name;
    protected FileConfiguration fileConfiguration;
    private File file;

    public CustomConfig(CookieClicker plugin, String name) {
        this.plugin = plugin;
        this.name = name;
        loadConfiguration();
    }

    /** Chargement au démarrage : un fichier illisible est signalé et la configuration reste vide. */
    public void loadConfiguration() {
        try {
            apply(parse());
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.SEVERE, name + " illisible : " + exception.getMessage());
            apply(fileConfiguration != null ? (YamlConfiguration) fileConfiguration : new YamlConfiguration());
        }
    }

    /** Lit le fichier (créé à partir du jar s'il manque) sans rien appliquer. */
    public YamlConfiguration parse() throws IOException, InvalidConfigurationException {
        file = new File(plugin.getDataFolder(), name);
        if (!(file.exists())) {
            plugin.saveResource(name, true);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    /** Remplace la configuration en mémoire par une lecture réussie. */
    public void apply(YamlConfiguration yaml) {
        fileConfiguration = yaml;
        if (usesJarDefaults()) {
            loadJarDefaults();
        }
    }

    /** Une clé absente du fichier du serveur (ajoutée par une mise à jour) prend sa valeur dans le fichier du jar. */
    protected boolean usesJarDefaults() {
        return true;
    }

    private void loadJarDefaults() {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            // Valeur par défaut d'une clé absente d'une section présente : lue par setDefaults
            fileConfiguration.setDefaults(defaults);
            // Section entière absente du fichier du serveur : copiée en mémoire (une section qui n'existe que dans les
            // valeurs par défaut est vue vide par getKeys), le fichier du serveur n'est pas modifié
            for (String key : defaults.getKeys(false)) {
                if (fileConfiguration.contains(key, true)) {
                    continue;
                }
                Object value = defaults.get(key);
                if (value instanceof ConfigurationSection section) {
                    for (String path : section.getKeys(true)) {
                        Object leaf = section.get(path);
                        if (!(leaf instanceof ConfigurationSection)) {
                            fileConfiguration.set(key + "." + path, leaf);
                        }
                    }
                } else {
                    fileConfiguration.set(key, value);
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning(name + " du jar illisible : " + exception.getMessage());
        }
    }

    public String getFileName() {
        return name;
    }

    protected void save() {
        try {
            fileConfiguration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Écriture de " + file.getName() + " impossible : " + exception);
        }
    }

    public String[] getConfigurationSection(String sectionName) {
        if (!(fileConfiguration.get(sectionName) instanceof ConfigurationSection configurationSection)) {
            return new String[0];
        }
        return configurationSection.getKeys(false).toArray(new String[0]);
    }

    public FileConfiguration getFileConfiguration() {
        return fileConfiguration;
    }
}
