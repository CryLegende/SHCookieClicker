package de.zillolp.cookieclicker.database;

import com.zaxxer.hikari.HikariDataSource;
import de.zillolp.cookieclicker.CookieClicker;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseConnector {
    private final Logger logger;
    private final HikariDataSource hikariDataSource = new HikariDataSource();
    private final boolean mySQL;
    private boolean isConnected = false;

    public DatabaseConnector(CookieClicker plugin, boolean useMySQL, String filename, String serverName, String port, String databaseName, String user, String password) {
        logger = plugin.getLogger();
        mySQL = useMySQL;
        try {
            if (useMySQL) {
                configureMySQLDataSource(serverName, port, databaseName, user, password);
            } else {
                configureSQLiteDataSource(plugin, filename);
            }
            configureHikariCP();
            testConnection();
        } catch (IllegalStateException | IllegalArgumentException exception) {
            logger.log(Level.SEVERE, "Connexion à la base impossible (configuration) : ", exception);
            isConnected = false;
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Connexion à la base impossible : ", exception);
            isConnected = false;
        }
    }

    private void configureMySQLDataSource(String serverName, String port, String databaseName, String user, String password) {
        hikariDataSource.setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource");
        hikariDataSource.addDataSourceProperty("serverName", serverName);
        hikariDataSource.addDataSourceProperty("port", port);
        hikariDataSource.addDataSourceProperty("databaseName", databaseName);
        hikariDataSource.addDataSourceProperty("user", user);
        hikariDataSource.addDataSourceProperty("password", password);
    }

    private void configureSQLiteDataSource(CookieClicker plugin, String filename) {
        File databaseFile = new File(plugin.getDataFolder(), filename + ".db");
        if (!(databaseFile.exists())) {
            File parentDir = databaseFile.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                logger.log(Level.SEVERE, "Dossier de la base impossible à créer : " + parentDir.getAbsolutePath());
            }
            try {
                if (!(databaseFile.createNewFile())) {
                    logger.log(Level.SEVERE, "Fichier de la base impossible à créer : " + databaseFile.getAbsolutePath());
                }
            } catch (IOException exception) {
                logger.log(Level.SEVERE, "Fichier de la base impossible à créer : " + databaseFile.getAbsolutePath());
            }
        }
        hikariDataSource.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());

        hikariDataSource.addDataSourceProperty("synchronous", "NORMAL");
        hikariDataSource.addDataSourceProperty("cache_size", "10000");
        hikariDataSource.addDataSourceProperty("temp_store", "memory");
        // Un fichier SQLite n'a qu'un seul écrivain : une connexion unique évite « database is locked »
        hikariDataSource.addDataSourceProperty("busy_timeout", "10000");
    }

    private void configureHikariCP() {
        hikariDataSource.setMinimumIdle(mySQL ? 2 : 1);
        hikariDataSource.setMaximumPoolSize(mySQL ? 10 : 1);
        hikariDataSource.setConnectionTimeout(30000);
        hikariDataSource.setIdleTimeout(600000);
        hikariDataSource.setMaxLifetime(1800000);
        hikariDataSource.setLeakDetectionThreshold(60000);
        hikariDataSource.setPoolName("CookieClicker-HikariCP");

        hikariDataSource.setConnectionTestQuery("SELECT 1");
    }

    private void testConnection() {
        try (Connection connection = getConnection()) {
            isConnected = connection.isValid(5);
        } catch (SQLException exception) {
            isConnected = false;
            logger.log(Level.SEVERE, "Test de la connexion à la base échoué : ", exception);
        }
    }

    public void close() {
        if (hikariDataSource.isClosed()) {
            return;
        }
        hikariDataSource.close();
    }

    public boolean isMySQL() {
        return mySQL;
    }

    public boolean hasConnection() {
        return isConnected && !hikariDataSource.isClosed();
    }

    public Connection getConnection() throws SQLException {
        if (hikariDataSource.isClosed()) {
            throw new SQLException("La connexion à la base est déjà fermée");
        }
        return hikariDataSource.getConnection();
    }
}
