package org.Cry_Legende.sHCookieClicker.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Le menu range les designs par prix, à prix égal par numéro, quel que soit leur numéro. */
class DesignOrderTest {

    @Test
    void sortedByPriceThenNumber() {
        Map<Integer, Long> designs = new LinkedHashMap<>();
        designs.put(4, 2_000L);
        designs.put(7, 10_000L);
        designs.put(3, 250L);
        designs.put(9, 2_000L);
        designs.put(8, 50L);
        assertEquals(List.of(8, 3, 4, 9, 7), List.copyOf(Settings.byPrice(designs, Long::longValue).keySet()));
    }
}
