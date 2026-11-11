package de.zillolp.cookieclicker.commands.maincommands;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.commands.subcommands.SubCommand;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.manager.VersionManager;
import de.zillolp.cookieclicker.profiles.ClickerGameProfile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CookieClickerCommand extends MainCommand {
    private final VersionManager versionManager;

    public CookieClickerCommand(CookieClicker plugin) {
        super(plugin);
        versionManager = plugin.getVersionManager();
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String label, String[] args) {
        String PREFIX = languageConfig.getTranslatedLanguage(PluginLanguage.PREFIX);
        if (!(versionManager.checkVersion())) {
            for (String wrongVersionMessage : versionManager.getWrongVersionMessage()) {
                commandSender.sendMessage(PREFIX + wrongVersionMessage);
            }
            return true;
        }
        if (!(plugin.getDatabaseConnector().hasConnection())) {
            commandSender.sendMessage(PREFIX + "§cThe plugin isn't connected to the database!");
            return true;
        }
        List<String> allowed = new ArrayList<>();
        for (String mainCommand : subCommandOrder) {
            if (subCommands.get(mainCommand).get(0).hasPermission(commandSender)) {
                allowed.add(mainCommand);
            }
        }
        List<SubCommand> found = args.length == 0 ? null : subCommands.get(args[0].toLowerCase());
        if (found == null) {
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.USAGE).replace("%subcommands%", String.join("|", allowed)));
            return true;
        }
        SubCommand subCommand = found.get(0);
        if (!(subCommand.hasPermission(commandSender))) {
            commandSender.sendMessage(languageConfig.getLanguageWithPrefix(PluginLanguage.NO_PERMISSION));
            return true;
        }
        if (subCommand.onlyPlayer() && (!(commandSender instanceof Player))) {
            commandSender.sendMessage(languageConfig.getLanguageWithPrefix(PluginLanguage.ONLY_PLAYER));
            return true;
        }
        if (commandSender instanceof Player player) {
            ClickerGameProfile clickerGameProfile = plugin.getClickerPlayerManager().getGameProfile(player.getUniqueId());
            if (clickerGameProfile.isOverLastCommandInteraction(500)) {
                return true;
            }
            clickerGameProfile.updateLastCommandInteraction();
        }
        if (!(subCommand.onCommand(plugin, commandSender, command, args))) {
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.SYNTAX).replace("%syntax%", subCommand.getSyntax()));
        }
        return true;
    }
}
