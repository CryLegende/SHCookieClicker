package de.zillolp.cookieclicker.commands.subcommands;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.enums.SoundType;
import de.zillolp.cookieclicker.manager.ClickerPlayerManager;
import de.zillolp.cookieclicker.manager.SoundManager;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.ArrayList;
import java.util.List;

public class StatsSubCommand extends SubCommand {
    private final BukkitScheduler bukkitScheduler;
    private final ClickerPlayerManager clickerPlayerManager;
    private final SoundManager soundManager;

    public StatsSubCommand(CookieClicker plugin, String mainCommand, String... subCommands) {
        super(plugin, mainCommand, subCommands);
        bukkitScheduler = plugin.getServer().getScheduler();
        clickerPlayerManager = plugin.getClickerPlayerManager();
        soundManager = plugin.getSoundManager();
    }

    @Override
    public boolean onlyPlayer() {
        return false;
    }

    @Override
    public boolean onCommand(CookieClicker plugin, CommandSender commandSender, Command command, String[] args) {
        if (args.length == 1) {
            if (!(commandSender instanceof Player player)) {
                commandSender.sendMessage(languageConfig.getLanguageWithPrefix(PluginLanguage.ONLY_PLAYER));
                return true;
            }
            soundManager.playSound(player, SoundType.STATS_INFO);
            send(commandSender, clickerPlayerManager.getStatsProfile(player.getUniqueId()));
            return true;
        }
        if (args.length != 2) {
            return false;
        }
        if (commandSender instanceof Player player) {
            soundManager.playSound(player, SoundType.STATS_INFO);
        }
        ClickerStatsProfile profile = clickerPlayerManager.findProfile(args[1]);
        if (profile != null) {
            send(commandSender, profile);
            return true;
        }
        // Joueur absent de la mémoire (arrivé depuis un autre serveur) : la base se lit hors du fil principal
        bukkitScheduler.runTaskAsynchronously(plugin, () -> {
            ClickerStatsProfile loaded = clickerPlayerManager.findOrLoadProfile(args[1]);
            bukkitScheduler.runTask(plugin, () -> send(commandSender, loaded));
        });
        return true;
    }

    private void send(CommandSender commandSender, ClickerStatsProfile profile) {
        if (profile == null) {
            commandSender.sendMessage(languageConfig.getLanguageWithPrefix(PluginLanguage.PLAYER_NOT_FOUND));
            return;
        }
        for (String line : languageConfig.getReplaceLanguages(PluginLanguage.PLAYER_STATS_INFO, profile.getUuid())) {
            commandSender.sendMessage(line);
        }
    }

    @Override
    public boolean hasPermission(CommandSender commandSender) {
        return true;
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
        return "stats [joueur]";
    }
}
