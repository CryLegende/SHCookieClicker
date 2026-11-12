package org.Cry_Legende.sHCookieClicker.config;

import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.Cry_Legende.sHCookieClicker.game.Animation;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;

/** Les trois sortes de designs : ils s'achètent une fois, puis se sélectionnent. */
public final class Designs {

    private Designs() {
    }

    public sealed interface Design permits Block, Effect, Background {
        int id();

        String name();

        long price();

        ShopKind kind();
    }

    /**
     * Bloc vu par le joueur à la place du clicker.
     *
     * @param texture    texture d'une tête (PLAYER_HEAD), sinon null
     * @param playerHead la tête du joueur lui-même
     */
    public record Block(int id, String name, long price, Material block, String texture, boolean playerHead) implements Design {
        @Override
        public ShopKind kind() {
            return ShopKind.BLOCK_DESIGN;
        }

        public boolean isHead() {
            return playerHead || block == Material.PLAYER_HEAD;
        }
    }

    /**
     * Effet de coup.
     *
     * @param color poussière de cette couleur à la place de la particule, ou null
     */
    public record Effect(int id, String name, long price, Material icon, Animation animation, Particle particle,
                         Particle second, Color color) implements Design {
        @Override
        public ShopKind kind() {
            return ShopKind.HIT_PARTICLE_DESIGN;
        }
    }

    /** Fond des menus : la vitre qui entoure les menus du clicker. */
    public record Background(int id, String name, long price, Material pane) implements Design {
        @Override
        public ShopKind kind() {
            return ShopKind.MENU_DESIGN;
        }

        /** Icône du menu : le bloc de verre de la vitre. */
        public Material icon() {
            Material glass = Material.matchMaterial(pane.name().replace("_PANE", ""));
            return glass != null && glass.isItem() ? glass : pane;
        }
    }
}
