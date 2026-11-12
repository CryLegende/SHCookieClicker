package org.Cry_Legende.sHCookieClicker.config;

import org.Cry_Legende.sHCookieClicker.util.Numbers;
import org.bukkit.Material;

/**
 * Amélioration d'une boutique : chaque achat ajoute {@code gain} cookies par clic, le prix du n-ième achat
 * (n à partir de 0) vaut {@code price + n × step}.
 */
public record Upgrade(int id, Material icon, long price, long step, long gain) {

    public long priceAt(long purchases) {
        return Numbers.add(price, Numbers.multiply(Math.max(0, purchases), step));
    }

    /** Prix de {@code count} achats à la suite, à partir de {@code purchases} achats déjà faits. */
    public long priceOf(long purchases, int count) {
        long total = 0;
        for (int index = 0; index < count; index++) {
            total = Numbers.add(total, priceAt(purchases + index));
        }
        return total;
    }
}
