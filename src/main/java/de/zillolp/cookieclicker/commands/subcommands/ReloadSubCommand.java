package de.zillolp.cookieclicker.commands.subcommands;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.config.ConfigManager;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.runnables.StatsWallUpdater;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public class ReloadSubCommand extends SubCommand {

    public ReloadSubCommand(CookieClicker plugin, String mainCommand, String... subCommands) {
        super(plugin, mainCommand, subCommands);
    }

    @Override
    public boolean onlyPlayer() {
        return false;
    }

    @Override
    public boolean onCommand(CookieClicker plugin, CommandSender commandSender, Command command, String[] args) {
        // Tous les fichiers sont lus d'abord : si l'un est illisible, rien n'est touché et l'ancienne configuration reste
        ConfigManager.Pending pending = plugin.getConfigManager().prepareReload();
        if (!pending.errors().isEmpty()) {
            for (String error : pending.errors()) {
                int split = error.indexOf('\n');
                commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.RELOAD_ERROR)
                    .replace("%file%", error.substring(0, split)).replace("%error%", error.substring(split + 1)));
            }
            return true;
        }
        plugin.closeInventories();
        plugin.unloadPlayers();
        pending.apply();
        plugin.getResetTimerUpdater().resetTime();
        plugin.getCookieClickerManager().loadLocations();
        plugin.getSoundManager().load();
        plugin.restartStatsUpdater();
        StatsWallUpdater statsWallUpdater = plugin.getStatsWallUpdater();
        statsWallUpdater.getSortedAlltimeData().clear();
        statsWallUpdater.getCachedTimedData().clear();
        statsWallUpdater.updateAlltimeStatsWall();
        statsWallUpdater.updateTimeStatsWall();
        plugin.loadPlayers();
        commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.RELOAD_OK));
        commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.RELOAD_RESTART));
        return true;
    }

    @Override
    public boolean hasPermission(CommandSender commandSender) {
        return canAdmin(commandSender, "reload");
    }

    @Override
    public String getSyntax() {
        return "reload";
    }
}
