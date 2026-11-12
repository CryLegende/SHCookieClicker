package org.Cry_Legende.sHCookieClicker.data;

/** Boutiques. Le nom est la valeur de cookieclicker_shops.SHOP_ID : ne pas le changer. */
public enum ShopKind {
    DEFAULT(true),
    PREMIUM(true),
    BLOCK_DESIGN(false),
    HIT_PARTICLE_DESIGN(false),
    MENU_DESIGN(false);

    private final boolean upgrade;

    ShopKind(boolean upgrade) {
        this.upgrade = upgrade;
    }

    /** Amélioration (se rachète, ITEMS_PURCHASED = nombre d'achats) ou design (un achat, ITEMS_PURCHASED = 1). */
    public boolean isUpgrade() {
        return upgrade;
    }

    public static ShopKind parse(String id) {
        for (ShopKind kind : values()) {
            if (kind.name().equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
