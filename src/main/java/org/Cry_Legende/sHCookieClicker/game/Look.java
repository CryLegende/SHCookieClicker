package org.Cry_Legende.sHCookieClicker.game;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Material;

/**
 * Ce que le joueur voit flotter au-dessus du clicker : un bloc ou une tête. {@code key} identifie le design
 * (deux joueurs qui ont le même design ont la même clé) ; un changement de clé remplace l'entité.
 *
 * @param block   le bloc affiché, null pour une tête
 * @param profile le profil de la tête affichée, null pour un bloc
 */
public record Look(String key, Material block, PlayerProfile profile) {

    public boolean isBlock() {
        return block != null;
    }

    public static Look ofBlock(Material block) {
        return new Look("bloc:" + block.name(), block, null);
    }

    public static Look ofHead(String key, PlayerProfile profile) {
        return new Look("tete:" + key, null, profile);
    }
}
