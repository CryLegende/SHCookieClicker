package de.zillolp.cookieclicker.config;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.config.customconfigs.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final CookieClicker plugin;
    private PluginConfig pluginConfig;
    private LanguageConfig languageConfig;
    private MenuConfig menuConfig;
    private LocationConfig locationConfig;
    private MySQLConfig mySQLConfig;

    public ConfigManager(CookieClicker plugin) {
        this.plugin = plugin;
        registerConfigs();
    }

    private void registerConfigs() {
        pluginConfig = new PluginConfig(plugin, "config.yml");
        languageConfig = new LanguageConfig(plugin, "messages.yml");
        menuConfig = new MenuConfig(plugin, "menus.yml");
        locationConfig = new LocationConfig(plugin, "locations.yml");
        mySQLConfig = new MySQLConfig(plugin, "mysql.yml");
    }

    /** Lecture de tous les fichiers de configuration, sans rien appliquer tant que l'un d'eux est illisible. */
    public final class Pending {
        private final Map<CustomConfig, YamlConfiguration> parsed = new LinkedHashMap<>();
        private final List<String> errors = new ArrayList<>();

        /** Une entrée « fichier\nerreur » par fichier illisible. */
        public List<String> errors() {
            return errors;
        }

        /** Remplace les configurations en mémoire ; sans effet si un fichier était illisible. */
        public void apply() {
            if (!errors.isEmpty()) {
                return;
            }
            parsed.forEach(CustomConfig::apply);
        }
    }

    public Pending prepareReload() {
        Pending pending = new Pending();
        for (CustomConfig config : new CustomConfig[]{pluginConfig, languageConfig, menuConfig, locationConfig, mySQLConfig}) {
            try {
                pending.parsed.put(config, config.parse());
            } catch (IOException | InvalidConfigurationException exception) {
                String reason = String.valueOf(exception.getMessage()).strip();
                int newline = reason.indexOf('\n');
                pending.errors.add(config.getFileName() + "\n" + (newline < 0 ? reason : reason.substring(0, newline)));
            }
        }
        return pending;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public LanguageConfig getLanguageConfig() {
        return languageConfig;
    }

    public MenuConfig getMenuConfig() {
        return menuConfig;
    }

    public LocationConfig getLocationConfig() {
        return locationConfig;
    }

    public MySQLConfig getMySQLConfig() {
        return mySQLConfig;
    }
}
