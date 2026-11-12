package de.zillolp.cookieclicker.commands.subcommands;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.config.customconfigs.LocationConfig;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.manager.HologramManager;
import de.zillolp.cookieclicker.profiles.ClickerGameProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SetSubCommand extends SubCommand {
    private final LocationConfig locationConfig;
    private final HologramManager hologramManager;

    public SetSubCommand(CookieClicker plugin, String mainCommand, String... subCommands) {
        super(plugin, mainCommand, subCommands);
        locationConfig = plugin.getLocationConfig();
        hologramManager = plugin.getHologramManager();
    }

    public boolean onCommand(CookieClicker plugin, CommandSender commandSender, Command command, String[] args) {
        if (args.length < 2 || args.length > 4) {
            return false;
        }
        Player player = (Player) commandSender;
        UUID uuid = player.getUniqueId();
        if (args.length == 2) {
            if (!(args[1].equalsIgnoreCase("resettimer"))) {
                return false;
            }
            Location resetTimerLocation = locationConfig.getLocation("ResetTimer");
            if (resetTimerLocation != null) {
                for (Player player1 : Bukkit.getOnlinePlayers()) {
                    hologramManager.deleteHologramsByLocation(player1, resetTimerLocation);
                }
            }
            locationConfig.saveLocation("ResetTimer", player.getLocation());
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.RESETTIMER_SET));
            return true;
        }
        if (args.length == 3) {
            if (!(args[1].equalsIgnoreCase("clicker"))) {
                return false;
            }
            if (!(isNumeric(args[2]))) {
                commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.NOT_A_NUMBER).replace("%input%", args[2]));
                return true;
            }
            ClickerGameProfile clickerGameProfile = plugin.getClickerPlayerManager().getGameProfile(uuid);
            clickerGameProfile.setSetupState(ClickerGameProfile.SetupState.SET_CLICKER, Integer.parseInt(args[2]));
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.SETUP_CLICK_BLOCK));
            return true;
        }
        if (!(args[1].equalsIgnoreCase("statswall"))) {
            return false;
        }
        if ((!(args[2].equalsIgnoreCase("alltime"))) && (!(args[2].equalsIgnoreCase("time")))) {
            return false;
        }
        if (!(isNumeric(args[3]))) {
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.NOT_A_NUMBER).replace("%input%", args[3]));
            return true;
        }
        ClickerGameProfile clickerGameProfile = plugin.getClickerPlayerManager().getGameProfile(uuid);
        ClickerGameProfile.SetupState setupState = ClickerGameProfile.SetupState.SET_ALLTIME_HEAD;
        if (args[2].equalsIgnoreCase("time")) {
            setupState = ClickerGameProfile.SetupState.SET_TIME_HEAD;
        }
        clickerGameProfile.setSetupState(setupState, Integer.parseInt(args[3]));
        commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.SETUP_CLICK_HEAD));
        return true;
    }

    @Override
    public boolean onlyPlayer() {
        return true;
    }

    @Override
    public boolean hasPermission(CommandSender commandSender) {
        return canAdmin(commandSender, "set");
    }

    @Override
    public String getSyntax() {
        return "set <clicker <numéro>|resettimer|statswall <alltime|time> <numéro>>";
    }
}
