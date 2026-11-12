package org.Cry_Legende.sHCookieClicker.network;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.data.Database;
import org.Cry_Legende.sHCookieClicker.data.LegacyShopMigration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * /shcookieclicker import : copie les joueurs et leurs achats du fichier SQLite du serveur dans la base du réseau,
 * sans toucher aux joueurs qui y sont déjà. La boutique de la 4.8 du fichier est d'abord reprise si besoin.
 */
public final class NetworkImport {

    private NetworkImport() {
    }

    /** Hors du fil principal. @return {ajoutés, déjà présents}, ou null en cas d'échec */
    public static int[] run(SHCookieClicker plugin, File sqliteFile) {
        try (Database sqlite = Database.sqlite(sqliteFile, plugin.getLogger())) {
            sqlite.createSchema();
            return copy(plugin, sqlite);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Copie de la progression SQLite impossible", exception);
            return null;
        }
    }

    private static int[] copy(SHCookieClicker plugin, Database sqlite) throws SQLException {
        int imported = 0;
        int present = 0;
        try (Connection from = sqlite.connection(); Connection to = plugin.database().connection()) {
            LegacyShopMigration.run(from, plugin.getLogger());
            try (PreparedStatement select = from.prepareStatement("SELECT UUID, NAME, COOKIES, PER_CLICK, CLICKER_CLICKS, BLOCK_DESIGN, PARTICLE_DESIGN, MENU_DESIGN FROM " + Database.PLAYERS);
                 PreparedStatement insert = to.prepareStatement("INSERT IGNORE INTO " + Database.PLAYERS
                         + " (UUID, NAME, COOKIES, PER_CLICK, CLICKER_CLICKS, BLOCK_DESIGN, PARTICLE_DESIGN, MENU_DESIGN) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
                 ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    insert.setString(1, rows.getString(1));
                    insert.setString(2, rows.getString(2));
                    for (int column = 3; column <= 8; column++) {
                        insert.setLong(column, rows.getLong(column));
                    }
                    if (insert.executeUpdate() > 0) {
                        imported++;
                        copyShops(from, to, rows.getString(1));
                    } else {
                        present++;
                    }
                }
            }
        }
        return new int[]{imported, present};
    }

    // Achats d'un joueur qui vient d'être ajouté, jamais ceux d'un joueur déjà présent dans la base du réseau
    private static void copyShops(Connection from, Connection to, String uuid) throws SQLException {
        try (PreparedStatement select = from.prepareStatement("SELECT SHOP_ID, ID, ITEMS_PURCHASED FROM " + Database.SHOPS + " WHERE UUID = ?");
             PreparedStatement insert = to.prepareStatement("INSERT IGNORE INTO " + Database.SHOPS + " (UUID, SHOP_ID, ID, ITEMS_PURCHASED) VALUES (?, ?, ?, ?)")) {
            select.setString(1, uuid);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    insert.setString(1, uuid);
                    insert.setString(2, rows.getString(1));
                    insert.setInt(3, rows.getInt(2));
                    insert.setLong(4, rows.getLong(3));
                    insert.addBatch();
                }
            }
            insert.executeBatch();
        }
    }
}
