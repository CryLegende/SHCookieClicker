package org.Cry_Legende.sHCookieClicker.game;

import org.Cry_Legende.sHCookieClicker.config.MovingCookie;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Places du cookie mobile autour de la place habituelle d'un clicker. Géométrie seule, sans serveur : le monde
 * n'est vu qu'à travers {@link Solid}.
 */
public final class Places {
    /** Pas de la grille des places, en blocs. */
    static final double STEP = 0.25;
    /** Essais pour trouver une place assez loin de la précédente. */
    private static final int TRIES = 24;

    private Places() {
    }

    /** Décalage depuis la place habituelle (centre de l'objet), en blocs. */
    public record Offset(float x, float y, float z) {
        public static final Offset ZERO = new Offset(0, 0, 0);

        double distance(Offset other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** Vrai si le bloc (x, y, z) arrête la zone de clic. */
    @FunctionalInterface
    public interface Solid {
        boolean at(int x, int y, int z);
    }

    /**
     * Places possibles : une grille au quart de bloc dans les écarts permis, sans celles où la zone de clic toucherait
     * un bloc plein.
     *
     * @param cx centre de l'objet à sa place habituelle (de même pour cy, cz)
     */
    public static List<Offset> around(MovingCookie settings, double cx, double cy, double cz, Solid solid) {
        List<Offset> places = new ArrayList<>();
        double half = settings.hitSize() / 2.0;
        int side = (int) Math.floor(settings.side() / STEP + 1e-9);
        int down = (int) Math.floor(settings.down() / STEP + 1e-9);
        int up = (int) Math.floor(settings.up() / STEP + 1e-9);
        for (int i = -side; i <= side; i++) {
            for (int j = -down; j <= up; j++) {
                for (int k = -side; k <= side; k++) {
                    double ox = i * STEP;
                    double oy = j * STEP;
                    double oz = k * STEP;
                    if (free(cx + ox, cy + oy, cz + oz, half, solid)) {
                        places.add(new Offset((float) ox, (float) oy, (float) oz));
                    }
                }
            }
        }
        return places;
    }

    // Aucun bloc plein dans le cube de demi-côté « half » centré en (x, y, z)
    private static boolean free(double x, double y, double z, double half, Solid solid) {
        int x0 = (int) Math.floor(x - half);
        int x1 = (int) Math.floor(x + half - 1e-9);
        int y0 = (int) Math.floor(y - half);
        int y1 = (int) Math.floor(y + half - 1e-9);
        int z0 = (int) Math.floor(z - half);
        int z1 = (int) Math.floor(z + half - 1e-9);
        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    if (solid.at(bx, by, bz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Prochaine place, au hasard, à au moins {@code minDistance} de la précédente ; si aucun essai n'y arrive, la plus
     * éloignée des essais. Sans place possible, la place habituelle.
     */
    public static Offset next(List<Offset> places, Offset previous, double minDistance, RandomGenerator random) {
        if (places.isEmpty()) {
            return Offset.ZERO;
        }
        Offset best = places.getFirst();
        double bestDistance = -1;
        for (int i = 0; i < TRIES; i++) {
            Offset candidate = places.get(random.nextInt(places.size()));
            double distance = candidate.distance(previous);
            if (distance >= minDistance) {
                return candidate;
            }
            if (distance > bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }
}
