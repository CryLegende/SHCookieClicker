package org.Cry_Legende.sHCookieClicker.game;

import org.Cry_Legende.sHCookieClicker.config.Appearance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Géométrie de l'objet du clicker : repos, bond, centrage d'un bloc affiché. */
class PoseTest {
    private static final Appearance LOOK = new Appearance("texture", 40, 0.5, 1.0f, 0.75f, 1.6f, 1.6f, 160, 0.08, 60, 5, 0.2, 0.12);

    /** Tourne (x, y, z) par le quaternion (0, qy, 0, qw), comme le client le fait. */
    private static double[] rotate(Pose.Frame frame, double x, double y, double z) {
        double qy = frame.qy();
        double qw = frame.qw();
        return new double[]{x + 2 * qw * qy * z - 2 * qy * qy * x, y, z - 2 * qw * qy * x - 2 * qy * qy * z};
    }

    /** Position finale d'un point (u, v, w) du modèle : translation + rotation × (taille × point). */
    private static double[] place(Pose.Frame frame, double u, double v, double w) {
        double[] turned = rotate(frame, u * frame.scale(), v * frame.scale(), w * frame.scale());
        return new double[]{frame.tx() + turned[0], frame.ty() + turned[1], frame.tz() + turned[2]};
    }

    @Test
    void blockTurnsAndGrowsAroundItsCentre() {
        for (long tick = 0; tick < 400; tick += 7) {
            for (double pop : new double[]{0, 0.5, 1}) {
                Pose.Frame frame = Pose.at(LOOK, true, tick, pop);
                double[] centre = place(frame, 0.5, 0.5, 0.5);
                double lift = LOOK.bob() * Math.sin(Math.PI * 2 * (tick % 60) / 60) + LOOK.popJump() * pop;
                assertEquals(0, centre[0], 1e-5, "x au tick " + tick);
                assertEquals(lift, centre[1], 1e-5, "y au tick " + tick);
                assertEquals(0, centre[2], 1e-5, "z au tick " + tick);
            }
        }
    }

    @Test
    void headStaysOnItsOrigin() {
        Pose.Frame frame = Pose.at(LOOK, false, 33, 0);
        assertEquals(0, frame.tx(), 1e-6);
        assertEquals(0, frame.tz(), 1e-6);
        assertEquals(1.0f, frame.scale(), 1e-6);
    }

    @Test
    void restRepeatsEachRevolution() {
        Pose.Frame a = Pose.at(LOOK, false, 21, 0);
        Pose.Frame b = Pose.at(LOOK, false, 21 + 160 * 3, 0);
        assertEquals(a.qy(), b.qy(), 1e-5);
        assertEquals(a.qw(), b.qw(), 1e-5);
        assertEquals(Pose.at(LOOK, false, 10, 0).ty(), Pose.at(LOOK, false, 10 + 60, 0).ty(), 1e-6);
    }

    @Test
    void oneTurnIsMadeOfSmallSteps() {
        // Entre deux envois le client tourne par le plus court chemin : chaque pas reste bien en dessous d'un demi-tour
        double step = 360.0 * LOOK.stepTicks() / LOOK.revolutionTicks();
        assertTrue(step < 90, "pas de " + step + " degrés");
    }

    @Test
    void popMakesItBiggerAndHigher() {
        Pose.Frame rest = Pose.at(LOOK, false, 40, 0);
        Pose.Frame top = Pose.at(LOOK, false, 40, 1);
        assertEquals(1.2f, top.scale(), 1e-5);
        assertEquals(rest.ty() + 0.12f, top.ty(), 1e-5);
    }

    @Test
    void rotationIsAUnitQuaternion() {
        for (long tick = 0; tick < 320; tick += 13) {
            Pose.Frame frame = Pose.at(LOOK, true, tick, 0);
            assertEquals(1.0, frame.qy() * frame.qy() + frame.qw() * frame.qw(), 1e-6);
        }
    }
}
