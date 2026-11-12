package org.Cry_Legende.sHCookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Catalogue des blocs à vendre du config.yml du jar : numéros gardés, les plus chers en haut, têtes valides. */
class DesignCatalogTest {

    private static ConfigurationSection blocks() throws Exception {
        YamlConfiguration jar = new YamlConfiguration();
        try (InputStream input = DesignCatalogTest.class.getResourceAsStream("/config.yml")) {
            jar.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        return jar.getConfigurationSection("designs.blocs");
    }

    @Test
    void ownedDesignsKeepTheirNumberAndAreTheRarest() throws Exception {
        ConfigurationSection blocks = blocks();
        // Acheté par un joueur : le numéro est la clé de la base, il ne change pas de sens
        assertEquals("DRAGON_EGG", blocks.getString("4.bloc"));
        assertEquals("TETE_JOUEUR", blocks.getString("7.bloc"));
        // L'œuf de dragon et la tête du joueur sont les deux designs les plus chers
        List<String> byPrice = new ArrayList<>(blocks.getKeys(false));
        byPrice.sort((x, y) -> Long.compare(blocks.getLong(y + ".prix"), blocks.getLong(x + ".prix")));
        assertEquals(List.of("4", "7"), byPrice.subList(0, 2).stream().sorted().toList());
        assertTrue(blocks.getLong("4.prix") > blocks.getLong(byPrice.get(2) + ".prix"));
    }

    @Test
    void oneMenuPage() throws Exception {
        assertTrue(blocks().getKeys(false).size() <= 7, "une page du menu");
    }

    @Test
    void everyDesignIsUsable() throws Exception {
        ConfigurationSection blocks = blocks();
        for (String id : blocks.getKeys(false)) {
            String block = blocks.getString(id + ".bloc");
            if (block.equals("TETE_JOUEUR")) {
                continue;
            }
            assertTrue(block.matches("[A-Z_]+"), id + " : " + block);
            if (block.equals("PLAYER_HEAD")) {
                assertTrue(blocks.getString(id + ".texture", "").matches("http://textures[.]minecraft[.]net/texture/[0-9a-f]{64}"), id + " : texture");
            }
        }
    }
}
