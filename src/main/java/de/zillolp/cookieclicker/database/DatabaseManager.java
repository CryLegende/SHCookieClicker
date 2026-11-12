package de.zillolp.cookieclicker.database;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.config.customconfigs.PluginConfig;
import de.zillolp.cookieclicker.enums.ShopType;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Accès à la base (SQLite du serveur, MySQL ou base du réseau). Tous les joueurs sont lus d'un coup au démarrage ;
 * ensuite la mémoire fait foi et la base n'est touchée que pour écrire, hors du fil principal.
 *
 * <p>cookieclicker_shops.ITEMS_PURCHASED est le nombre d'achats : le nombre de fois qu'une amélioration a été
 * prise, 1 pour un design acheté.</p>
 */
public class DatabaseManager {
    private static final String PLAYERS_COLUMNS = "UUID, NAME, COOKIES, PER_CLICK, CLICKER_CLICKS, BLOCK_DESIGN, PARTICLE_DESIGN, MENU_DESIGN";
    private final CookieClicker plugin;
    private final Logger logger;
    private final DatabaseConnector databaseConnector;
    private final String playersTable = "cookieclicker_players";
    private final String shopsTable = "cookieclicker_shops";

    public DatabaseManager(CookieClicker plugin) {
        this.plugin = plugin;
        logger = plugin.getLogger();
        databaseConnector = plugin.getDatabaseConnector();
        initialize();
    }

