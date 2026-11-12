package de.zillolp.cookieclicker.enums;

import net.kyori.adventure.sound.Sound;

/** Sons du plugin. Le nom de la clé de config.yml (section sounds) est le nom en minuscules. */
public enum SoundType {
    CLICK_ALLOW,
    CLICK_DENY,
    OPEN_INVENTORY,
    SHOP,
    DESIGN,
    BLOCK_DESIGN,
    HIT_PARTICLE_DESIGN,
    MENU_DESIGN,
    NEXT,
    BACK,
    PREMIUM_ALLOW,
    PREMIUM_DENY,
    BUY_ALLOW,
    BUY_DENY,
    SELECT_DESIGN,
    REMOVE_DESIGN,
    STATS_INFO,
    GOLDEN_COOKIE,
    COOKIE_EXPLOSION;

    private volatile Sound sound;

    /** Le son chargé depuis la configuration, ou null s'il est muet. */
    public Sound getSound() {
        return sound;
    }

    public void setSound(Sound sound) {
        this.sound = sound;
    }
}
