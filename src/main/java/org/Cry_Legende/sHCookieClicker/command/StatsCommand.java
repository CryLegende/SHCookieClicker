package org.Cry_Legende.sHCookieClicker.command;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.SoundSet;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.network.NetworkMode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/** /cookies [joueur] : statistiques du Cookie Clicker. */
public final class StatsCommand implements TabExecutor {
    public static final String PERMISSION = "shcookieclicker.use";
    private final SHCookieClicker plugin;

    public StatsCommand(SHCookieClicker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        plugin.sync(() -> show(sender, args.length == 0 ? null : args[0]));
        return true;
    }

    /** Ouverte à tous : seul un refus explicite (shcookieclicker.use à false) la ferme. */
    static boolean allowed(CommandSender sender) {
        return !sender.isPermissionSet(PERMISSION) || sender.hasPermission(PERMISSION);
    }

    /** Fil principal. {@code name} null : l'expéditeur lui-même. */
    void show(CommandSender sender, String name) {
        if (!allowed(sender)) {
            plugin.messages().send(sender, "commande.inconnue");
            return;
        }
        if (!plugin.isReady()) {
            plugin.messages().send(sender, "commande.base-injoignable");
            return;
        }
        if (sender instanceof Player player) {
            plugin.settings().sounds.play(player, SoundSet.Action.STATS);
        }
        if (name == null) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "commande.reservee-joueur");
                return;
            }
            send(sender, player.getUniqueId(), player.getName(), plugin.profiles().get(player.getUniqueId()));
            return;
        }
        Profile profile = plugin.profiles().find(name);
        if (profile != null) {
            send(sender, profile.uuid(), profile.name(), profile);
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        NetworkMode network = plugin.network();
        if (network != null) {
            // Joueur arrivé sur un autre serveur depuis le démarrage : la base se lit hors du fil principal
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Profile fetched = network.fetch(name);
                plugin.sync(() -> {
                    if (fetched == null && online != null) {
                        send(sender, online.getUniqueId(), online.getName(), null);
                    } else if (fetched == null) {
                        plugin.messages().send(sender, "commande.joueur-inconnu");
                    } else {
                        send(sender, fetched.uuid(), fetched.name(), fetched);
                    }
                });
            });
            return;
        }
        if (online != null) {
            send(sender, online.getUniqueId(), online.getName(), null);
            return;
        }
        plugin.messages().send(sender, "commande.joueur-inconnu");
    }

    private void send(CommandSender sender, UUID uuid, String name, Profile profile) {
        int place = plugin.ranking().place(uuid);
        plugin.messages().sendLines(sender, "stats",
                "joueur", name,
                "place", place > 0 ? "#" + plugin.number(place) : "-",
                "cookies", plugin.number(profile == null ? 0 : profile.cookies()),
                "par-clic", plugin.number(profile == null ? 1 : profile.perClick()),
                "clics", plugin.number(profile == null ? 0 : profile.clickerClicks()));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1 || !allowed(sender)) {
            return List.of();
        }
        return AdminCommand.playerNames(plugin, args[0]);
    }
}
