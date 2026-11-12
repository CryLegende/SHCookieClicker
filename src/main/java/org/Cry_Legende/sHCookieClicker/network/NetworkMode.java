package org.Cry_Legende.sHCookieClicker.network;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.Settings;
import org.Cry_Legende.sHCookieClicker.data.Database;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Mode réseau (SHNetwork installé, reseau.actif) : la progression est dans la base partagée.
 * <ul>
 *   <li>à la connexion, une fois le départ de l'autre serveur confirmé, le profil est relu en base ;</li>
 *   <li>au départ, la barrière de SHNetwork attend que le profil soit écrit avant que l'autre serveur ne le lise ;</li>
 *   <li>tant que le joueur est sur un serveur, la mémoire de ce serveur fait foi : modify et reset visant un joueur
 *       d'un autre serveur y sont transmis.</li>
 * </ul>
 */
public final class NetworkMode {
    private final SHCookieClicker plugin;
    private final NetworkLink link;
    private final Database database;
    private final Duration waitMax;
    private final Map<UUID, CompletableFuture<Void>> quitting = new ConcurrentHashMap<>();

    private NetworkMode(SHCookieClicker plugin, NetworkLink link, Database database, Duration waitMax) {
        this.plugin = plugin;
        this.link = link;
        this.database = database;
        this.waitMax = waitMax;
    }

    /** Null si SHNetwork est inutilisable ou la base du réseau injoignable : le serveur garde alors sa base. */
    public static NetworkMode open(SHCookieClicker plugin) {
        Settings settings = plugin.settings();
        Database database = null;
        try {
            NetworkLink link = ShNetworkLink.open();
            database = Database.mysql(settings.networkHost, settings.networkPort, settings.networkName, settings.networkUser,
                    settings.networkPassword, "SHCookieClicker-Reseau", plugin.getLogger());
            try (Connection connection = database.connection()) {
                connection.isValid(5);
            }
            plugin.getLogger().info("SHNetwork détecté : la progression est dans la base du réseau (" + settings.networkName + ").");
            return new NetworkMode(plugin, link, database, Duration.ofSeconds(settings.networkWaitSeconds));
        } catch (SQLException | RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.SEVERE, "SHNetwork présent mais base du réseau injoignable : progression locale à ce serveur (" + exception + ")");
            if (database != null) {
                database.close();
            }
            return null;
        }
    }

    public Database database() {
        return database;
    }

    public void start() {
        link.registerSaveTask(player -> {
            CompletableFuture<Void> pending = quitting.get(player.getUniqueId());
            return pending != null ? pending : CompletableFuture.completedFuture(null);
        });
    }

    /** Hors du fil principal : attend que l'autre serveur ait fini d'écrire le joueur. */
    public void awaitArrival(UUID uuid, String name) {
        try {
            Boolean saved = link.awaitSaved(uuid, waitMax).get(waitMax.toMillis() + 2000L, TimeUnit.MILLISECONDS);
            if (!Boolean.TRUE.equals(saved)) {
                plugin.getLogger().warning("Départ de " + name + " non confirmé par l'autre serveur : profil relu quand même.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException exception) {
            plugin.getLogger().warning("Départ de " + name + " non confirmé (" + exception + ") : profil relu quand même.");
        }
    }

    /** Hors du fil principal : relit le profil du joueur en base (rien s'il n'y est pas encore). */
    public void reload(UUID uuid, String name) {
        try {
            Profile stored = database.loadOne(plugin.profiles(), uuid);
            if (stored != null) {
                Profile profile = plugin.profiles().adopt(stored);
                plugin.profiles().rename(profile, name);
                plugin.ranking().markChanged();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Lecture du profil de " + name + " impossible", exception);
        }
    }

    /** Hors du fil principal : profil d'un joueur d'après son pseudo, relu en base ; null s'il n'y est pas. */
    public Profile fetch(String name) {
        try {
            UUID uuid = database.findUuid(name);
            if (uuid == null) {
                return null;
            }
            Profile stored = database.loadOne(plugin.profiles(), uuid);
            return stored == null ? null : plugin.profiles().adopt(stored);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Lecture du profil de " + name + " impossible", exception);
            return null;
        }
    }

    /** Départ d'un joueur : le futur se termine une fois son profil écrit. */
    public CompletableFuture<Void> beginQuit(UUID uuid) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        quitting.put(uuid, done);
        done.whenComplete((value, error) -> quitting.remove(uuid, done));
        return done;
    }

    /** Le joueur est-il connecté sur un autre serveur du réseau ? */
    public Optional<NetworkLink.Remote> remote(String name) {
        return link.find(name).filter(remote -> !remote.server().equalsIgnoreCase(link.serverName()));
    }

    /**
     * Commande visant un joueur connecté ailleurs : exécutée sur son serveur, là où est son profil.
     *
     * @return vrai si elle a été transmise
     */
    public boolean forward(CommandSender sender, String[] args, int playerArg) {
        Optional<NetworkLink.Remote> found = remote(args[playerArg]);
        if (found.isEmpty()) {
            return false;
        }
        NetworkLink.Remote remote = found.get();
        String[] forwarded = args.clone();
        forwarded[playerArg] = remote.name();
        link.runFor(remote.name(), "shcookieclicker " + String.join(" ", forwarded));
        plugin.messages().send(sender, "reseau.transmis", "joueur", remote.name(), "serveur", remote.server());
        return true;
    }
}
