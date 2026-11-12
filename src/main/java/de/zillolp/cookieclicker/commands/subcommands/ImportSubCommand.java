package de.zillolp.cookieclicker.commands.subcommands;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.database.DatabaseConnector;
import de.zillolp.cookieclicker.database.DatabaseManager;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * /cookieclicker import : copie les joueurs et leurs achats de l'ancien fichier SQLite du serveur dans la base du
 * réseau, sans toucher aux joueurs qui y ont déjà une ligne (mode réseau seulement).
 */
public class ImportSubCommand extends SubCommand {

    public ImportSubCommand(CookieClicker plugin, String mainCommand, String... subCommands) {
        super(plugin, mainCommand, subCommands);
    }

    @Override
    public boolean onlyPlayer() {
        return false;
    }

    @Override
    public boolean hasPermission(CommandSender commandSender) {
        return canAdmin(commandSender, "import");
    }

    @Override
    public boolean onCommand(CookieClicker plugin, CommandSender commandSender, Command command, String[] args) {
        if (args.length != 1) {
            return false;
        }
        if (plugin.getNetwork() == null) {
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.NETWORK_IMPORT_NO_NETWORK));
            return true;
        }
        File sqliteFile = new File(plugin.getDataFolder(), "cookieclicker.db");
        if (!sqliteFile.isFile()) {
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.NETWORK_IMPORT_NO_FILE));
            return true;
        }
        commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.NETWORK_IMPORT_STARTED));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int[] result = copy(plugin);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (result == null) {
                    commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.NETWORK_IMPORT_FAILED));
                    return;
                }
                commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.NETWORK_IMPORT_DONE)
                    .replace("%imported%", String.valueOf(result[0])).replace("%present%", String.valueOf(result[1])));
            });
        });
        return true;
    }

    /** @return {joueurs ajoutés, joueurs déjà présents}, ou null si la copie a échoué */
    private int[] copy(CookieClicker plugin) {
        DatabaseConnector sqlite = new DatabaseConnector(plugin, false, "cookieclicker", "", "", "", "", "");
        int imported = 0;
        int present = 0;
        try (Connection from = sqlite.getConnection(); Connection to = plugin.getDatabaseConnector().getConnection()) {
            DatabaseManager.migrateLegacyShops(from, plugin.getPluginConfig(), plugin.getLogger());
            try (PreparedStatement select = from.prepareStatement("SELECT UUID, NAME, COOKIES, PER_CLICK, CLICKER_CLICKS, BLOCK_DESIGN, PARTICLE_DESIGN, MENU_DESIGN FROM cookieclicker_players");
                 PreparedStatement insert = to.prepareStatement("INSERT IGNORE INTO cookieclicker_players (UUID, NAME, COOKIES, PER_CLICK, CLICKER_CLICKS, BLOCK_DESIGN, PARTICLE_DESIGN, MENU_DESIGN) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
                 ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    insert.setString(1, rows.getString("UUID"));
                    insert.setString(2, rows.getString("NAME"));
                    insert.setLong(3, rows.getLong("COOKIES"));
                    insert.setLong(4, rows.getLong("PER_CLICK"));
                    insert.setLong(5, rows.getLong("CLICKER_CLICKS"));
                    insert.setLong(6, rows.getLong("BLOCK_DESIGN"));
                    insert.setLong(7, rows.getLong("PARTICLE_DESIGN"));
                    insert.setLong(8, rows.getLong("MENU_DESIGN"));
                    if (insert.executeUpdate() > 0) {
                        imported++;
                        copyShops(from, to, rows.getString("UUID"));
                    } else {
                        present++;
                    }
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Importation de la progression impossible : " + exception.getMessage());
            return null;
        } finally {
            sqlite.close();
        }
        return new int[]{imported, present};
    }

    // Achats d'un joueur qui vient d'être ajouté : jamais ceux d'un joueur déjà présent dans la base du réseau
    private void copyShops(Connection from, Connection to, String uuid) throws SQLException {
        try (PreparedStatement select = from.prepareStatement("SELECT SHOP_ID, ID, ITEMS_PURCHASED FROM cookieclicker_shops WHERE UUID = ?");
             PreparedStatement insert = to.prepareStatement("INSERT IGNORE INTO cookieclicker_shops (UUID, SHOP_ID, ID, ITEMS_PURCHASED) VALUES (?, ?, ?, ?)")) {
            select.setString(1, uuid);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    insert.setString(1, uuid);
                    insert.setString(2, rows.getString("SHOP_ID"));
                    insert.setInt(3, rows.getInt("ID"));
                    insert.setLong(4, rows.getLong("ITEMS_PURCHASED"));
                    insert.addBatch();
                }
            }
            insert.executeBatch();
        }
    }

    @Override
    public String getSyntax() {
        return "import";
    }
}
