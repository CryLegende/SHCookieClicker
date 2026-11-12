package org.Cry_Legende.sHCookieClicker.game;

import org.Cry_Legende.sHCookieClicker.config.Designs;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Effets de coup en cours, joués par une seule tâche du fil principal (aucune tâche par effet). Les particules ne
 * sont envoyées qu'au joueur qui a cliqué.
 */
public final class ParticleAnimator {
    private final ConcurrentLinkedQueue<Running> running = new ConcurrentLinkedQueue<>();

    private static final class Running {
        final Player player;
        final Location center;
        final Designs.Effect design;
        int frame;
        int wait;

        Running(Player player, Location center, Designs.Effect design) {
            this.player = player;
            this.center = center;
            this.design = design;
        }
    }

    void play(Player player, Location blockCorner, Designs.Effect design) {
        running.add(new Running(player, blockCorner.clone().add(0.5, 0.5, 0.5), design));
    }

    public void stop(UUID uuid) {
        running.removeIf(effect -> effect.player.getUniqueId().equals(uuid));
    }

    /** Fil principal, à chaque tick ; ne fait rien sans effet en cours. */
    public void tick() {
        if (running.isEmpty()) {
            return;
        }
        for (Iterator<Running> iterator = running.iterator(); iterator.hasNext(); ) {
            Running effect = iterator.next();
            if (!effect.player.isOnline() || effect.player.getWorld() != effect.center.getWorld()) {
                iterator.remove();
                continue;
            }
            if (effect.wait > 0) {
                effect.wait--;
                continue;
            }
            Animation animation = effect.design.animation();
            animation.frame(effect.frame, (x, y, z, second) -> spawn(effect, x, y, z, second));
            effect.frame++;
            effect.wait = animation.period() - 1;
            if (effect.frame >= animation.frames()) {
                iterator.remove();
            }
        }
    }

    private void spawn(Running effect, double x, double y, double z, boolean second) {
        Location at = effect.center.clone().add(x, y, z);
        Designs.Effect design = effect.design;
        Color color = design.color();
        if (color != null) {
            effect.player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1));
            return;
        }
        Particle particle = second ? design.second() : design.particle();
        Class<?> data = particle.getDataType();
        if (data == Float.class) {
            effect.player.spawnParticle(particle, at, 1, 0, 0, 0, 0, 1.0f);
        } else if (data == Color.class) {
            effect.player.spawnParticle(particle, at, 1, 0, 0, 0, 0, Color.WHITE);
        } else if (data == Particle.DustOptions.class) {
            effect.player.spawnParticle(particle, at, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.WHITE, 1));
        } else {
            effect.player.spawnParticle(particle, at, 1, 0, 0, 0, 0);
        }
    }
}
