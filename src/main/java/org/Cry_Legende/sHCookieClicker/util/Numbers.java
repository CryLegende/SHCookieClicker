package org.Cry_Legende.sHCookieClicker.util;

/** Nombres : calculs saturés (jamais de dépassement de long) et écriture groupée par milliers. */
public final class Numbers {

    private Numbers() {
    }

    public static long add(long a, long b) {
        long sum = a + b;
        if (((a ^ sum) & (b ^ sum)) < 0) {
            return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return sum;
    }

    public static long multiply(long a, long b) {
        long high = Math.multiplyHigh(a, b);
        long low = a * b;
        if ((high == 0 && low >= 0) || (high == -1 && low < 0)) {
            return low;
        }
        return (a < 0) == (b < 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
    }

    /** 1,011,876,175,853 : format des placeholders, relu tel quel par ajLeaderboards, QuestCreator et les scripts de PlaceholderAPI. */
    public static String placeholder(long value) {
        return group(value, ",");
    }

    /** Nombre groupé par milliers avec le séparateur donné. */
    public static String group(long value, String separator) {
        String digits = Long.toString(value);
        int start = value < 0 ? 1 : 0;
        int length = digits.length() - start;
        if (length <= 3 || separator.isEmpty()) {
            return digits;
        }
        StringBuilder out = new StringBuilder(digits.length() + (length / 3) * separator.length());
        out.append(digits, 0, start);
        int head = length % 3 == 0 ? 3 : length % 3;
        out.append(digits, start, start + head);
        for (int index = start + head; index < digits.length(); index += 3) {
            out.append(separator).append(digits, index, index + 3);
        }
        return out.toString();
    }
}
