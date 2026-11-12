package org.Cry_Legende.sHCookieClicker.game;

/**
 * Animations des effets de coup, autour du centre du clicker. Chaque image pose quelques particules ; une
 * animation dure {@code frames × period} ticks et ne peut pas se relancer avant d'être finie.
 */
public enum Animation {
    /** Double hélice qui monte du pied du clicker. */
    SPIRALE(48, 1) {
        @Override
        void frame(int frame, Sink sink) {
            double angle = frame * 0.42;
            double y = -0.5 + frame * 0.055;
            for (int arm = 0; arm < 2; arm++) {
                double a = angle + arm * Math.PI;
                sink.point(Math.cos(a) * 0.75, y, Math.sin(a) * 0.75, false);
            }
        }
    },
    /** Anneaux qui s'élargissent en montant. */
    ANNEAUX(6, 4) {
        @Override
        void frame(int frame, Sink sink) {
            double radius = 0.65 + frame * 0.15;
            double y = -0.3 + frame * 0.35;
            for (int point = 0; point < 18; point++) {
                double a = point * (Math.PI * 2 / 18);
                sink.point(Math.cos(a) * radius, y, Math.sin(a) * radius, false);
            }
        }
    },
    /** Onde au sol qui part du clicker. */
    ONDE(9, 2) {
        @Override
        void frame(int frame, Sink sink) {
            double radius = 0.6 + frame * 0.2;
            int points = 12 + frame * 2;
            for (int point = 0; point < points; point++) {
                double a = point * (Math.PI * 2 / points);
                sink.point(Math.cos(a) * radius, -0.45, Math.sin(a) * radius, false);
            }
        }
    },
    /** Étoile à cinq branches qui tourne en s'ouvrant. */
    ETOILE(14, 2) {
        @Override
        void frame(int frame, Sink sink) {
            double radius = 0.45 + frame * 0.11;
            double y = -0.4 + frame * 0.17;
            for (int arm = 0; arm < 5; arm++) {
                double a = Math.toRadians(arm * 72 + frame * 12);
                sink.point(Math.cos(a) * radius, y, Math.sin(a) * radius, false);
            }
        }
    },
    /** Tourbillon : deux traînées qui tournent, la seconde particule plus près du centre. */
    TOURBILLON(40, 1) {
        @Override
        void frame(int frame, Sink sink) {
            double t = frame * 0.1;
            double angle = t * 2.4;
            double radius = 0.9 + Math.sin(t * 1.5) * 0.3;
            double y = Math.sin(t * 1.2) * 0.45;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            sink.point(x, y, z, false);
            sink.point(-x, y, -z, false);
            sink.point(x * 0.5, y + 0.4, z * 0.5, true);
            sink.point(-x * 0.5, y + 0.4, -z * 0.5, true);
        }
    };

    private final int frames;
    private final int period;

    Animation(int frames, int period) {
        this.frames = frames;
        this.period = period;
    }

    public int frames() {
        return frames;
    }

    /** Ticks entre deux images. */
    public int period() {
        return period;
    }

    /** Durée de l'animation, en millisecondes. */
    public long durationMillis() {
        return frames * period * 50L;
    }

    /** Pose les particules d'une image (décalages depuis le centre du clicker). */
    abstract void frame(int frame, Sink sink);

    interface Sink {
        void point(double x, double y, double z, boolean second);
    }

    public static Animation parse(String name) {
        if (name == null) {
            return null;
        }
        for (Animation animation : values()) {
            if (animation.name().equalsIgnoreCase(name.trim())) {
                return animation;
            }
        }
        return null;
    }
}
