package org.Cry_Legende.sHCookieClicker.game;

import org.Cry_Legende.sHCookieClicker.config.Appearance;

/**
 * Position et forme de l'objet du clicker à un instant : le repos (il tourne sur lui-même et monte et descend
 * doucement) et le bond d'un clic (il grossit et monte un instant). Géométrie seule, sans serveur.
 * <p>
 * L'instant est un tick du serveur, donc tous les joueurs voient la même phase. Le client lisse entre deux poses
 * envoyées ; l'objet est toujours centré sur son entité : un bloc affiché a son coin pour origine, la translation
 * du bloc compense la rotation et la taille pour qu'il tourne et grossisse autour de son centre.
 */
public final class Pose {

    private Pose() {
    }

    /**
     * Transformation d'une entité d'affichage : translation, rotation autour de l'axe vertical (quaternion
     * {@code (0, qy, 0, qw)}) et taille.
     */
    public record Frame(float tx, float ty, float tz, float qy, float qw, float scale) {

        /** La même pose, déplacée de {@code offset} (cookie mobile). */
        public Frame shifted(Places.Offset offset) {
            return new Frame(tx + offset.x(), ty + offset.y(), tz + offset.z(), qy, qw, scale);
        }
    }

    /**
     * @param block vrai pour un bloc affiché (origine au coin), faux pour une tête (origine au centre)
     * @param tick  instant, en ticks
     * @param pop   force du bond : 0 au repos, 1 au sommet
     */
    public static Frame at(Appearance look, boolean block, long tick, double pop) {
        double angle = Math.PI * 2 * Math.floorMod(tick, (long) look.revolutionTicks()) / look.revolutionTicks();
        double bob = look.bob() * Math.sin(Math.PI * 2 * Math.floorMod(tick, (long) look.bobTicks()) / look.bobTicks());
        double scale = (block ? look.blockSize() : look.headSize()) * (1 + look.popGrow() * pop);
        double y = bob + look.popJump() * pop;
        double x = 0;
        double z = 0;
        if (block) {
            // Le centre du bloc (0,5 ; 0,5 ; 0,5) tourné autour de Y puis réduit doit tomber sur l'origine de l'entité
            double half = scale / 2;
            x = -half * (Math.cos(angle) + Math.sin(angle));
            z = -half * (Math.cos(angle) - Math.sin(angle));
            y -= half;
        }
        return new Frame((float) x, (float) y, (float) z, (float) Math.sin(angle / 2), (float) Math.cos(angle / 2), (float) scale);
    }
}
