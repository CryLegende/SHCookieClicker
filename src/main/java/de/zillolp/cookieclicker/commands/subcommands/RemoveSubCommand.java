package de.zillolp.cookieclicker.commands.subcommands;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.config.customconfigs.LocationConfig;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.manager.HologramManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class RemoveSubCommand extends SubCommand {
    private final LocationConfig locationConfig;
    private final HologramManager hologramManager;

    public RemoveSubCommand(CookieClicker plugin, String mainCommand, String... subCommands) {
        super(plugin, mainCommand, subCommands);
        locationConfig = plugin.getLocationConfig();
        hologramManager = plugin.getHologramManager();
    }

    @Override
    public boolean onlyPlayer() {
        return false;
    }

    @Override
    public boolean onCommand(CookieClicker plugin, CommandSender commandSender, Command command, String[] args) {
        if (args.length == 2) {
            if (!(args[1].equalsIgnoreCase("resettimer"))) {
                return false;
            }
            String locationName = "ResetTimer";
            if (!(locationConfig.isLocation(locationName))) {
                commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.RESETTIMER_NOT_SET));
                return true;
            }
            Location resetTimerLocation = locationConfig.getLocation("ResetTimer");
            if (resetTimerLocation != null) {
                for (Player player1 : Bukkit.getOnlinePlayers()) {
                    hologramManager.deleteHologramsByLocation(player1, resetTimerLocation);
                }
            }
            locationConfig.removeLocation(locationName);
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.RESETTIMER_REMOVED));
            return true;
        }
        if (args.length != 3) {
            return false;
        }
        if (!(args[1].equalsIgnoreCase("clicker"))) {
            return false;
        }
        if (!(isNumeric(args[2]))) {
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.NOT_A_NUMBER).replace("%input%", args[2]));
            return true;
        }
        int number = Integer.parseInt(args[2]);
        String locationName = "CookieClicker.Clicker-" + number;
        if (!(locationConfig.isLocation(locationName))) {
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.CLICKER_UNKNOWN));
            return true;
        }
        Location location = locationConfig.getLocation(locationName);
        plugin.getCookieClickerManager().getClickerLocations().remove(location);
        locationConfig.removeLocation(locationName);
        location.getBlock().getState().update(true);

        HologramManager hologramManager = plugin.getHologramManager();
        for (Player player : Bukkit.getOnlinePlayers()) {
            hologramManager.deleteHologramsByLocation(player, location);
        }
        commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.CLICKER_REMOVED).replace("%number%", String.valueOf(number)));
        return true;
    }

    @Override
    public boolean hasPermission(CommandSender commandSender) {
        return canAdmin(commandSender, "remove");
    }

    @Override
    public List<String> getCustomTabCompletions(String[] args, int position) {
        if (position != 3 || !args[1].equalsIgnoreCase("clicker")) {
            return null;
        }
        return locationConfig.getClickerNumbers("CookieClicker");
    }

    @Override
    public String getSyntax() {
        return "remove <clicker <numéro>|resettimer>";
    }
}
