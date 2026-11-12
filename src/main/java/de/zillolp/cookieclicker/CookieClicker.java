package de.zillolp.cookieclicker;

import de.zillolp.cookieclicker.commands.maincommands.CookieClickerCommand;
import de.zillolp.cookieclicker.commands.subcommands.*;
import de.zillolp.cookieclicker.config.ConfigManager;
import de.zillolp.cookieclicker.config.customconfigs.*;
import de.zillolp.cookieclicker.database.DatabaseConnector;
import de.zillolp.cookieclicker.database.DatabaseManager;
import de.zillolp.cookieclicker.handler.ClickerHandler;
import de.zillolp.cookieclicker.listener.*;
import de.zillolp.cookieclicker.manager.*;
import de.zillolp.cookieclicker.menu.Menu;
import de.zillolp.cookieclicker.menu.MenuListener;
import de.zillolp.cookieclicker.network.NetworkCookies;
import de.zillolp.cookieclicker.network.NetworkLink;
import de.zillolp.cookieclicker.network.ShNetworkLink;
import de.zillolp.cookieclicker.placeholder.PlaceholderListener;
import de.zillolp.cookieclicker.profiles.ClickerGameProfile;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import de.zillolp.cookieclicker.runnables.*;
import de.zillolp.cookieclicker.utils.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;

public class CookieClicker extends JavaPlugin {
    private static final String[] LEGACY_DATA_FILES = {"cookieclicker.db", "locations.yml", "mysql.yml"};
    private final ArrayList<CustomUpdater> customUpdaters = new ArrayList<>();
    private PluginConfig pluginConfig;
    private LanguageConfig languageConfig;
    private MenuConfig menuConfig;
    private LocationConfig locationConfig;
    private MySQLConfig mySQLConfig;
    private DatabaseConnector databaseConnector;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private ClickerPlayerManager clickerPlayerManager;
    private CookieClickerManager cookieClickerManager;
    private HologramManager hologramManager;
    private DesignManager designManager;
    private SoundManager soundManager;
    private ClickerEventManager clickerEventManager;
    private ReflectionUtil reflectionUtil;
    private ClickerHandler clickerHandler;
    private StatsWallUpdater statsWallUpdater;
    private ResetTimerUpdater resetTimerUpdater;
    // Non nul seulement avec SHNetwork : la progression est dans la base partagée
    private NetworkCookies networkCookies;

