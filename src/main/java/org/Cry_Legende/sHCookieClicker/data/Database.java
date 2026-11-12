package org.Cry_Legende.sHCookieClicker.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Base du Cookie Clicker : fichier SQLite du serveur, MySQL, ou base partagée du mode réseau. Les tables et leurs
 * colonnes sont celles de CookieClicker (cookieclicker_players, cookieclicker_shops, cookieclicker_migrations) :
 * la base d'un serveur se lit telle quelle.
 */
public final class Database implements AutoCloseable {
    public static final String PLAYERS = "cookieclicker_players";
    public static final String SHOPS = "cookieclicker_shops";
    public static final String MIGRATIONS = "cookieclicker_migrations";
    private static final String COLUMNS = "UUID, NAME, COOKIES, PER_CLICK, CLICKER_CLICKS, BLOCK_DESIGN, PARTICLE_DESIGN, MENU_DESIGN";

    private final HikariDataSource dataSource;
    private final boolean sqlite;
    private final Logger logger;

    private Database(HikariDataSource dataSource, boolean sqlite, Logger logger) {
        this.dataSource = dataSource;
        this.sqlite = sqlite;
        this.logger = logger;
    }

    /** Fichier SQLite : une seule connexion (un seul écrivain, jamais « database is locked »). */
    public static Database sqlite(File file, Logger logger) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("SHCookieClicker-SQLite");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        config.addDataSourceProperty("busy_timeout", "10000");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        return new Database(new HikariDataSource(config), true, logger);
    }

    public static Database mysql(String host, int port, String name, String user, String password, String pool, Logger logger) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(pool);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&rewriteBatchedStatements=true");
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setMaxLifetime(1_800_000);
        return new Database(new HikariDataSource(config), false, logger);
    }

    public boolean isSqlite() {
        return sqlite;
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Crée les tables absentes ; celles qui existent ne sont pas touchées. */
    public void createSchema() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + PLAYERS + "(UUID VARCHAR(64) NOT NULL, NAME VARCHAR(64) NOT NULL,"
                    + " COOKIES BIGINT DEFAULT 0, PER_CLICK BIGINT DEFAULT 0, CLICKER_CLICKS BIGINT DEFAULT 0, BLOCK_DESIGN BIGINT DEFAULT 0,"
                    + " PARTICLE_DESIGN BIGINT DEFAULT 0, MENU_DESIGN BIGINT DEFAULT 0, PRIMARY KEY (UUID))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + SHOPS + "(UUID VARCHAR(64) NOT NULL, SHOP_ID VARCHAR(64) NOT NULL,"
                    + " ID INTEGER NOT NULL, ITEMS_PURCHASED BIGINT NOT NULL, PRIMARY KEY (UUID, SHOP_ID, ID))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + MIGRATIONS + "(NAME VARCHAR(64) NOT NULL PRIMARY KEY)");
        }
    }

    public boolean hasMarker(String marker) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + MIGRATIONS + " WHERE NAME = ?")) {
            statement.setString(1, marker);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    public void addMarker(String marker) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement((sqlite ? "INSERT OR IGNORE INTO " : "INSERT IGNORE INTO ") + MIGRATIONS + "(NAME) VALUES (?)")) {
            statement.setString(1, marker);
            statement.executeUpdate();
        }
    }

    /** Ce que la lecture complète a trouvé. */
    public record LoadResult(int players, int purchaseRows, int playersWithPurchases, int skipped) {
    }

    /** Lit tous les joueurs et leurs achats en deux requêtes. */
    public LoadResult loadAll(ProfileStore store) throws SQLException {
        int players = 0;
        int purchaseRows = 0;
        int skipped = 0;
        java.util.Set<UUID> buyers = new java.util.HashSet<>();
        try (Connection connection = connection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM " + PLAYERS);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Profile profile = read(store, rows);
                    if (profile == null) {
                        skipped++;
                        continue;
                    }
                    store.put(profile);
                    players++;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT UUID, SHOP_ID, ID, ITEMS_PURCHASED FROM " + SHOPS);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID uuid = parse(rows.getString(1));
                    Profile profile = uuid == null ? null : store.get(uuid);
                    ShopKind kind = ShopKind.parse(rows.getString(2));
                    if (profile == null || kind == null) {
                        skipped++;
                        continue;
                    }
                    profile.loadPurchase(kind, rows.getInt(3), rows.getLong(4));
                    purchaseRows++;
                    buyers.add(uuid);
                }
            }
        }
        return new LoadResult(players, purchaseRows, buyers.size(), skipped);
    }

    /** Un joueur relu en base (mode réseau), ou null s'il n'y est pas. */
    public Profile loadOne(ProfileStore store, UUID uuid) throws SQLException {
        try (Connection connection = connection()) {
            Profile profile;
            try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM " + PLAYERS + " WHERE UUID = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return null;
                    }
                    profile = read(store, rows);
                }
            }
            if (profile == null) {
                return null;
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT SHOP_ID, ID, ITEMS_PURCHASED FROM " + SHOPS + " WHERE UUID = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        ShopKind kind = ShopKind.parse(rows.getString(1));
                        if (kind != null) {
                            profile.loadPurchase(kind, rows.getInt(2), rows.getLong(3));
                        }
                    }
                }
            }
            return profile;
        }
    }

    /** UUID d'un joueur de la base d'après son pseudo, sans tenir compte de la casse. */
    public UUID findUuid(String name) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT UUID FROM " + PLAYERS + " WHERE LOWER(NAME) = LOWER(?)")) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? parse(rows.getString(1)) : null;
            }
        }
    }

    private Profile read(ProfileStore store, ResultSet rows) throws SQLException {
        UUID uuid = parse(rows.getString(1));
        if (uuid == null) {
            return null;
        }
        String name = rows.getString(2);
        Profile profile = store.blank(uuid, name == null || name.isEmpty() ? "?" : name);
        profile.load(name == null || name.isEmpty() ? "?" : name, rows.getLong(3), rows.getLong(4), rows.getLong(5),
                rows.getLong(6), rows.getLong(7), rows.getLong(8));
        return profile;
    }

    private UUID parse(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException | NullPointerException exception) {
            logger.warning("UUID illisible dans la base : " + text);
            return null;
        }
    }

    /**
     * Écrit des joueurs en une transaction : la ligne de chacun, et ses achats quand ils ont changé
     * (ceux de la base sont alors remplacés).
     */
    public void save(List<Profile.Row> rows) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        try (Connection connection = connection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement players = connection.prepareStatement("REPLACE INTO " + PLAYERS + "(" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
                 PreparedStatement clear = connection.prepareStatement("DELETE FROM " + SHOPS + " WHERE UUID = ?");
                 PreparedStatement insert = connection.prepareStatement("INSERT INTO " + SHOPS + "(UUID, SHOP_ID, ID, ITEMS_PURCHASED) VALUES (?, ?, ?, ?)")) {
                boolean shops = false;
                for (Profile.Row row : rows) {
                    String uuid = row.uuid().toString();
                    players.setString(1, uuid);
                    players.setString(2, row.name());
                    players.setLong(3, row.cookies());
                    players.setLong(4, row.perClick());
                    players.setLong(5, row.clickerClicks());
                    players.setLong(6, row.blockDesign());
                    players.setLong(7, row.particleDesign());
                    players.setLong(8, row.menuDesign());
                    players.addBatch();
                    if (row.purchases() == null) {
                        continue;
                    }
                    shops = true;
                    clear.setString(1, uuid);
                    clear.addBatch();
                    for (Map.Entry<ShopKind, Map<Integer, Long>> shop : row.purchases().entrySet()) {
                        for (Map.Entry<Integer, Long> item : shop.getValue().entrySet()) {
                            if (item.getValue() <= 0) {
                                continue;
                            }
                            insert.setString(1, uuid);
                            insert.setString(2, shop.getKey().name());
                            insert.setInt(3, item.getKey());
                            insert.setLong(4, item.getValue());
                            insert.addBatch();
                        }
                    }
                }
                players.executeBatch();
                if (shops) {
                    clear.executeBatch();
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
