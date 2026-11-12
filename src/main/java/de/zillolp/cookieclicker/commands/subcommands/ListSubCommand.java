package de.zillolp.cookieclicker.commands.subcommands;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;

public class ListSubCommand extends SubCommand {

    public ListSubCommand(CookieClicker plugin, String mainCommand, String... subCommands) {
        super(plugin, mainCommand, subCommands);
    }

    @Override
    public boolean onCommand(CookieClicker plugin, CommandSender commandSender, Command command, String[] args) {
        ArrayList<Location> clickerLocations = plugin.getCookieClickerManager().getClickerLocations();
        commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.LIST_HEADER));
        int number = 1;
        for (Location clickerLocation : clickerLocations) {
            double x = clickerLocation.getX() + 0.5;
            double y = clickerLocation.getY() + 1;
            double z = clickerLocation.getZ() + 0.5;
            Component entry = Text.component(languageConfig.getTranslatedLanguage(PluginLanguage.LIST_ENTRY).replace("%number%", String.valueOf(number)))
                    .clickEvent(ClickEvent.runCommand("/tp " + x + " " + y + " " + z))
                    .hoverEvent(HoverEvent.showText(Text.component(languageConfig.getTranslatedLanguage(PluginLanguage.LIST_HOVER))));
            commandSender.sendMessage(entry);
            number++;
        }
        int clickerLocationSize = clickerLocations.size();
        if (clickerLocationSize == 1) {
            commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.LIST_COUNT_ONE));
            return true;
        }
        commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.LIST_COUNT_MANY).replace("%count%", String.valueOf(clickerLocationSize)));
        return true;
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return canAdmin(sender, "list");
    }

    @Override
    public boolean onlyPlayer() {
        return false;
    }

    @Override
    public String getSyntax() {
        return "list";
    }
}
