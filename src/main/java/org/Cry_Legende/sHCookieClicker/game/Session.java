package org.Cry_Legende.sHCookieClicker.game;

import java.util.UUID;

/**
 * État de jeu d'un joueur connecté. Il n'est touché que par les événements du joueur (fil de son monde) et par
 * les tâches du fil principal, qui ne tournent jamais en même temps que les mondes.
 */
public final class Session {
    final UUID uuid;
    /** Progression prête (en mode réseau : relue en base après le départ de l'autre serveur). */
    volatile boolean loaded;

    // Anti-autoclic : seau qui se remplit d'un par clic et se vide de « max-par-seconde » par seconde
    private double bucket;
    private long bucketAt;
    // Anti-AFK : dernier changement de bloc
    volatile long movedAt;

    long barAt;
    volatile boolean barPending;
    long deniedAt;
    long menuClickAt;
    long effectReadyAt;
    /** Numéro du clicker en cours de pose (/shcookieclicker set clicker), 0 sinon. */
    int placing;

    volatile long goldenUntil;
    int goldenFrame;
    Explosions.Explosion explosion;

    Session(UUID uuid, long now) {
        this.uuid = uuid;
        this.movedAt = now;
        this.bucketAt = now;
    }

    public UUID uuid() {
        return uuid;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Compte un clic dans le seau ; vrai s'il déborde (clic trop rapide). Les clics refusés le remplissent aussi,
     * jusqu'à deux secondes de clics : il faut ralentir pour que les clics comptent à nouveau, sans attendre plus.
     */
    boolean fill(long now, int perSecond) {
        drain(now, perSecond);
        bucket = Math.min(bucket + 1, 2.0 * perSecond);
        return bucket > perSecond;
    }

    boolean overflowing(long now, int perSecond) {
        drain(now, perSecond);
        return bucket > perSecond;
    }

    private void drain(long now, int perSecond) {
        if (now > bucketAt) {
            bucket = Math.max(0, bucket - (now - bucketAt) * perSecond / 1000.0);
            bucketAt = now;
        }
    }

    boolean idle(long now, long afkMillis) {
        return afkMillis > 0 && now - movedAt > afkMillis;
    }

    boolean golden(long now) {
        return now < goldenUntil;
    }

    /** Un clic de menu à moins de 80 ms du précédent est ignoré (double clic, rafale). */
    public boolean menuThrottled(long now) {
        if (now - menuClickAt < 80) {
            return true;
        }
        menuClickAt = now;
        return false;
    }

    public void moved(long now) {
        movedAt = now;
    }
}
