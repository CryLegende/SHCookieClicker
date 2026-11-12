package org.Cry_Legende.sHCookieClicker.util;

import java.util.ArrayList;
import java.util.List;

/** Cases d'un menu écrites « 10 », « 10-16, 19-25 » ou en liste YAML. */
public final class Slots {

    private Slots() {
    }

    public static List<Integer> parse(Object value) {
        List<Integer> slots = new ArrayList<>();
        if (value == null) {
            return slots;
        }
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                slots.addAll(parse(entry));
            }
            return slots;
        }
        for (String part : value.toString().split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int dash = trimmed.indexOf('-', 1);
            try {
                if (dash > 0) {
                    int from = Integer.parseInt(trimmed.substring(0, dash).trim());
                    int to = Integer.parseInt(trimmed.substring(dash + 1).trim());
                    for (int slot = Math.min(from, to); slot <= Math.max(from, to); slot++) {
                        slots.add(slot);
                    }
                } else {
                    slots.add(Integer.parseInt(trimmed));
                }
            } catch (NumberFormatException ignored) {
                // case illisible : ignorée
            }
        }
        return slots;
    }
}
