package org.Cry_Legende.sHCookieClicker.data;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Écriture des progressions, toujours sur son propre fil (jamais celui du serveur) : les profils modifiés
 * attendent dans une file et partent ensemble, en une transaction, à intervalle régulier, au départ d'un joueur
 * et à l'arrêt.
 */
public final class Saver {
    private final Database database;
    private final Logger logger;
    private final ConcurrentLinkedQueue<Profile> pending = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SHCookieClicker-Sauvegarde");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> periodic;

    public Saver(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /** Appelé par un profil quand il devient à écrire. */
    public void enqueue(Profile profile) {
        pending.add(profile);
    }

    public synchronized void schedule(int seconds) {
        if (periodic != null) {
            periodic.cancel(false);
        }
        int period = Math.max(5, seconds);
        periodic = executor.scheduleWithFixedDelay(this::flushQuietly, period, period, TimeUnit.SECONDS);
    }

    /** Écrit tout ce qui attend, tout de suite ; le futur se termine une fois la transaction validée. */
    public CompletableFuture<Void> flushNow() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                flushQuietly();
                done.complete(null);
            });
        } catch (RuntimeException rejected) {
            done.complete(null);
        }
        return done;
    }

    /** Nombre de profils qui attendent d'être écrits. */
    public int pendingCount() {
        return pending.size();
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Sauvegarde des cookies impossible", exception);
        }
    }

    /** @return le nombre de profils écrits */
    int flush() {
        List<Profile> profiles = new ArrayList<>();
        Profile next;
        while ((next = pending.poll()) != null) {
            profiles.add(next);
        }
        if (profiles.isEmpty()) {
            return 0;
        }
        List<Profile.Row> rows = new ArrayList<>(profiles.size());
        for (Profile profile : profiles) {
            rows.add(profile.takeForSave());
        }
        try {
            database.save(rows);
            return rows.size();
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Écriture de " + rows.size() + " progression(s) impossible, nouvel essai à la prochaine sauvegarde", exception);
            for (int index = 0; index < profiles.size(); index++) {
                profiles.get(index).failed(rows.get(index).purchases() != null);
            }
            return 0;
        }
    }

    /** Arrêt : plus de sauvegarde périodique, puis tout ce qui attend est écrit sur le fil appelant. */
    public void shutdown() {
        synchronized (this) {
            if (periodic != null) {
                periodic.cancel(false);
            }
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                logger.warning("La sauvegarde en cours n'a pas fini à temps.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        int written = flush();
        if (written > 0) {
            logger.info(written + " progression(s) écrite(s) à l'arrêt.");
        }
    }
}
