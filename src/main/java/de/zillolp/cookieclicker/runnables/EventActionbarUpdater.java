package de.zillolp.cookieclicker.runnables;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.clickerevents.ClickerEvent;
import de.zillolp.cookieclicker.clickerevents.GoldenCookieEvent;
import de.zillolp.cookieclicker.config.customconfigs.LanguageConfig;
import de.zillolp.cookieclicker.config.customconfigs.PluginConfig;
import de.zillolp.cookieclicker.enums.ClickerEventType;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.manager.ClickerEventManager;
import de.zillolp.cookieclicker.manager.ClickerPlayerManager;
import de.zillolp.cookieclicker.profiles.ClickerGameProfile;
import de.zillolp.cookieclicker.utils.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EventActionbarUpdater extends CustomUpdater {
    private final PluginConfig pluginConfig;
    private final LanguageConfig languageConfig;
    private final ClickerPlayerManager clickerPlayerManager;
    private final ClickerEventManager clickerEventManager;

    public EventActionbarUpdater(CookieClicker plugin) {
        super(plugin, false, 1);
        pluginConfig = plugin.getPluginConfig();
        languageConfig = plugin.getLanguageConfig();
        clickerPlayerManager = plugin.getClickerPlayerManager();
        clickerEventManager = plugin.getClickerEventManager();
    }

    @Override
    protected void tick() {
        HashMap<UUID, HashMap<ClickerEventType, ClickerEvent>> activeEvents = clickerEventManager.getActiveEvents();
        if (activeEvents.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!activeEvents.containsKey(uuid)) {
                continue;
            }
            ClickerGameProfile clickerGameProfile = clickerPlayerManager.getGameProfile(uuid);
            for (Map.Entry<ClickerEventType, ClickerEvent> clickerEventEntry : activeEvents.get(uuid).entrySet()) {
                ClickerEvent clickerEvent = clickerEventEntry.getValue();
                if (clickerEvent == null || clickerEvent.getTime() <= 0) {
                    continue;
                }
                if (!(clickerEvent instanceof GoldenCookieEvent)) {
                    continue;
                }
                for (Location location : plugin.getCookieClickerManager().getClickerLocations()) {
                    if (player.getWorld() != location.getWorld() || player.getLocation().distanceSquared(location) > 25) {
                        continue;
                    }

                    if (clickerGameProfile.isUnderLastPlayerMove(pluginConfig.getAFKCooldownSeconds())) {
                        player.sendActionBar(Text.component(languageConfig.getReplacedLanguage(PluginLanguage.AFK_MESSAGE, uuid)));
                        break;
                    }

                    if (clickerGameProfile.isOverCPS(pluginConfig.getMaximumClicksPerSecond())) {
                        player.sendActionBar(Text.component(languageConfig.getReplacedLanguage(PluginLanguage.MAX_CPS, uuid)));
                        break;
                    }

                    player.sendActionBar(Text.component(languageConfig.getAnimatedLanguage(PluginLanguage.CLICK_MESSAGE, uuid)));
                    break;
                }
            }
        }
    }
}
