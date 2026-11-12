package de.zillolp.cookieclicker.config.customconfigs;

import de.zillolp.cookieclicker.CookieClicker;

public class MySQLConfig extends CustomConfig {
    public MySQLConfig(CookieClicker plugin, String name) {
        super(plugin, name);
    }

    @Override
    protected boolean usesJarDefaults() {
        return false;
    }
}
