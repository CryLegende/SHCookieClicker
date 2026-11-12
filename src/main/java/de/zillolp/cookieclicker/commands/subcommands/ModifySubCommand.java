package de.zillolp.cookieclicker.commands.subcommands;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.database.DatabaseManager;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.manager.ClickerPlayerManager;
import de.zillolp.cookieclicker.menu.Menu;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModifySubCommand extends SubCommand {
    private final DatabaseManager databaseManager;
    private final ClickerPlayerManager clickerPlayerManager;

    public ModifySubCommand(CookieClicker plugin, String mainCommand, String... subCommands) {
        super(plugin, mainCommand, subCommands);
        databaseManager = plugin.getDatabaseManager();
        clickerPlayerManager = plugin.getClickerPlayerManager();
    }

    public boolean onCommand(CookieClicker plugin, CommandSender commandSender, Command command, String[] args) {
        if (args.length != 5) {
            return false;
        }
        ClickerStatsProfile clickerStatsProfile = clickerPlayerManager.findOrLoadProfile(args[1]);
        if (clickerStatsProfile == null) {
            commandSender.sendMessage(languageConfig.getLanguageWithPrefix(PluginLanguage.PLAYER_NOT_FOUND));
            return true;
        }
        String type = args[3].toUpperCase();
        if ((!(type.equals("COOKIES"))) && (!(type.equals("PER_CLICK"))) && (!(type.equals("CLICKER_CLICKS")))) {
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.MODIFY_BAD_TYPE).replace("%input%", args[3]));
            return true;
        }
        if (!(isNumeric(args[4]))) {
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.NOT_A_NUMBER).replace("%input%", args[4]));
            return true;
        }

        // Mode réseau : le joueur est sur un autre serveur, c'est là que se trouve son profil
        if (plugin.getNetwork() != null && plugin.getNetwork().forwardIfRemote(commandSender, args, 1)) {
            return true;
        }

        UUID uuid = clickerStatsProfile.getUuid();
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
            player.closeInventory();
        }

        long value = Long.parseLong(args[4]);
        if (plugin.getNetwork() != null && player == null) {
            // Mode réseau : joueur connecté nulle part, son profil en mémoire peut dater d'avant sa dernière partie
            // sur un autre serveur : on repart de la base
            databaseManager.reloadProfile(clickerStatsProfile);
        }
        switch (args[2].toUpperCase()) {
            case "ADD":
                setValue(clickerStatsProfile, type, getValue(clickerStatsProfile, type) + value);
                break;
            case "SET":
                setValue(clickerStatsProfile, type, value);
                break;
            case "REMOVE":
                setValue(clickerStatsProfile, type, getValue(clickerStatsProfile, type) - value);
                break;
            default:
                return false;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> databaseManager.saveProfile(clickerStatsProfile));
        commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.MODIFY_DONE).replace("%type%", type).replace("%player%", clickerStatsProfile.getName()));
        return true;
    }

    private long getValue(ClickerStatsProfile clickerStatsProfile, String type) {
        switch (type) {
            case "COOKIES":
                return clickerStatsProfile.getCookies();
            case "PER_CLICK":
                return clickerStatsProfile.getPerClick();
            case "CLICKER_CLICKS":
                return clickerStatsProfile.getClickerClicks();
            default:
                return 0;
        }
    }

    private void setValue(ClickerStatsProfile clickerStatsProfile, String type, long value) {
        switch (type) {
            case "COOKIES":
                clickerStatsProfile.setCookies(value);
                break;
            case "PER_CLICK":
                clickerStatsProfile.setPerClick(value);
                break;
            case "CLICKER_CLICKS":
                clickerStatsProfile.setClickerClicks(value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onlyPlayer() {
        return false;
    }

    @Override
    public boolean hasPermission(CommandSender commandSender) {
        return canAdmin(commandSender, "modify");
    }

    @Override
    public List<String> getCustomTabCompletions(String[] args, int position) {
        if (position != 2) {
            return null;
        }
        ArrayList<String> playerNames = new ArrayList<>();
        for (ClickerStatsProfile clickerStatsProfile : plugin.getClickerPlayerManager().getClickerStatsProfiles().values()) {
            playerNames.add(clickerStatsProfile.getName());
        }
        return playerNames;
    }

    @Override
    public String getSyntax() {
        return "modify <joueur> <add|set|remove> <COOKIES|PER_CLICK|CLICKER_CLICKS> <montant>";
    }
}
