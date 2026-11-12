package org.Cry_Legende.sHCookieClicker.game;

import org.Cry_Legende.sHCookieClicker.config.MovingCookie;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Places du cookie mobile : grille, blocs pleins écartés, distance entre deux places de suite. */
class PlacesTest {
    private static final MovingCookie SETTINGS = new MovingCookie(3, 0.8f, 1.0, 0.25, 0.75, 1.0, 2);
    // Centre habituel de l'objet : clicker en (-99, 89, 53), hauteur 0,5
    private static final double CX = -98.5;
    private static final double CY = 90.0;
    private static final double CZ = 53.5;

    @Test
    void openSpaceGivesTheWholeGrid() {
        List<Places.Offset> places = Places.around(SETTINGS, CX, CY, CZ, (x, y, z) -> false);
        // 9 × 5 × 9 : de -1 à 1 par quart de côté, de -0,25 à 0,75 en hauteur
        assertEquals(9 * 5 * 9, places.size());
        assertTrue(places.contains(Places.Offset.ZERO));
        assertTrue(places.contains(new Places.Offset(-1f, -0.25f, 1f)));
        assertTrue(places.contains(new Places.Offset(1f, 0.75f, -1f)));
    }

    @Test
    void placesTouchingASolidBlockAreLeftOut() {
        // Mur plein en x = -98 (toute la colonne) : la zone (demi-côté 0,4) ne doit jamais y entrer
        List<Places.Offset> places = Places.around(SETTINGS, CX, CY, CZ, (x, y, z) -> x == -98);
        assertFalse(places.isEmpty());
        for (Places.Offset offset : places) {
            assertTrue(CX + offset.x() + 0.4 <= -98.0 + 1e-6, "place " + offset + " dans le mur");
        }
        // Bloc plein sous la case du clicker (88) : la zone la plus basse descend à 89,35, toute la grille reste
        assertEquals(9 * 5 * 9, Places.around(SETTINGS, CX, CY, CZ, (x, y, z) -> y == 88).size());
        // Tout plein : aucune place
        assertTrue(Places.around(SETTINGS, CX, CY, CZ, (x, y, z) -> true).isEmpty());
    }

    @Test
    void nextPlaceIsFarEnoughFromThePreviousOne() {
        List<Places.Offset> places = Places.around(SETTINGS, CX, CY, CZ, (x, y, z) -> false);
        Random random = new Random(7);
        Places.Offset previous = Places.Offset.ZERO;
        Set<Places.Offset> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            Places.Offset next = Places.next(places, previous, SETTINGS.minDistance(), random);
            assertTrue(next.distance(previous) >= SETTINGS.minDistance(), "de " + previous + " à " + next);
            seen.add(next);
            previous = next;
        }
        assertTrue(seen.size() > 100, "places variées : " + seen.size());
    }

    @Test
    void withoutAnyPlaceTheObjectStays() {
        assertEquals(Places.Offset.ZERO, Places.next(List.of(), new Places.Offset(1, 0, 0), 1.0, new Random(1)));
        // Une seule place, trop proche : elle est tout de même prise
        Places.Offset only = new Places.Offset(0.25f, 0, 0);
        assertEquals(only, Places.next(List.of(only), Places.Offset.ZERO, 1.0, new Random(1)));
    }

    @Test
    void shiftedPoseKeepsTurnAndSize() {
        Pose.Frame frame = new Pose.Frame(0.1f, 0.2f, 0.3f, 0.5f, 0.8f, 1.2f);
        Pose.Frame moved = frame.shifted(new Places.Offset(1f, -0.25f, 0.5f));
        assertEquals(1.1f, moved.tx(), 1e-6);
        assertEquals(-0.05f, moved.ty(), 1e-6);
        assertEquals(0.8f, moved.tz(), 1e-6);
        assertEquals(frame.qy(), moved.qy());
        assertEquals(frame.qw(), moved.qw());
        assertEquals(frame.scale(), moved.scale());
    }

    @Test
    void jarFileMatchesCodeDefaults() throws Exception {
        YamlConfiguration jar = new YamlConfiguration();
        try (InputStream input = PlacesTest.class.getResourceAsStream("/config.yml")) {
            jar.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        Method parse = MovingCookie.class.getDeclaredMethod("parse", org.bukkit.configuration.ConfigurationSection.class);
        parse.setAccessible(true);
        assertEquals(parse.invoke(null, (Object) null), parse.invoke(null, jar.getConfigurationSection("cookie-mobile")));
        assertEquals(SETTINGS, parse.invoke(null, jar.getConfigurationSection("cookie-mobile")));
    }
}
