package de.zillolp.cookieclicker.network;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.config.customconfigs.LanguageConfig;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Mode réseau : la progression des joueurs est dans la base partagée (MySQL) au lieu du fichier SQLite du serveur.
 *
 * <ul>
 *   <li>à la connexion : une fois le départ de l'autre serveur confirmé, le profil du joueur est relu en base
 *       (le plugin le relit déjà à chaque connexion) ;</li>
 *   <li>au départ : la barrière de SHNetwork attend que la sauvegarde du profil soit finie avant que l'autre serveur ne lise ;</li>
 *   <li>tant que le joueur est sur un serveur, la mémoire de ce serveur fait foi : une commande d'administration visant
 *       un joueur d'un autre serveur y est exécutée, là où se trouve son profil.</li>
 * </ul>
 */
public final class NetworkCookies {

    private final CookieClicker plugin;
    private final NetworkLink link;
    private final Duration waitMax;
    /** Sauvegardes de départ en cours, par joueur : la barrière de SHNetwork les attend. */
    private final Map<UUID, CompletableFuture<Void>> quitSaves = new ConcurrentHashMap<>();

    public NetworkCookies(CookieClicker plugin, NetworkLink link, Duration waitMax) {
        this.plugin = plugin;
        this.link = link;
        this.waitMax = waitMax;
    }

    /** Branche la barrière de passage. */
    public void start() {
        link.registerSaveTask(player -> {
            CompletableFuture<Void> pending = quitSaves.get(player.getUniqueId());
            return pending != null ? pending : CompletableFuture.completedFuture(null);
        });
    }

    /**
     * Attend que l'autre serveur ait fini de sauvegarder le joueur. À appeler hors du fil principal,
     * avant de lire son profil en base.
     */
    public void awaitArrival(UUID uuid, String name) {
        try {
            Boolean saved = link.awaitSaved(uuid, waitMax).get(waitMax.toMillis() + 2000L, TimeUnit.MILLISECONDS);
            if (!Boolean.TRUE.equals(saved)) {
                plugin.getLogger().warning("Départ de " + name + " non confirmé par l'autre serveur : profil relu quand même.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            plugin.getLogger().warning("Départ de " + name + " non confirmé (" + e + ") : profil relu quand même.");
        }
    }

    /** Annonce la sauvegarde de départ d'un joueur ; le futur doit être terminé une fois le profil écrit. */
    public CompletableFuture<Void> beginQuit(UUID uuid) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        quitSaves.put(uuid, done);
        done.whenComplete((value, error) -> quitSaves.remove(uuid, done));
        return done;
    }

    /**
     * Commande d'administration visant un joueur : s'il est connecté sur un autre serveur, elle y est exécutée
     * (là où se trouve son profil) et l'expéditeur en est prévenu.
     *
     * @param playerArg position du pseudo dans la commande (sans le mot {@code cookieclicker})
     * @return vrai si la commande a été transmise
     */
    public boolean forwardIfRemote(CommandSender sender, String[] args, int playerArg) {
        Optional<NetworkLink.Remote> found = link.find(args[playerArg])
            .filter(remote -> !remote.server().equalsIgnoreCase(link.serverName()));
        if (found.isEmpty()) {
            return false;
        }
        NetworkLink.Remote remote = found.get();
        String[] forwarded = args.clone();
        forwarded[playerArg] = remote.name();
        link.runFor(remote.name(), "cookieclicker " + String.join(" ", forwarded));
        LanguageConfig language = plugin.getLanguageConfig();
        sender.sendMessage(language.getTranslatedLanguage(PluginLanguage.NETWORK_FORWARDED)
            .replace("%player%", remote.name()).replace("%server%", remote.server()));
        return true;
    }
}
