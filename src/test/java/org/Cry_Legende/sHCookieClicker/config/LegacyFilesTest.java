package org.Cry_Legende.sHCookieClicker.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fichiers de la 1.3 (ceux d'Opale) passés au format 2.0. */
class LegacyFilesTest {

    private static String resource(String name) throws IOException {
        try (InputStream input = LegacyFilesTest.class.getResourceAsStream(name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void legacyFolder(Path folder) throws IOException {
        for (String name : List.of("config.yml", "messages.yml", "menus.yml", "mysql.yml")) {
            Files.writeString(folder.resolve(name), resource("/legacy-1.3/" + name));
        }
    }

    @Test
    void defaultFilesOfTheOldVersionBecomeTheJarFiles(@TempDir Path folder) throws Exception {
        legacyFolder(folder);
        List<String> done = LegacyFiles.migrate(folder.toFile(), resource("/config.yml"), resource("/messages.yml"), resource("/menus.yml"));
        done.forEach(System.out::println);
        assertEquals(resource("/config.yml"), Files.readString(folder.resolve("config.yml")), "réglages de la 1.3 = ceux du jar");
        assertEquals(resource("/messages.yml"), Files.readString(folder.resolve("messages.yml")));
        assertEquals(resource("/menus.yml"), Files.readString(folder.resolve("menus.yml")));
        assertFalse(Files.exists(folder.resolve("mysql.yml")));
        assertEquals(4, done.size());

        assertTrue(LegacyFiles.migrate(folder.toFile(), resource("/config.yml"), resource("/messages.yml"), resource("/menus.yml")).isEmpty(),
                "deuxième démarrage : rien à faire");
    }

    @Test
    void customSettingsAreKept(@TempDir Path folder) throws Exception {
        legacyFolder(folder);
        String config = resource("/legacy-1.3/config.yml")
                .replace("    base_price: 360\n", "    base_price: 400\n")
                .replace("Maximum CPS: 12", "Maximum CPS: 9")
                .replace("Golden cookie range: 10000", "Golden cookie range: 2")
                .replace("MySQL: false", "MySQL: true");
        Files.writeString(folder.resolve("config.yml"), config);
        LegacyFiles.migrate(folder.toFile(), resource("/config.yml"), resource("/messages.yml"), resource("/menus.yml"));
        YamlConfiguration result = YamlConfiguration.loadConfiguration(folder.resolve("config.yml").toFile());
        assertEquals(400, result.getLong("boutique.ameliorations.2.prix"));
        assertEquals(30, result.getLong("boutique.ameliorations.1.prix"));
        assertEquals(9, result.getInt("clics.max-par-seconde"));
        assertEquals(1, result.getInt("cookie-dore.chance"));
        assertEquals("mysql", result.getString("base-de-donnees.type"));
        assertEquals("123+", result.getString("base-de-donnees.mysql.mot-de-passe"));
        assertEquals("ui.toast.in", result.getString("sons.ouverture.son"));
        assertEquals("#471EBA", result.getString("designs.effets.3.couleur"));
        assertEquals("TETE_JOUEUR", result.getString("designs.blocs.7.bloc"));
        assertEquals("ENCHANT", result.getString("designs.effets.5.particule-secondaire"));
    }
}
