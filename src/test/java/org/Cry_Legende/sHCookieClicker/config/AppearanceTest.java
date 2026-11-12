package org.Cry_Legende.sHCookieClicker.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Réglages de l'apparence : le config.yml du jar et les valeurs par défaut du code disent la même chose. */
class AppearanceTest {

    @Test
    void jarFileMatchesCodeDefaults() throws Exception {
        YamlConfiguration jar = new YamlConfiguration();
        try (InputStream input = AppearanceTest.class.getResourceAsStream("/config.yml")) {
            jar.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        List<String> warnings = new ArrayList<>();
        assertEquals(Appearance.parse(null, warnings), Appearance.parse(jar.getConfigurationSection("apparence"), warnings));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void nonsenseValuesAreBounded() {
        YamlConfiguration file = new YamlConfiguration();
        file.set("rayon", -5);
        file.set("repos.tour-secondes", -3);
        file.set("repos.fluidite-ticks", 0);
        file.set("coup.gonflement", 50);
        List<String> warnings = new ArrayList<>();
        Appearance look = Appearance.parse(file, warnings);
        assertEquals(8, look.radius());
        assertEquals(160, look.revolutionTicks());
        assertEquals(1, look.stepTicks());
        assertEquals(1.5, look.popGrow(), 1e-9);
    }
}
