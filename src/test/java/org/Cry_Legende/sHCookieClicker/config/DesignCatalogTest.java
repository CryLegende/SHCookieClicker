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

/** Catalogue des blocs à vendre du config.yml du jar : numéros gardés, prix qui montent, têtes valides. */
class DesignCatalogTest {

    private static ConfigurationSection blocks() throws Exception {
        YamlConfiguration jar = new YamlConfiguration();
        try (InputStream input = DesignCatalogTest.class.getResourceAsStream("/config.yml")) {
            jar.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        return jar.getConfigurationSection("designs.blocs");
    }

    @Test
    void ownedDesignsKeepTheirNumber() throws Exception {
        ConfigurationSection blocks = blocks();
        // Acheté par un joueur : le numéro est la clé de la base, il ne change pas de sens
        assertEquals("DRAGON_EGG", blocks.getString("4.bloc"));
        assertEquals(400000, blocks.getLong("4.prix"));
        assertEquals("TETE_JOUEUR", blocks.getString("7.bloc"));
        assertEquals(700000, blocks.getLong("7.prix"));
    }

    @Test
    void menuOrderIsPriceOrder() throws Exception {
        // Le menu range les designs par numéro : les prix doivent monter avec lui
        ConfigurationSection blocks = blocks();
        List<Integer> ids = new ArrayList<>(blocks.getKeys(false).stream().map(Integer::parseInt).sorted().toList());
        long previous = 0;
        for (int id : ids) {
            long price = blocks.getLong(id + ".prix");
            assertTrue(price > previous, "design " + id + " : " + price + " après " + previous);
            previous = price;
        }
        assertTrue(ids.size() <= 7, "une page du menu");
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
