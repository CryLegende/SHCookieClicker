package de.zillolp.cookieclicker.enums;

public enum ShopType {
    DEFAULT("default_shop", true),
    PREMIUM("premium_shop", true),
    BLOCK_DESIGN("block_design_shop", false),
    HIT_PARTICLE_DESIGN("hit_particle_design_shop", false),
    MENU_DESIGN("menu_design_shop", false);

    private final String configSection;
    private final boolean repeatable;

    ShopType(String configSection, boolean repeatable) {
        this.configSection = configSection;
        this.repeatable = repeatable;
    }

    public String getConfigSection() {
        return configSection;
    }

    /** Les améliorations se rachètent sans limite, un design s'achète une fois. */
    public boolean isRepeatable() {
        return repeatable;
    }
}
