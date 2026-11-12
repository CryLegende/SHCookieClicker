package org.Cry_Legende.sHCookieClicker.network;

import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Ce que le mode réseau demande à SHNetwork. Aucune classe de SHNetwork n'y apparaît : sans SHNetwork,
 * {@link ShNetworkLink} n'est jamais chargée.
 */
public interface NetworkLink {

    /** Joueur connecté quelque part sur le réseau. */
    record Remote(UUID uuid, String name, String server) {
    }

    String serverName();

    /** Se termine (hors du fil principal) quand le serveur de départ a fini d'écrire le joueur. */
    CompletableFuture<Boolean> awaitSaved(UUID uuid, Duration timeout);

    /** Tâche lancée au départ de chaque joueur ; le passage attend son futur. */
    void registerSaveTask(Function<Player, CompletableFuture<Void>> task);

    Optional<Remote> find(String nameOrUuid);

    /** Commande console exécutée là où se trouve le joueur. */
    void runFor(String nameOrUuid, String consoleCommand);
}
