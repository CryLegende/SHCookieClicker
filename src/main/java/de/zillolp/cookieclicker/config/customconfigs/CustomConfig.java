package de.zillolp.cookieclicker.config.customconfigs;

import de.zillolp.cookieclicker.CookieClicker;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
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
    }

    public String getFileName() {
        return name;
    }

    protected void save() {
        try {
            fileConfiguration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + file.getName() + ": " + exception);
        }
    }

    public String[] getConfigurationSection(String sectionName) {
        if (!(fileConfiguration.contains(sectionName))) {
            return new String[0];
        }
        ConfigurationSection configurationSection = fileConfiguration.getConfigurationSection(sectionName);
        if (configurationSection == null) {
            return new String[0];
        }
        return configurationSection.getKeys(false).toArray(new String[0]);
    }

    public FileConfiguration getFileConfiguration() {
        return fileConfiguration;
    }
}
