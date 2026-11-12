package de.zillolp.cookieclicker.placeholder;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.config.customconfigs.LanguageConfig;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PlaceholderListener extends PlaceholderExpansion {
    private final CookieClicker plugin;

    public PlaceholderListener(CookieClicker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @NotNull String getAuthor() {
        return "ZilloLP";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cookieclicker";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if (player == null || plugin.getClickerPlayerManager() == null) {
            return "N/A";
        }
        ClickerStatsProfile clickerStatsProfile = plugin.getClickerPlayerManager().getStatsProfile(player.getUniqueId());
        LanguageConfig languageConfig = plugin.getLanguageConfig();
        switch (identifier.toUpperCase()) {
            case "COOKIES":
                // %cookieclicker_cookies%
                return languageConfig.formatNumber(clickerStatsProfile.getCookies());
            case "PERCLICK":
                // %cookieclicker_perclick%
                return languageConfig.formatNumber(clickerStatsProfile.getPerClick());
            case "CLICKERCLICKS":
                // %cookieclicker_clickerclicks%
                return languageConfig.formatNumber(clickerStatsProfile.getClickerClicks());
            case "PLACE":
                // %cookieclicker_place%
                if (plugin.getStatsWallUpdater() == null) {
                    break;
                }
                int place = plugin.getStatsWallUpdater().getAlltimeRank(clickerStatsProfile.getName());
                if (place <= 0) {
                    break;
                }
                return languageConfig.formatNumber((long) place);
        }
        return "N/A";
    }
}