    // Version SH : au premier démarrage, reprend la base, les positions et les identifiants MySQL de l'ancien
    // plugin (plugins/CookieClicker). Copie seulement, l'ancien dossier reste en place.
    @Override
    public void onLoad() {
        File dataFolder = getDataFolder();
        File oldFolder = new File(dataFolder.getParentFile(), "CookieClicker");
        if (dataFolder.exists() || !(oldFolder.isDirectory()) || !(dataFolder.mkdirs())) {
            return;
        }
        try {
            for (String fileName : LEGACY_DATA_FILES) {
                File source = new File(oldFolder, fileName);
                if (source.isFile()) {
                    Files.copy(source.toPath(), new File(dataFolder, fileName).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            getLogger().info("Données de CookieClicker reprises depuis " + oldFolder.getPath());
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Impossible de reprendre les données de " + oldFolder.getPath(), exception);
        }
    }

    @Override
    public void onEnable() {
        registerConfigs();
        if (!(connectDatabase())) {
            return;
        }
        registerManager();
        reflectionUtil = new ReflectionUtil();
        clickerHandler = new ClickerHandler(this);
        registerCommands();
        registerListener(Bukkit.getPluginManager());
        if (networkCookies != null) {
            networkCookies.start();
        }
        loadPlayers();
        registerUpdaters();
    }

    @Override
    public void onDisable() {
        if (databaseConnector == null || !(databaseConnector.hasConnection()) || clickerPlayerManager == null) {
            return;
        }
        stopUpdaters();
        closeInventories();
        unloadPlayers();
        saveProfiles();
        databaseConnector.close();
    }

    private void registerConfigs() {
        configManager = new ConfigManager(this);
        pluginConfig = configManager.getPluginConfig();
        languageConfig = configManager.getLanguageConfig();
        menuConfig = configManager.getMenuConfig();
        locationConfig = configManager.getLocationConfig();
        mySQLConfig = configManager.getMySQLConfig();
    }

    // Mode réseau : SHNetwork installé et 'reseau.actif' vrai. La base partagée remplace alors le fichier SQLite du serveur.
    private boolean connectNetworkDatabase() {
        FileConfiguration configuration = pluginConfig.getFileConfiguration();
        if (!(configuration.getBoolean("reseau.actif", true)) || !(Bukkit.getPluginManager().isPluginEnabled("SHNetwork"))) {
            return false;
        }
        try {
            NetworkLink link = ShNetworkLink.open();
            databaseConnector = new DatabaseConnector(this, true, "cookieclicker",
                    configuration.getString("reseau.base-de-donnees.hote", "127.0.0.1"),
                    String.valueOf(configuration.get("reseau.base-de-donnees.port", "3306")),
                    configuration.getString("reseau.base-de-donnees.nom", "bo_net"),
                    configuration.getString("reseau.base-de-donnees.utilisateur", ""),
                    configuration.getString("reseau.base-de-donnees.mot-de-passe", ""));
            if (!(databaseConnector.hasConnection())) {
                databaseConnector.close();
                getLogger().log(Level.SEVERE, "SHNetwork présent mais base du réseau injoignable, progression locale à ce serveur.");
                return false;
            }
            Duration wait = Duration.ofSeconds(Math.max(1, configuration.getInt("reseau.attente-max-secondes", 10)));
            networkCookies = new NetworkCookies(this, link, wait);
            getLogger().info("SHNetwork détecté : la progression est dans la base partagée.");
            return true;
        } catch (Exception | LinkageError e) {
            getLogger().log(Level.SEVERE, "SHNetwork présent mais inutilisable, progression locale à ce serveur : " + e);
            return false;
        }
    }

    /** Connecte la base. Sans elle, seule la commande répond (elle le dit) et le plugin ne démarre pas le reste. */
    private boolean connectDatabase() {
        if (!(connectNetworkDatabase())) {
            FileConfiguration mysqlConfiguration = mySQLConfig.getFileConfiguration();
            databaseConnector = new DatabaseConnector(this, pluginConfig.getFileConfiguration().getBoolean("MySQL", false), "cookieclicker",
                    mysqlConfiguration.getString("Host", "localhost"),
                    mysqlConfiguration.getString("Port", "3306"),
                    mysqlConfiguration.getString("Database", "cookieclicker"),
                    mysqlConfiguration.getString("User", "root"),
                    mysqlConfiguration.getString("Password", "123+"));
        }
        if (databaseConnector.hasConnection()) {
            return true;
        }
        getLogger().log(Level.SEVERE, "Connexion à la base impossible, le plugin reste inactif.");
        databaseConnector.close();
        PluginCommand pluginCommand = getCommand("shcookieclicker");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(new CookieClickerCommand(this));
        }
        return false;
    }

    private void registerManager() {
        databaseManager = new DatabaseManager(this);
        cookieClickerManager = new CookieClickerManager(this);
        hologramManager = new HologramManager(this);
        clickerPlayerManager = new ClickerPlayerManager(this);
        designManager = new DesignManager(this);
        soundManager = new SoundManager(this);
        clickerEventManager = new ClickerEventManager();
    }

    private void registerCommands() {
        CookieClickerCommand cookieClickerCommand = new CookieClickerCommand(this);
        PluginCommand cookieClickerPluginCommand = getCommand("shcookieclicker");
        if (cookieClickerPluginCommand != null) {
            cookieClickerPluginCommand.setExecutor(cookieClickerCommand);
            cookieClickerCommand.registerSubCommand(new ImportSubCommand(this, "import"));
            cookieClickerCommand.registerSubCommand(new ListSubCommand(this, "list"));
            cookieClickerCommand.registerSubCommand(new ModifySubCommand(this, "modify", "", "add;set;remove", "COOKIES;PER_CLICK;CLICKER_CLICKS"));
            cookieClickerCommand.registerSubCommand(new ReloadSubCommand(this, "reload"));
            cookieClickerCommand.registerSubCommand(new RemoveSubCommand(this, "remove", "clicker;resettimer"));
            cookieClickerCommand.registerSubCommand(new ResetSubCommand(this, "reset"));
            cookieClickerCommand.registerSubCommand(new SetSubCommand(this, "set", "clicker;resettimer"));
            cookieClickerCommand.registerSubCommand(new SetSubCommand(this, "set", "statswall", "alltime;time"));
            cookieClickerCommand.registerSubCommand(new StatsSubCommand(this, "stats"));
        }
    }

    private void registerListener(PluginManager pluginManager) {
        pluginManager.registerEvents(new ClickerListener(this), this);
        pluginManager.registerEvents(new PaperClickReader(this), this);
        pluginManager.registerEvents(new PlayerConnectionListener(this), this);
        pluginManager.registerEvents(new PlayerMovementListener(this), this);
        pluginManager.registerEvents(new SetupListener(this), this);
        pluginManager.registerEvents(new StatsWallListener(this), this);
        pluginManager.registerEvents(new CookieExplosionListener(this), this);
        pluginManager.registerEvents(new MenuListener(), this);
        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new PlaceholderListener(this).register();
        }
    }

    /** Lit la progression de tous les joueurs, puis prépare ceux qui sont déjà connectés (rechargement). */
    public void loadPlayers() {
        clickerPlayerManager.loadProfiles();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            ClickerGameProfile clickerGameProfile = clickerPlayerManager.createGameProfile(uuid);
            clickerPlayerManager.loadPlayerProfile(uuid, player.getName(), false);
            clickerGameProfile.setLoaded(true);
            hologramManager.spawnHolograms(player);
            designManager.sendClickerBlockDesign(player);
        }
    }

