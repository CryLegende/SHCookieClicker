package org.Cry_Legende.sHCookieClicker.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.util.Numbers;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * %cookieclicker_cookies%, %cookieclicker_perclick%, %cookieclicker_clickerclicks%, %cookieclicker_place%.
 * Les noms ne tiennent pas compte de la casse (%cookieclicker_perClick% marche). Nombres au format 1,234,567 :
 * ajLeaderboards, QuestCreator (%shraw_...%) et les scripts de PlaceholderAPI le relisent. Lus en mémoire, depuis
 * n'importe quel fil.
 */
public final class CookiePlaceholders extends PlaceholderExpansion {
    private static final String UNKNOWN = "N/A";
    private final SHCookieClicker plugin;

    public CookiePlaceholders(SHCookieClicker plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cookieclicker";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Cry_Legende";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !plugin.isReady()) {
            return UNKNOWN;
        }
        Profile profile = plugin.profiles().get(player.getUniqueId());
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "cookies" -> Numbers.placeholder(profile == null ? 0 : profile.cookies());
            case "perclick" -> Numbers.placeholder(profile == null ? 1 : profile.perClick());
            case "clickerclicks" -> Numbers.placeholder(profile == null ? 0 : profile.clickerClicks());
            case "place" -> {
                int place = plugin.ranking().place(player.getUniqueId());
                yield place > 0 ? Numbers.placeholder(place) : UNKNOWN;
            }
            default -> UNKNOWN;
        };
    }
}
