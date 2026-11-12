package org.Cry_Legende.sHCookieClicker.util;

import org.junit.jupiter.api.Test;

import java.text.NumberFormat;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumbersTest {

    @Test
    void placeholderFormatIsTheUsFormat() {
        NumberFormat us = NumberFormat.getIntegerInstance(Locale.US);
        long[] values = {0, 1, 12, 999, 1000, 99999, 100000, 1011876175853L, -1234567, Long.MAX_VALUE, Long.MIN_VALUE};
        for (long value : values) {
            assertEquals(us.format(value), Numbers.placeholder(value), "valeur " + value);
        }
        assertEquals("1,011,876,175,853", Numbers.placeholder(1011876175853L));
    }

    @Test
    void groupWithSpaces() {
        assertEquals("1 011 876 175 853", Numbers.group(1011876175853L, " "));
        assertEquals("999", Numbers.group(999, " "));
        assertEquals("1000", Numbers.group(1000, ""));
    }

    @Test
    void saturatedArithmetic() {
        assertEquals(Long.MAX_VALUE, Numbers.add(Long.MAX_VALUE, 5));
        assertEquals(Long.MIN_VALUE, Numbers.add(Long.MIN_VALUE, -5));
        assertEquals(Long.MAX_VALUE, Numbers.multiply(Long.MAX_VALUE / 2, 3));
        assertEquals(Long.MIN_VALUE, Numbers.multiply(Long.MAX_VALUE / 2, -3));
        assertEquals(1_572_930, Numbers.multiply(786_465, 2));
    }
}