    private void registerUpdaters() {
        statsWallUpdater = new StatsWallUpdater(this);
        resetTimerUpdater = new ResetTimerUpdater(this);
        customUpdaters.add(new HologramUpdater(this));
        customUpdaters.add(new PlayerProtectionUpdater(this));
        customUpdaters.add(new StatsUpdater(this, pluginConfig.getCacheSynchronizationTime()));
        customUpdaters.add(new EventActionbarUpdater(this));
        customUpdaters.add(new EventUpdater(this));
        customUpdaters.add(statsWallUpdater);
        customUpdaters.add(resetTimerUpdater);
    }

    private void stopUpdaters() {
        for (CustomUpdater customUpdater : customUpdaters) {
            customUpdater.stop();
        }
    }

    /** Relance la sauvegarde périodique avec l'intervalle « Cache synchronization » du config.yml. */
    public void restartStatsUpdater() {
        customUpdaters.removeIf(updater -> {
            if (updater instanceof StatsUpdater) {
                updater.stop();
                return true;
            }
            return false;
        });
        customUpdaters.add(new StatsUpdater(this, pluginConfig.getCacheSynchronizationTime()));
    }

    /** Retire l'état de jeu des joueurs connectés (arrêt, rechargement). La progression reste en mémoire. */
    public void unloadPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clickerEventManager.deactivateEvents(player.getUniqueId());
            clickerPlayerManager.unregisterPlayer(player);
        }
    }

    /** Écrit tous les joueurs modifiés, sur le fil appelant : seulement à l'arrêt. */
    private void saveProfiles() {
        for (ClickerStatsProfile profile : clickerPlayerManager.getClickerStatsProfiles().values()) {
            if (profile.isDirty()) {
                databaseManager.saveProfile(profile);
            }
        }
    }

    public void closeInventories() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    /** Mode réseau actif, ou null sur un serveur seul. */
    public NetworkCookies getNetwork() {
        return networkCookies;
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

    public DatabaseConnector getDatabaseConnector() {
        return databaseConnector;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ClickerPlayerManager getClickerPlayerManager() {
        return clickerPlayerManager;
    }

    public CookieClickerManager getCookieClickerManager() {
        return cookieClickerManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public DesignManager getDesignManager() {
        return designManager;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public ClickerEventManager getClickerEventManager() {
        return clickerEventManager;
    }

    public ReflectionUtil getReflectionUtil() {
        return reflectionUtil;
    }

    public ClickerHandler getClickerHandler() {
        return clickerHandler;
    }

    public StatsWallUpdater getStatsWallUpdater() {
        return statsWallUpdater;
    }

    public ResetTimerUpdater getResetTimerUpdater() {
        return resetTimerUpdater;
    }
}
