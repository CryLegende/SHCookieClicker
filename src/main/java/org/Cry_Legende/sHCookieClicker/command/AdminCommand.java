package org.Cry_Legende.sHCookieClicker.command;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.Messages;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.game.Clickers;
import org.Cry_Legende.sHCookieClicker.menu.Menu;
import org.Cry_Legende.sHCookieClicker.network.NetworkImport;
import org.Cry_Legende.sHCookieClicker.network.NetworkMode;
import org.Cry_Legende.sHCookieClicker.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * /shcookieclicker (ancien nom /cookieclicker) : administration. Chaque sous-commande a sa permission
 * shcookieclicker.admin.<nom> ; toutes marchent depuis la console sauf set. Une commande arrivée du fil d'un
 * monde s'exécute au tick suivant, sur le fil principal.
 */
public final class AdminCommand implements TabExecutor {
    private static final List<String> SUBCOMMANDS = List.of("reload", "list", "set", "remove", "modify", "reset", "import");
    private static final List<String> OPERATIONS = List.of("add", "set", "remove");
    private static final List<String> FIELDS = List.of("COOKIES", "PER_CLICK", "CLICKER_CLICKS");

    private final SHCookieClicker plugin;

    public AdminCommand(SHCookieClicker plugin) {
        this.plugin = plugin;
    }

    private static boolean allowed(CommandSender sender, String subcommand) {
        return sender.hasPermission("shcookieclicker.admin." + subcommand) || sender.hasPermission("shcookieclicker.admin");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        String[] copy = args.clone();
        plugin.sync(() -> execute(sender, copy));
        return true;
    }

    private void execute(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        // Ancienne sous-commande, gardée pour les habitudes : /cookies
        if (sub.equals("stats")) {
            new StatsCommand(plugin).show(sender, args.length > 1 ? args[1] : null);
            return;
        }
        if (!SUBCOMMANDS.contains(sub)) {
            List<String> permitted = SUBCOMMANDS.stream().filter(name -> allowed(sender, name)).toList();
            if (permitted.isEmpty()) {
                messages.send(sender, "commande.inconnue");
            } else {
                messages.send(sender, "commande.utilisation", "commandes", String.join("|", permitted));
            }
            return;
        }
        if (!allowed(sender, sub)) {
            messages.send(sender, "commande.inconnue");
            return;
        }
        if (!plugin.isReady() && !sub.equals("reload")) {
            messages.send(sender, "commande.base-injoignable");
            return;
        }
        switch (sub) {
            case "reload" -> reload(sender);
            case "list" -> list(sender);
            case "set" -> set(sender, args);
            case "remove" -> remove(sender, args);
            case "modify" -> modify(sender, args);
            case "reset" -> reset(sender, args);
            case "import" -> importSqlite(sender);
            default -> {
            }
        }
    }

    private void syntax(CommandSender sender, String syntax) {
        plugin.messages().send(sender, "commande.syntaxe", "syntaxe", syntax);
    }

    // ---- reload

    private void reload(CommandSender sender) {
        List<String> errors = plugin.reload();
        if (!errors.isEmpty()) {
            for (String error : errors) {
                int split = error.indexOf('\n');
                plugin.messages().send(sender, "rechargement.erreur", "fichier", error.substring(0, split),
                        "erreur", error.substring(split + 1).replace('\n', ' '));
            }
            return;
        }
        plugin.messages().send(sender, "rechargement.ok");
        plugin.messages().send(sender, "rechargement.redemarrage");
    }

    // ---- clickers

