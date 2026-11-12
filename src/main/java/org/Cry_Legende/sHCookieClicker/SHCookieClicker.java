package org.Cry_Legende.sHCookieClicker;

import org.Cry_Legende.sHCookieClicker.command.AdminCommand;
import org.Cry_Legende.sHCookieClicker.command.StatsCommand;
import org.Cry_Legende.sHCookieClicker.config.ConfigFiles;
import org.Cry_Legende.sHCookieClicker.config.LegacyFiles;
import org.Cry_Legende.sHCookieClicker.config.MenuLayout;
import org.Cry_Legende.sHCookieClicker.config.Messages;
import org.Cry_Legende.sHCookieClicker.config.Settings;
import org.Cry_Legende.sHCookieClicker.data.Database;
import org.Cry_Legende.sHCookieClicker.data.LegacyShopMigration;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.data.ProfileStore;
import org.Cry_Legende.sHCookieClicker.data.Ranking;
import org.Cry_Legende.sHCookieClicker.data.Saver;
import org.Cry_Legende.sHCookieClicker.game.ClickService;
import org.Cry_Legende.sHCookieClicker.game.ClickerListener;
import org.Cry_Legende.sHCookieClicker.game.ClickerLook;
import org.Cry_Legende.sHCookieClicker.game.Clickers;
import org.Cry_Legende.sHCookieClicker.game.Explosions;
import org.Cry_Legende.sHCookieClicker.game.Feedback;
import org.Cry_Legende.sHCookieClicker.game.Holograms;
import org.Cry_Legende.sHCookieClicker.game.ParticleAnimator;
import org.Cry_Legende.sHCookieClicker.game.Sessions;
import org.Cry_Legende.sHCookieClicker.hook.CookiePlaceholders;
import org.Cry_Legende.sHCookieClicker.menu.Menu;
import org.Cry_Legende.sHCookieClicker.menu.MenuListener;
import org.Cry_Legende.sHCookieClicker.network.NetworkMode;
import org.Cry_Legende.sHCookieClicker.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class SHCookieClicker extends JavaPlugin {
    private static final String FIRST_BOOT_MARKER = "sh-2.0";

    private Thread mainThread;
    private volatile Settings settings;
    private volatile Messages messages;
    private volatile MenuLayout menus;
    private volatile Clickers clickers;

    private Database database;
    private ProfileStore profiles;
    private Saver saver;
    private Ranking ranking;
    private NetworkMode network;
    private final Sessions sessions = new Sessions();
    private Feedback feedback;
    private ClickerLook look;
    private ParticleAnimator particles;
    private Explosions explosions;
    private Holograms holograms;
    private ClickService clicks;
    private BukkitTask rankingTask;
    private boolean ready;

    @Override
    public void onEnable() {
        mainThread = Thread.currentThread();
        getDataFolder().mkdirs();
        convertLegacyFiles();
        List<String> errors = loadConfiguration(true);
        errors.forEach(error -> getLogger().severe(error));

        profiles = new ProfileStore(profile -> saver.enqueue(profile));
        if (!openDatabase()) {
            registerCommands();
            return;
        }
        saver = new Saver(database, getLogger());
        saver.schedule(settings.saveSeconds);
        ranking = new Ranking(profiles);
        ranking.refresh();
        rankingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, ranking::refresh, settings.rankingSeconds * 20L, settings.rankingSeconds * 20L);

        feedback = new Feedback(this);
        look = new ClickerLook(this);
        particles = new ParticleAnimator();
        explosions = new Explosions(this);
        holograms = new Holograms(this);
        clicks = new ClickService(this);

        Bukkit.getPluginManager().registerEvents(new ClickerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(this), this);
        registerCommands();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new CookiePlaceholders(this).register();
        }
        if (network != null) {
            network.start();
        }
        Bukkit.getScheduler().runTaskTimer(this, feedback::tick, 2, 2);
        Bukkit.getScheduler().runTaskTimer(this, particles::tick, 1, 1);
        holograms.start();
        ready = true;
        // Rechargement à chaud : les joueurs déjà là reprennent une session
        for (Player player : Bukkit.getOnlinePlayers()) {
            clicks.join(player);
        }
    }

    @Override
    public void onDisable() {
        ready = false;
        if (rankingTask != null) {
            rankingTask.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
        if (clicks != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                clicks.leave(player, false);
            }
        }
        if (explosions != null) {
            explosions.clear();
        }
        if (holograms != null) {
            holograms.stop();
        }
        if (saver != null) {
            saver.shutdown();
        }
        if (database != null) {
            database.close();
        }
    }

    // ---- démarrage

    private void convertLegacyFiles() {
        try {
            List<String> done = LegacyFiles.migrate(getDataFolder(), ConfigFiles.jarText(this, "config.yml"),
                    ConfigFiles.jarText(this, "messages.yml"), ConfigFiles.jarText(this, "menus.yml"));
            done.forEach(line -> getLogger().info("Passage à la 2.0 : " + line + "."));
        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().log(Level.SEVERE, "Passage des fichiers de la 1.x impossible, ceux du jar s'appliquent", exception);
        }
    }

    /**
     * Lit config.yml, messages.yml, menus.yml et locations.yml. Au démarrage, un fichier illisible est remplacé par
     * les valeurs du jar ; au rechargement, rien n'est changé.
     *
     * @return les erreurs, une par fichier illisible
     */
    private List<String> loadConfiguration(boolean boot) {
        List<String> errors = new ArrayList<>();
        Settings newSettings = null;
        Messages newMessages = null;
        MenuLayout newMenus = null;
        Clickers newClickers = null;
        try {
            ConfigFiles.Loaded config = ConfigFiles.load(this, "config.yml");
            newSettings = Settings.parse(config.file(), config.jar());
        } catch (IOException | InvalidConfigurationException exception) {
            errors.add("config.yml\n" + exception.getMessage());
        }
        try {
            newMessages = new Messages(ConfigFiles.load(this, "messages.yml").file());
        } catch (IOException | InvalidConfigurationException exception) {
            errors.add("messages.yml\n" + exception.getMessage());
        }
        try {
            newMenus = new MenuLayout(ConfigFiles.load(this, "menus.yml").file());
        } catch (IOException | InvalidConfigurationException exception) {
            errors.add("menus.yml\n" + exception.getMessage());
        }
        try {
            File file = new File(getDataFolder(), "locations.yml");
            YamlConfiguration locations = new YamlConfiguration();
            if (file.isFile()) {
                locations.load(file);
            }
            newClickers = Clickers.load(locations);
        } catch (IOException | InvalidConfigurationException exception) {
            errors.add("locations.yml\n" + exception.getMessage());
        }
        if (!errors.isEmpty() && !boot) {
            return errors;
        }
        try {
            if (newSettings == null) {
                newSettings = Settings.parse(ConfigFiles.jar(this, "config.yml"), ConfigFiles.jar(this, "config.yml"));
            }
            if (newMessages == null) {
                newMessages = new Messages(ConfigFiles.jar(this, "messages.yml"));
            }
            if (newMenus == null) {
                newMenus = new MenuLayout(ConfigFiles.jar(this, "menus.yml"));
            }
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Fichiers du jar illisibles", exception);
        }
        if (newClickers == null) {
            newClickers = Clickers.load(new YamlConfiguration());
        }
        newSettings.warnings.forEach(warning -> getLogger().warning("config.yml : " + warning + "."));
        settings = newSettings;
        messages = newMessages;
        menus = newMenus;
        clickers = newClickers;
        return errors;
    }

    /** Base du mode réseau (SHNetwork), sinon SQLite ou MySQL. Faux si aucune n'est joignable. */
    private boolean openDatabase() {
        if (settings.networkEnabled && Bukkit.getPluginManager().isPluginEnabled("SHNetwork")) {
            network = NetworkMode.open(this);
        }
        if (network == null) {
            database = settings.mysql
                    ? Database.mysql(settings.mysqlHost, settings.mysqlPort, settings.mysqlName, settings.mysqlUser, settings.mysqlPassword, "SHCookieClicker-MySQL", getLogger())
                    : Database.sqlite(new File(getDataFolder(), "cookieclicker.db"), getLogger());
        } else {
            database = network.database();
        }
        try {
            long start = System.nanoTime();
            try {
                database.createSchema();
            } catch (SQLException exception) {
                // Base du réseau dont l'utilisateur n'a que les droits de lecture et d'écriture : tables créées à la main (sql/schema.sql)
                getLogger().warning("Tables non créées (" + exception.getMessage() + ") : elles doivent déjà exister.");
            }
            if (database.isSqlite()) {
                migrateLegacyShop(database);
            }
            boolean firstBoot;
            try {
                firstBoot = !database.hasMarker(FIRST_BOOT_MARKER);
                if (firstBoot) {
                    database.addMarker(FIRST_BOOT_MARKER);
                }
            } catch (SQLException exception) {
                firstBoot = false;
            }
            Database.LoadResult result = database.loadAll(profiles);
            long millis = (System.nanoTime() - start) / 1_000_000;
            String summary = Numbers.group(result.players(), " ") + " joueurs, " + Numbers.group(result.purchaseRows(), " ")
                    + " achats (" + Numbers.group(result.playersWithPurchases(), " ") + " joueurs)";
            if (firstBoot) {
                getLogger().info("Passage à la 2.0 : base " + (network != null ? "du réseau" : database.isSqlite() ? "cookieclicker.db" : "MySQL")
                        + " reprise telle quelle (mêmes tables, mêmes colonnes) : " + summary + ", cookies "
                        + Numbers.group(totalCookies(), " ") + ", lignes illisibles " + result.skipped() + ".");
            }
            getLogger().info("Progressions chargées en " + millis + " ms : " + summary + ".");
            return true;
        } catch (SQLException | RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Base du Cookie Clicker injoignable : le clicker reste inactif", exception);
            database.close();
            database = null;
            return false;
        }
    }

    private void migrateLegacyShop(Database sqlite) throws SQLException {
        try (Connection connection = sqlite.connection()) {
            LegacyShopMigration.Result result = LegacyShopMigration.run(connection, LegacyShopMigration.lookup(settings.backgroundIdsByPane()), getLogger());
            if (result != null) {
                getLogger().info("Boutique de CookieClicker 4.8 reprise : " + result.purchaseRows() + " achats de " + result.players()
                        + " joueurs, " + result.menuDesigns() + " fonds de menu, " + result.unreadable() + " prix illisibles.");
            }
        }
    }

    private long totalCookies() {
        long total = 0;
        for (Profile profile : profiles.all()) {
            total = Numbers.add(total, profile.cookies());
        }
        return total;
    }

    private void registerCommands() {
        AdminCommand admin = new AdminCommand(this);
        PluginCommand root = getCommand("shcookieclicker");
        if (root != null) {
            root.setExecutor(admin);
            root.setTabCompleter(admin);
        }
        StatsCommand stats = new StatsCommand(this);
        PluginCommand cookies = getCommand("cookies");
        if (cookies != null) {
            cookies.setExecutor(stats);
            cookies.setTabCompleter(stats);
        }
    }

    // ---- rechargement

    /** /shcookieclicker reload : rien ne change si un fichier est illisible. */
    public List<String> reload() {
        int oldSave = settings.saveSeconds;
        List<String> errors = loadConfiguration(false);
        if (!errors.isEmpty()) {
            return errors;
        }
        if (saver != null && settings.saveSeconds != oldSave) {
            saver.schedule(settings.saveSeconds);
        }
        if (ready) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                    player.closeInventory();
                }
                look.show(player);
            }
            holograms.restart();
            ranking.markChanged();
        }
        return errors;
    }

    // ---- fil principal

    /**
     * Exécute la tâche sur le fil principal, tout de suite si on y est déjà. Avec les mondes en parallèle de
     * DivineMC, les événements d'un joueur arrivent sur le fil de son monde ; les tâches du fil principal ne
     * tournent jamais en même temps que les mondes.
     */
    public void sync(Runnable task) {
        if (Thread.currentThread() == mainThread) {
            task.run();
        } else if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    public boolean isMainThread() {
        return Thread.currentThread() == mainThread;
    }

    // ---- accès

    public boolean isReady() {
        return ready;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public MenuLayout menus() {
        return menus;
    }

    public Clickers clickers() {
        return clickers;
    }

    /** Relit locations.yml après la pose ou le retrait d'un clicker. */
    public void reloadClickers() {
        File file = new File(getDataFolder(), "locations.yml");
        clickers = Clickers.load(YamlConfiguration.loadConfiguration(file));
    }

    public Database database() {
        return database;
    }

    public ProfileStore profiles() {
        return profiles;
    }

    public Saver saver() {
        return saver;
    }

    public Ranking ranking() {
        return ranking;
    }

    public NetworkMode network() {
        return network;
    }

    public Sessions sessions() {
        return sessions;
    }

    public Feedback feedback() {
        return feedback;
    }

    public ClickerLook look() {
        return look;
    }

    public ParticleAnimator particles() {
        return particles;
    }

    public Explosions explosions() {
        return explosions;
    }

    public Holograms holograms() {
        return holograms;
    }

    public ClickService clicks() {
        return clicks;
    }

    /** Nombre écrit pour les textes du plugin (séparateur de config.yml). */
    public String number(long value) {
        return Numbers.group(value, settings.thousandsSeparator);
    }
}
