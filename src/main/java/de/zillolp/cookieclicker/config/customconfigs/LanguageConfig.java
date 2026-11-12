package de.zillolp.cookieclicker.config.customconfigs;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.manager.ClickerPlayerManager;
import de.zillolp.cookieclicker.profiles.ClickerGameProfile;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import de.zillolp.cookieclicker.runnables.StatsWallUpdater;
import de.zillolp.cookieclicker.utils.Text;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** messages.yml : textes envoyés aux joueurs. */
public class LanguageConfig extends CustomConfig {

    public LanguageConfig(CookieClicker plugin, String name) {
        super(plugin, name);
    }

    public String getTranslatedLanguage(String keyName) {
        if (!(fileConfiguration.contains(keyName))) {
            return keyName;
        }
        // getString(clé, défaut) rendrait le défaut sans regarder le messages.yml du jar : la valeur du jar passe avant « N/A »
        String raw = fileConfiguration.getString(keyName);
        String language = Text.color(raw != null ? raw : "N/A");
        language = language.replace("%>%", "»");
        language = language.replace("%<%", "«");
        language = language.replace("%*%", "×");
        language = language.replace("%|%", "┃");
        language = language.replace("%->%", "➜");
        language = language.replace("%_>%", "➥");
        language = language.replace("%!%", "✔");
        return language;
    }

    public String getTranslatedLanguage(PluginLanguage pluginLanguage) {
        return getTranslatedLanguage(pluginLanguage.name());
    }

    public String getLanguageWithPrefix(PluginLanguage pluginLanguage) {
        return getTranslatedLanguage(PluginLanguage.PREFIX) + getTranslatedLanguage(pluginLanguage);
    }

    public String getReplacedLanguageWithPrefix(PluginLanguage pluginLanguage, UUID uuid) {
        return getTranslatedLanguage(PluginLanguage.PREFIX) + getReplacedLanguage(pluginLanguage, uuid);
    }

    /** Remplace %player%, %cookies%, %perclick%, %clickerclicks% et %place% par les valeurs du joueur. */
    public String replaceValues(String language, UUID uuid) {
        ClickerPlayerManager clickerPlayerManager = plugin.getClickerPlayerManager();
        StatsWallUpdater statsWallUpdater = plugin.getStatsWallUpdater();
        if (clickerPlayerManager == null || statsWallUpdater == null) {
            return language;
        }
        ClickerStatsProfile clickerStatsProfile = clickerPlayerManager.getStatsProfile(uuid);
        language = language.replace("%player%", clickerStatsProfile.getName());
        language = language.replace("%cookies%", formatNumber(clickerStatsProfile.getCookies()));
        language = language.replace("%perclick%", formatNumber(clickerStatsProfile.getPerClick()));
        language = language.replace("%clickerclicks%", formatNumber(clickerStatsProfile.getClickerClicks()));
        if (language.contains("%place%")) {
            int place = statsWallUpdater.getAlltimeRank(clickerStatsProfile.getName());
            language = language.replace("%place%", place > 0 ? formatNumber((long) place) : "-");
        }
        return language;
    }

    private String getReplacedLanguage(String keyName, UUID uuid) {
        return replaceValues(getTranslatedLanguage(keyName), uuid);
    }

    public String getReplacedLanguage(PluginLanguage pluginLanguage, UUID uuid) {
        return getReplacedLanguage(pluginLanguage.name(), uuid);
    }

    public String[] getReplaceLanguages(PluginLanguage pluginLanguage, UUID uuid) {
        String keyName = pluginLanguage.name();
        List<String> languages = new ArrayList<>();
        for (String language : getConfigurationSection(keyName)) {
            languages.add(getReplacedLanguage(keyName + "." + language, uuid));
        }
        return languages.toArray(new String[0]);
    }

    public String[] getStatsWallLanguage(String section, String place, String name, String value) {
        ArrayList<String> languages = new ArrayList<>();
        ClickerStatsProfile profile = "?".equals(name) ? null : plugin.getClickerPlayerManager().findProfile(name);
        for (String language : getConfigurationSection(section)) {
            String replacedLanguage = getTranslatedLanguage(section + "." + language);
            replacedLanguage = replacedLanguage.replace("%place%", place);
            replacedLanguage = replacedLanguage.replace("%name%", name);
            replacedLanguage = replacedLanguage.replace("%value%", value);
            if (profile != null) {
                replacedLanguage = replaceValues(replacedLanguage, profile.getUuid());
            } else {
                replacedLanguage = replacedLanguage.replace("%cookies%", "?");
                replacedLanguage = replacedLanguage.replace("%perclick%", "?");
                replacedLanguage = replacedLanguage.replace("%clickerclicks%", "?");
            }
            languages.add(replacedLanguage);
        }
        return languages.toArray(new String[0]);
    }

    public String getAnimatedLanguage(PluginLanguage pluginLanguage, UUID uuid) {
        StringBuilder actionbar = new StringBuilder();
        for (String language : getReplacedLanguage(pluginLanguage, uuid).split("§")) {
            if (!(language.isEmpty())) {
                actionbar.append(language.substring(1));
                continue;
            }
            actionbar.append(language);
        }
        ClickerPlayerManager clickerPlayerManager = plugin.getClickerPlayerManager();
        if (clickerPlayerManager == null) {
            return actionbar.toString();
        }
        ClickerGameProfile clickerGameProfile = clickerPlayerManager.getGameProfile(uuid);
        int frameCount = clickerGameProfile.getFrameCount();
        if (frameCount >= actionbar.length() - 1) {
            frameCount = 0;
        } else {
            frameCount++;
        }
        clickerGameProfile.setFrameCount(frameCount);
        int length = 3;
        String coloredActionbar;
        if (frameCount + length >= actionbar.length()) {
            coloredActionbar = actionbar.substring(frameCount);
        } else {
            coloredActionbar = actionbar.substring(frameCount, frameCount + length) + getTranslatedLanguage("GOLDEN_COOKIE_COLOR.PRIMARY") + actionbar.substring(frameCount + length);
        }
        actionbar = new StringBuilder(getTranslatedLanguage("GOLDEN_COOKIE_COLOR.PRIMARY") + actionbar.substring(0, frameCount) + getTranslatedLanguage("GOLDEN_COOKIE_COLOR.SECONDARY") + coloredActionbar);
        return actionbar.toString();
    }

    public String formatTime(String language, int time) {
        int hours = (time % 86400) / 3600;
        int minutes = ((time % 86400) % 3600) / 60;
        int seconds = ((time % 86400) % 3600) % 60;
        return language.replace("%time%", String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }

    /** Nombre avec une virgule tous les trois chiffres, toujours au format US : les placeholders le relisent. */
    public String formatNumber(Long number) {
        return NumberFormat.getIntegerInstance(Locale.US).format(number);
    }
}
