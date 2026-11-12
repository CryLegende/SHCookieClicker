package de.zillolp.cookieclicker.commands.subcommands;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.database.DatabaseManager;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.manager.ClickerPlayerManager;
import de.zillolp.cookieclicker.manager.DesignManager;
import de.zillolp.cookieclicker.menu.Menu;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ResetSubCommand extends SubCommand {
    private final DatabaseManager databaseManager;
    private final ClickerPlayerManager clickerPlayerManager;
    private final DesignManager designManager;

    public ResetSubCommand(CookieClicker plugin, String mainCommand, String... subCommands) {
        super(plugin, mainCommand, subCommands);
        databaseManager = plugin.getDatabaseManager();
        clickerPlayerManager = plugin.getClickerPlayerManager();
        designManager = plugin.getDesignManager();
    }

    @Override
    public boolean onlyPlayer() {
        return false;
    }

    @Override
    public boolean onCommand(CookieClicker plugin, CommandSender commandSender, Command command, String[] args) {
        if (args.length != 2) {
            return false;
        }
        ClickerStatsProfile clickerStatsProfile = clickerPlayerManager.findOrLoadProfile(args[1]);
        if (clickerStatsProfile == null) {
            commandSender.sendMessage(languageConfig.getLanguageWithPrefix(PluginLanguage.PLAYER_NOT_FOUND));
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

        clickerStatsProfile.setCookies(0);
        clickerStatsProfile.setPerClick(1);
        clickerStatsProfile.setClickerClicks(0);
        clickerStatsProfile.setBlockDesign(0);
        clickerStatsProfile.setParticleDesign(0);
        clickerStatsProfile.setMenuDesign(0);
        clickerStatsProfile.clearPurchases();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> databaseManager.saveProfile(clickerStatsProfile));

        if (player != null) {
            designManager.stopParticleEffects(uuid);
            designManager.resetClickerBlocks(player);
        }

        commandSender.sendMessage(languageConfig.getTranslatedLanguage(PluginLanguage.RESET_DONE).replace("%player%", clickerStatsProfile.getName()));
        return true;
    }

    @Override
    public boolean hasPermission(CommandSender commandSender) {
        return canAdmin(commandSender, "reset");
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
        return "reset <joueur>";
    }
}