    private void list(CommandSender sender) {
        Messages messages = plugin.messages();
        List<Clickers.Spot> spots = plugin.clickers().all();
        if (spots.isEmpty()) {
            messages.send(sender, "clickers.liste-vide");
            return;
        }
        messages.send(sender, "clickers.liste-titre");
        for (Clickers.Spot spot : spots) {
            World world = Bukkit.getWorld(spot.world());
            String teleport = "/execute in " + (world == null ? "minecraft:overworld" : world.getKey().asString())
                    + " run tp @s " + (spot.x() + 0.5) + " " + (spot.y() + 1) + " " + (spot.z() + 0.5);
            sender.sendMessage(Text.of(messages.raw("clickers.liste-ligne", "numero", String.valueOf(spot.number()), "monde", spot.world(),
                            "x", String.valueOf(spot.x()), "y", String.valueOf(spot.y()), "z", String.valueOf(spot.z())))
                    .clickEvent(ClickEvent.runCommand(teleport))
                    .hoverEvent(HoverEvent.showText(Text.of(messages.raw("clickers.liste-survol")))));
        }
    }

    private Integer number(CommandSender sender, String text) {
        try {
            int number = Integer.parseInt(text);
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // message ci-dessous
        }
        plugin.messages().send(sender, "commande.pas-un-nombre", "valeur", text);
        return null;
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length != 3 || !args[1].equalsIgnoreCase("clicker")) {
            syntax(sender, "set clicker <numéro>");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "commande.reservee-joueur");
            return;
        }
        Integer number = number(sender, args[2]);
        if (number == null) {
            return;
        }
        plugin.clicks().startPlacing(player, number);
        plugin.messages().send(sender, "clickers.pose", "numero", String.valueOf(number));
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length != 3 || !args[1].equalsIgnoreCase("clicker")) {
            syntax(sender, "remove clicker <numéro>");
            return;
        }
        Integer number = number(sender, args[2]);
        if (number == null) {
            return;
        }
        Clickers.Spot spot = plugin.clickers().find(number);
        if (spot == null) {
            plugin.messages().send(sender, "clickers.inconnu", "numero", String.valueOf(number));
            return;
        }
        try {
            Clickers.write(new File(plugin.getDataFolder(), "locations.yml"), number, null);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Écriture de locations.yml impossible", exception);
            return;
        }
        plugin.reloadClickers();
        plugin.holograms().restart();
        Location location = spot.location();
        if (location != null) {
            Block block = location.getBlock();
            for (Player online : location.getWorld().getPlayers()) {
                online.sendBlockChange(location, block.getBlockData());
            }
        }
        plugin.messages().send(sender, "clickers.retire", "numero", String.valueOf(number));
    }

    // ---- progression

    private void modify(CommandSender sender, String[] args) {
        if (args.length != 5) {
            syntax(sender, "modify <joueur> <add|set|remove> <COOKIES|PER_CLICK|CLICKER_CLICKS> <nombre>");
            return;
        }
        Profile.Operation operation = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> Profile.Operation.ADD;
            case "set" -> Profile.Operation.SET;
            case "remove" -> Profile.Operation.REMOVE;
            default -> null;
        };
        if (operation == null) {
            syntax(sender, "modify <joueur> <add|set|remove> <COOKIES|PER_CLICK|CLICKER_CLICKS> <nombre>");
            return;
        }
        Profile.Field field = switch (args[3].toUpperCase(Locale.ROOT).replace("PERCLICK", "PER_CLICK").replace("CLICKERCLICKS", "CLICKER_CLICKS")) {
            case "COOKIES" -> Profile.Field.COOKIES;
            case "PER_CLICK" -> Profile.Field.PER_CLICK;
            case "CLICKER_CLICKS" -> Profile.Field.CLICKER_CLICKS;
            default -> null;
        };
        if (field == null) {
            plugin.messages().send(sender, "administration.type-inconnu", "valeur", args[3]);
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[4]);
            if (amount < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            plugin.messages().send(sender, "commande.pas-un-nombre", "valeur", args[4]);
            return;
        }
        if (plugin.network() != null && plugin.network().forward(sender, args, 1)) {
            return;
        }
        withProfile(sender, args[1], profile -> {
            long value = profile.modify(field, operation, amount);
            afterChange(profile);
            plugin.messages().send(sender, "administration.modifie", "type", field.name(), "joueur", profile.name(), "valeur", plugin.number(value));
        });
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length != 2) {
            syntax(sender, "reset <joueur>");
            return;
        }
        if (plugin.network() != null && plugin.network().forward(sender, args, 1)) {
            return;
        }
        withProfile(sender, args[1], profile -> {
            profile.reset();
            Player player = Bukkit.getPlayer(profile.uuid());
            if (player != null) {
                plugin.particles().stop(player.getUniqueId());
                plugin.look().reset(player);
            }
            afterChange(profile);
            plugin.messages().send(sender, "administration.remis-a-zero", "joueur", profile.name());
        });
    }

    /**
     * Profil d'un joueur pour modify et reset (fil principal) : en mémoire, ou créé pour un joueur connecté qui n'a
     * jamais cliqué. En mode réseau, un joueur connecté nulle part est relu en base d'abord (son profil en mémoire
     * peut dater d'avant sa dernière partie sur un autre serveur).
     */
    private void withProfile(CommandSender sender, String name, Consumer<Profile> action) {
        Player online = Bukkit.getPlayerExact(name);
        NetworkMode network = plugin.network();
        if (network != null && online == null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Profile fetched = network.fetch(name);
                plugin.sync(() -> {
                    if (fetched == null) {
                        plugin.messages().send(sender, "commande.joueur-inconnu");
                    } else {
                        action.accept(fetched);
                    }
                });
            });
            return;
        }
        Profile profile = online != null ? plugin.profiles().getOrCreate(online.getUniqueId(), online.getName()) : plugin.profiles().find(name);
        if (profile == null) {
            plugin.messages().send(sender, "commande.joueur-inconnu");
            return;
        }
        action.accept(profile);
    }

    // Écrit tout de suite, et redessine le menu ouvert du joueur
    private void afterChange(Profile profile) {
        plugin.ranking().markChanged();
        plugin.saver().flushNow();
        Player player = Bukkit.getPlayer(profile.uuid());
        if (player != null && player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
            menu.render();
        }
    }

    // ---- mode réseau

    private void importSqlite(CommandSender sender) {
        Messages messages = plugin.messages();
        if (plugin.network() == null) {
            messages.send(sender, "reseau.import-sans-reseau");
            return;
        }
        File file = new File(plugin.getDataFolder(), "cookieclicker.db");
        if (!file.isFile()) {
            messages.send(sender, "reseau.import-sans-fichier");
            return;
        }
        messages.send(sender, "reseau.import-debut");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int[] result = NetworkImport.run(plugin, file);
            plugin.sync(() -> {
                if (result == null) {
                    messages.send(sender, "reseau.import-echec");
                } else {
                    messages.send(sender, "reseau.import-fin", "ajoutes", String.valueOf(result[0]), "presents", String.valueOf(result[1]));
                }
            });
        });
    }

    // ---- complétion

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS.stream().filter(name -> allowed(sender, name)).toList(), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub) || !allowed(sender, sub)) {
            return List.of();
        }
        return switch (sub) {
            case "set" -> args.length == 2 ? filter(List.of("clicker"), args[1]) : List.of();
            case "remove" -> args.length == 2 ? filter(List.of("clicker"), args[1])
                    : args.length == 3 ? filter(plugin.clickers().all().stream().map(spot -> String.valueOf(spot.number())).toList(), args[2]) : List.of();
            case "modify" -> switch (args.length) {
                case 2 -> playerNames(plugin, args[1]);
                case 3 -> filter(OPERATIONS, args[2]);
                case 4 -> filter(FIELDS, args[3]);
                default -> List.of();
            };
            case "reset" -> args.length == 2 ? playerNames(plugin, args[1]) : List.of();
            default -> List.of();
        };
    }

    private static List<String> filter(List<String> options, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    /** Joueurs connectés, puis joueurs du Cookie Clicker, qui commencent par ce qui est tapé (au plus 40). */
    static List<String> playerNames(SHCookieClicker plugin, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        Set<String> names = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(player.getName());
            }
        }
        if (plugin.profiles() != null && !lower.isEmpty()) {
            for (Profile profile : plugin.profiles().all()) {
                if (names.size() >= 40) {
                    break;
                }
                String name = profile.name();
                if (name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }
}
