package org.Cry_Legende.sHCookieClicker.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sur la copie d'une vraie base (-Dcookieclicker.db=chemin, jamais versionnée) : reprise de la boutique 4.8,
 * lecture complète, puis tout réécrit dans une base neuve et comparé ligne à ligne avec l'originale.
 */
class ServerDatabaseTest {
    private static final Logger LOGGER = Logger.getLogger("test");

    @Test
    void migrationLoadAndRoundTrip() throws Exception {
        String source = System.getProperty("cookieclicker.db");
        Assumptions.assumeTrue(source != null && new File(source).isFile(), "pas de base fournie (-Dcookieclicker.db)");
        Path work = Files.createTempDirectory("shcc");
        File copy = work.resolve("cookieclicker.db").toFile();
        Files.copy(Path.of(source), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);

        try (Database database = Database.sqlite(copy, LOGGER)) {
            database.createSchema();
            LegacyShopMigration.Result migrated;
            try (Connection connection = database.connection()) {
                migrated = LegacyShopMigration.run(connection, LOGGER);
            }
            System.out.println("Reprise 4.8 : " + (migrated == null ? "déjà faite" : migrated));
            try (Connection connection = database.connection()) {
                LegacyShopMigration.Result again = LegacyShopMigration.run(connection, LOGGER);
                assertEquals(null, again, "la reprise ne se fait qu'une fois");
            }

            ProfileStore store = new ProfileStore(profile -> {
            });
            long start = System.nanoTime();
            Database.LoadResult result = database.loadAll(store);
            System.out.println("Lecture : " + result + " en " + (System.nanoTime() - start) / 1_000_000 + " ms");
            assertEquals(count(database, "SELECT COUNT(*) FROM cookieclicker_players"), result.players() + result.skipped());
            assertEquals(count(database, "SELECT COUNT(*) FROM cookieclicker_shops"), result.purchaseRows());

            File fresh = work.resolve("neuve.db").toFile();
            try (Database target = Database.sqlite(fresh, LOGGER)) {
                target.createSchema();
                List<Profile.Row> rows = new ArrayList<>();
                for (Profile profile : store.all()) {
                    rows.add(profile.row());
                }
                start = System.nanoTime();
                target.save(rows);
                System.out.println("Écriture de " + rows.size() + " joueurs en " + (System.nanoTime() - start) / 1_000_000 + " ms");
            }
            try (Connection connection = database.connection(); Statement statement = connection.createStatement()) {
                statement.execute("ATTACH DATABASE '" + fresh.getAbsolutePath().replace("'", "''") + "' AS neuve");
                for (String table : List.of("cookieclicker_players", "cookieclicker_shops")) {
                    String columns = table.equals("cookieclicker_players")
                            ? "UUID, NAME, COOKIES, PER_CLICK, CLICKER_CLICKS, BLOCK_DESIGN, PARTICLE_DESIGN, MENU_DESIGN"
                            : "UUID, SHOP_ID, ID, ITEMS_PURCHASED";
                    long missing = count(statement, "SELECT COUNT(*) FROM (SELECT " + columns + " FROM main." + table + " EXCEPT SELECT " + columns + " FROM neuve." + table + ")");
                    long extra = count(statement, "SELECT COUNT(*) FROM (SELECT " + columns + " FROM neuve." + table + " EXCEPT SELECT " + columns + " FROM main." + table + ")");
                    System.out.println(table + " : " + missing + " ligne(s) perdue(s), " + extra + " ligne(s) en trop");
                    assertEquals(0, missing, table);
                    assertEquals(0, extra, table);
                }
                long cookies = count(statement, "SELECT SUM(COOKIES) FROM main.cookieclicker_players");
                long purchases = count(statement, "SELECT SUM(ITEMS_PURCHASED) FROM main.cookieclicker_shops");
                System.out.println("Total des cookies " + cookies + ", total des achats " + purchases);
                statement.execute("DETACH DATABASE neuve");
            }
        }
    }

    private static long count(Database database, String sql) throws SQLException {
        try (Connection connection = database.connection(); Statement statement = connection.createStatement()) {
            return count(statement, sql);
        }
    }

    private static long count(Statement statement, String sql) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getLong(1) : 0;
        }
    }
}
