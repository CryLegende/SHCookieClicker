package org.Cry_Legende.sHCookieClicker.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Anti-autoclic et anti-AFK. */
class SessionTest {

    private static int refused(int clicksPerSecond, int seconds, int max) {
        Session session = new Session(UUID.randomUUID(), 0);
        int refused = 0;
        long interval = 1000 / clicksPerSecond;
        for (long time = 0; time < seconds * 1000L; time += interval) {
            if (session.fill(time, max)) {
                refused++;
            }
        }
        return refused;
    }

    @Test
    void humanSpeedIsNeverRefused() {
        assertEquals(0, refused(10, 60, 12));
        assertEquals(0, refused(12, 60, 12));
    }

    @Test
    void autoclickerIsRefused() {
        int total = 20 * 60;
        int refused = refused(20, 60, 12);
        assertTrue(refused > total * 0.35, "refusés : " + refused);
    }

    @Test
    void idlePlayerIsRefused() {
        Session session = new Session(UUID.randomUUID(), 0);
        assertFalse(session.idle(14_000, 15_000));
        assertTrue(session.idle(16_000, 15_000));
        session.moved(16_000);
        assertFalse(session.idle(20_000, 15_000));
        assertFalse(session.idle(1_000_000, 0), "0 : désactivé");
    }
}