    private void initialize() {
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement players = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + playersTable +
                     "(UUID VARCHAR(64) NOT NULL, NAME VARCHAR(64) NOT NULL, COOKIES BIGINT DEFAULT 0, PER_CLICK BIGINT DEFAULT 0, CLICKER_CLICKS  BIGINT DEFAULT 0," +
                     " BLOCK_DESIGN  BIGINT DEFAULT 0, PARTICLE_DESIGN  BIGINT DEFAULT 0, MENU_DESIGN  BIGINT DEFAULT 0, PRIMARY KEY (UUID))");
             PreparedStatement shops = connection.prepareStatement("CREATE TABLE IF NOT EXISTS " + shopsTable +
                     "(UUID VARCHAR(64) NOT NULL, SHOP_ID VARCHAR(64) NOT NULL, ID INTEGER NOT NULL, ITEMS_PURCHASED  BIGINT NOT NULL, PRIMARY KEY (UUID, SHOP_ID, ID))")) {
            players.executeUpdate();
            shops.executeUpdate();
            if (!(databaseConnector.isMySQL())) {
                LegacyMigration.run(connection, plugin.getPluginConfig(), logger);
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Création des tables impossible : ", exception);
        }
    }

    /** Reprise de la boutique de CookieClicker 4.8 dans un fichier SQLite (voir LegacyMigration), si elle n'est pas déjà faite. */
    public static void migrateLegacyShops(Connection sqliteConnection, PluginConfig pluginConfig, Logger logger) throws SQLException {
        LegacyMigration.run(sqliteConnection, pluginConfig, logger);
    }

    /** Tous les joueurs de la base, avec leurs achats. */
    public Map<UUID, ClickerStatsProfile> loadAllProfiles() {
        Map<UUID, ClickerStatsProfile> profiles = new HashMap<>();
        try (Connection connection = databaseConnector.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT " + PLAYERS_COLUMNS + " FROM " + playersTable);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ClickerStatsProfile profile = readProfile(rows);
                    if (profile != null) {
                        profiles.put(profile.getUuid(), profile);
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT UUID, SHOP_ID, ID, ITEMS_PURCHASED FROM " + shopsTable);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ClickerStatsProfile profile = profiles.get(parseUuid(rows.getString("UUID")));
                    if (profile != null) {
                        readPurchase(profile, rows);
                    }
                }
            }
            profiles.values().forEach(ClickerStatsProfile::markClean);
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Lecture des joueurs impossible : ", exception);
        }
        return profiles;
    }

    /** Un joueur de la base avec ses achats, ou null s'il n'y est pas. */
    public ClickerStatsProfile loadProfile(UUID uuid) {
        try (Connection connection = databaseConnector.getConnection()) {
            ClickerStatsProfile profile;
            try (PreparedStatement statement = connection.prepareStatement("SELECT " + PLAYERS_COLUMNS + " FROM " + playersTable + " WHERE UUID = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!(rows.next())) {
                        return null;
                    }
                    profile = readProfile(rows);
                }
            }
            if (profile == null) {
                return null;
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT UUID, SHOP_ID, ID, ITEMS_PURCHASED FROM " + shopsTable + " WHERE UUID = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        readPurchase(profile, rows);
                    }
                }
            }
            profile.markClean();
            return profile;
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Lecture du joueur " + uuid + " impossible : ", exception);
            return null;
        }
    }

    /** Remplace les valeurs en mémoire d'un joueur par celles de la base (mode réseau : l'autre serveur a pu les changer). */
    public void reloadProfile(ClickerStatsProfile profile) {
        ClickerStatsProfile stored = loadProfile(profile.getUuid());
        if (stored == null) {
            return;
        }
        profile.setCookies(stored.getCookies());
        profile.setPerClick(stored.getPerClick());
        profile.setClickerClicks(stored.getClickerClicks());
        profile.setBlockDesign(stored.getBlockDesign());
        profile.setParticleDesign(stored.getParticleDesign());
        profile.setMenuDesign(stored.getMenuDesign());
        profile.clearPurchases();
        stored.getPurchaseSnapshot().forEach((shopType, items) -> items.forEach((id, amount) -> profile.setPurchases(shopType, id, amount)));
        profile.markClean();
    }

    private ClickerStatsProfile readProfile(ResultSet rows) throws SQLException {
        UUID uuid = parseUuid(rows.getString("UUID"));
        if (uuid == null) {
            return null;
        }
        String name = rows.getString("NAME");
        ClickerStatsProfile profile = new ClickerStatsProfile(uuid, name == null || name.isEmpty() ? "?" : name);
        profile.setCookies(rows.getLong("COOKIES"));
        profile.setPerClick(rows.getLong("PER_CLICK"));
        profile.setClickerClicks(rows.getLong("CLICKER_CLICKS"));
        profile.setBlockDesign(rows.getLong("BLOCK_DESIGN"));
        profile.setParticleDesign(rows.getLong("PARTICLE_DESIGN"));
        profile.setMenuDesign(rows.getLong("MENU_DESIGN"));
        return profile;
    }

    private void readPurchase(ClickerStatsProfile profile, ResultSet rows) throws SQLException {
        String shopId = rows.getString("SHOP_ID");
        try {
            profile.setPurchases(ShopType.valueOf(shopId), rows.getInt("ID"), rows.getLong("ITEMS_PURCHASED"));
        } catch (IllegalArgumentException exception) {
            logger.log(Level.WARNING, "Boutique inconnue dans la base : " + shopId);
        }
    }

    private UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException | NullPointerException exception) {
            logger.log(Level.WARNING, "UUID illisible dans la base : " + text);
            return null;
        }
    }

    /** Écrit le joueur et ses achats en une transaction. À appeler hors du fil principal. */
    public void saveProfile(ClickerStatsProfile profile) {
        if (profile == null) {
            return;
        }
        profile.markClean();
        try (Connection connection = databaseConnector.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String uuid = profile.getUuid().toString();
                try (PreparedStatement statement = connection.prepareStatement("REPLACE INTO " + playersTable + "(" + PLAYERS_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setString(1, uuid);
                    statement.setString(2, profile.getName());
                    statement.setLong(3, profile.getCookies());
                    statement.setLong(4, profile.getPerClick());
                    statement.setLong(5, profile.getClickerClicks());
                    statement.setLong(6, profile.getBlockDesign());
                    statement.setLong(7, profile.getParticleDesign());
                    statement.setLong(8, profile.getMenuDesign());
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + shopsTable + " WHERE UUID = ?")) {
                    statement.setString(1, uuid);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + shopsTable + "(UUID, SHOP_ID, ID, ITEMS_PURCHASED) VALUES (?, ?, ?, ?)")) {
                    for (Map.Entry<ShopType, Map<Integer, Long>> shop : profile.getPurchaseSnapshot().entrySet()) {
                        for (Map.Entry<Integer, Long> item : shop.getValue().entrySet()) {
                            if (item.getValue() <= 0) {
                                continue;
                            }
                            statement.setString(1, uuid);
                            statement.setString(2, shop.getKey().name());
                            statement.setInt(3, item.getKey());
                            statement.setLong(4, item.getValue());
                            statement.addBatch();
                        }
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            profile.markDirty();
            logger.log(Level.SEVERE, "Sauvegarde de " + profile.getName() + " impossible : ", exception);
        }
    }

    /** UUID d'un joueur de la base d'après son pseudo, sans tenir compte de la casse. */
    public Optional<UUID> findUuidByName(String name) {
        try (Connection connection = databaseConnector.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT UUID FROM " + playersTable + " WHERE LOWER(NAME) = LOWER(?)")) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return Optional.ofNullable(parseUuid(rows.getString("UUID")));
                }
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Recherche du joueur " + name + " impossible : ", exception);
        }
        return Optional.empty();
    }
}
