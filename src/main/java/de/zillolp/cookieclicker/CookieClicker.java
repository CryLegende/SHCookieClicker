package de.zillolp.cookieclicker;

import de.zillolp.cookieclicker.commands.maincommands.CookieClickerCommand;
import de.zillolp.cookieclicker.commands.subcommands.*;
import de.zillolp.cookieclicker.config.ConfigManager;
import de.zillolp.cookieclicker.config.customconfigs.*;
import de.zillolp.cookieclicker.custominventories.CustomInventory;
import de.zillolp.cookieclicker.database.DatabaseConnector;
import de.zillolp.cookieclicker.database.DatabaseManager;
import de.zillolp.cookieclicker.enums.CustomInventoryType;
import de.zillolp.cookieclicker.handler.ClickerHandler;
import de.zillolp.cookieclicker.interfaces.ItemBuilder;
import de.zillolp.cookieclicker.interfaces.PacketReader;
import de.zillolp.cookieclicker.listener.*;
import de.zillolp.cookieclicker.listener.inventories.*;
import de.zillolp.cookieclicker.manager.*;
import de.zillolp.cookieclicker.network.NetworkCookies;
import de.zillolp.cookieclicker.network.NetworkLink;
import de.zillolp.cookieclicker.network.ShNetworkLink;
import de.zillolp.cookieclicker.placeholder.PlaceholderListener;
import de.zillolp.cookieclicker.profiles.ClickerInventoryProfile;
import de.zillolp.cookieclicker.runnables.*;
import de.zillolp.cookieclicker.utils.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class CookieClicker extends JavaPlugin {
    private final ArrayList<CustomUpdater> customUpdaters = new ArrayList<>();
    private PluginConfig pluginConfig;
    private LanguageConfig languageConfig;
    private LocationConfig locationConfig;
    private MySQLConfig mySQLConfig;
    private VersionManager versionManager;
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
    private ItemBuilder itemBuilder;
    private ClickerHandler clickerHandler;
    private PacketReader packetReader;
    private StatsWallUpdater statsWallUpdater;
    private ResetTimerUpdater resetTimerUpdater;
    // Non nul seulement avec SHNetwork : la progression est dans la base partagée
    private NetworkCookies networkCookies;

    // Version SH : reprend les données de l'ancien plugin (plugins/CookieClicker : base, positions, configs) au
    // premier démarrage. Copie seulement, l'ancien dossier reste en place pour un retour arrière.
    @Override
    public void onLoad() {
        File dataFolder = getDataFolder();
        File oldFolder = new File(dataFolder.getParentFile(), "CookieClicker");
        if (dataFolder.exists() || !(oldFolder.isDirectory())) {
            return;
        }
        Path oldPath = oldFolder.toPath();
        try (Stream<Path> paths = Files.walk(oldPath)) {
            for (Path source : (Iterable<Path>) paths::iterator) {
                Path target = dataFolder.toPath().resolve(oldPath.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
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
        if (!(checkCompatibility())) {
            return;
        }
        registerManager();
        reflectionUtil = new ReflectionUtil(this);
        clickerHandler = new ClickerHandler(this);
        itemBuilder = versionManager.getItemBuilder();
        packetReader = versionManager.getPacketReader();
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
        if ((!(versionManager.checkVersion())) || (!(databaseConnector.hasConnection()))) {
            return;
        }
        stopUpdaters();
        closeInventories();
        unloadPlayers();
        databaseConnector.close();
    }

    private void registerConfigs() {
        configManager = new ConfigManager(this);
        pluginConfig = configManager.getPluginConfig();
        languageConfig = configManager.getLanguageConfig();
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

    private void connectDatabase() {
        if (connectNetworkDatabase()) {
            return;
        }
        FileConfiguration mysqlConfiguration = mySQLConfig.getFileConfiguration();
        String serverName = mysqlConfiguration.getString("Host", "localhost");
        String port = mysqlConfiguration.getString("Port", "25565");
        String databaseName = mysqlConfiguration.getString("Database", "cookieclicker");
        String user = mysqlConfiguration.getString("User", "root");
        String password = mysqlConfiguration.getString("Password", "123+");
        databaseConnector = new DatabaseConnector(this, pluginConfig.getFileConfiguration().getBoolean("MySQL", false), "cookieclicker", serverName, port, databaseName, user, password);
    }

    private boolean checkCompatibility() {
        Logger logger = getLogger();
        versionManager = new VersionManager(this);
        boolean isConnected = false;
        boolean isVersion = versionManager.checkVersion();
        if (!(isVersion)) {
            for (String wrongVersionMessage : versionManager.getWrongVersionMessage()) {
                logger.warning(wrongVersionMessage);
            }
        } else {
            connectDatabase();
            isConnected = databaseConnector.hasConnection();
            if (!(isConnected)) {
                logger.log(Level.SEVERE, "Could not connect to Database!");
                databaseConnector.close();
            }
        }
        boolean isCompatible = isVersion && isConnected;
        if (!(isCompatible)) {
            CookieClickerCommand cookieClickerCommand = new CookieClickerCommand(this);
            PluginCommand cookieClickerPluginCommand = getCommand("shcookieclicker");
            if (cookieClickerPluginCommand != null) {
                cookieClickerPluginCommand.setExecutor(cookieClickerCommand);
            }
        }
        return isCompatible;
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
        pluginManager.registerEvents(new PlayerConnectionListener(this), this);
        pluginManager.registerEvents(new PlayerMovementListener(this), this);
        pluginManager.registerEvents(new SetupListener(this), this);
        pluginManager.registerEvents(new StatsWallListener(this), this);
        pluginManager.registerEvents(new CookieExplosionListener(this), this);
        if (packetReader instanceof Listener clickReader) {
            pluginManager.registerEvents(clickReader, this);
        }
        pluginManager.registerEvents(new HomeInventoryListener(this, CustomInventoryType.HOME), this);
        pluginManager.registerEvents(new DesignInventoryListener(this, CustomInventoryType.DESIGN), this);
        pluginManager.registerEvents(new HitParticleDesignInventoryListener(this, CustomInventoryType.HIT_PARTICLE_DESIGN), this);
        pluginManager.registerEvents(new MenuDesignInventoryListener(this, CustomInventoryType.MENU_DESIGN), this);
        pluginManager.registerEvents(new BlockDesignInventoryListener(this, CustomInventoryType.BLOCK_DESIGN), this);
        pluginManager.registerEvents(new PremiumShopInventoryListener(this, CustomInventoryType.PREMIUM_SHOP), this);
        pluginManager.registerEvents(new ShopInventoryListener(this, CustomInventoryType.SHOP), this);
        if (pluginManager.getPlugin("PlaceholderAPI") != null) {
            new PlaceholderListener(this).register();
        }
    }

    public void loadPlayers() {
        databaseManager.loadProfiles();
        for (Player player : Bukkit.getOnlinePlayers()) {
            clickerPlayerManager.registerPlayer(player);
            packetReader.inject(player);
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

    // Version SH : pas de statistiques bStats, l'identifiant 11733 est celui du plugin original.


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

    public void unloadPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clickerEventManager.deactivateEvents(player.getUniqueId());
            clickerPlayerManager.unregisterPlayer(player);
            packetReader.unInject(player);
        }
    }

    public void closeInventories() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ClickerInventoryProfile clickerInventoryProfile = clickerPlayerManager.getInventoryProfile(player);
            for (CustomInventory customInventory : clickerInventoryProfile.getCustomInventories().values()) {
                Inventory inventory = reflectionUtil.getOpenInventory(player);
                if (inventory != null && inventory == customInventory.getInventory()) {
                    player.closeInventory();
                    break;
                }
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

    public LocationConfig getLocationConfig() {
        return locationConfig;
    }

    public VersionManager getVersionManager() {
        return versionManager;
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

    public ItemBuilder getItemBuilder() {
        return itemBuilder;
    }

    public ClickerHandler getClickerHandler() {
        return clickerHandler;
    }

    public PacketReader getPacketReader() {
        return packetReader;
    }

    public StatsWallUpdater getStatsWallUpdater() {
        return statsWallUpdater;
    }

    public ResetTimerUpdater getResetTimerUpdater() {
        return resetTimerUpdater;
    }
}
