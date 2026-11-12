package de.zillolp.cookieclicker.listener;

import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.clickerevents.ClickerEvent;
import de.zillolp.cookieclicker.clickerevents.CookieExplosionEvent;
import de.zillolp.cookieclicker.config.customconfigs.LanguageConfig;
import de.zillolp.cookieclicker.config.customconfigs.PluginConfig;
import de.zillolp.cookieclicker.enums.ClickerEventType;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.holograms.MovingHologram;
import de.zillolp.cookieclicker.manager.ClickerEventManager;
import de.zillolp.cookieclicker.manager.ClickerPlayerManager;
import de.zillolp.cookieclicker.manager.HologramManager;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import de.zillolp.cookieclicker.utils.Text;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CookieExplosionListener implements Listener {
    private final CookieClicker plugin;
    private final PluginConfig pluginConfig;
    private final LanguageConfig languageConfig;
    private final ClickerPlayerManager clickerPlayerManager;
    private final HologramManager hologramManager;
    private final ClickerEventManager clickerEventManager;

    public CookieExplosionListener(CookieClicker plugin) {
        this.plugin = plugin;
        pluginConfig = plugin.getPluginConfig();
        languageConfig = plugin.getLanguageConfig();
        clickerPlayerManager = plugin.getClickerPlayerManager();
        hologramManager = plugin.getHologramManager();
        clickerEventManager = plugin.getClickerEventManager();
    }

    // Les cookies d'une explosion sont créés invisibles pour les autres joueurs (setVisibleByDefault) et le restent
    // pour ceux qui se connectent ou changent de monde.

    @EventHandler
    public void onPlayerAttemptPickupItemEvent(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Item item = event.getItem();

        for (Map.Entry<UUID, HashMap<ClickerEventType, ClickerEvent>> activeClickerEvents : clickerEventManager.getActiveEvents().entrySet()) {
            HashMap<ClickerEventType, ClickerEvent> clickerEvents = activeClickerEvents.getValue();
            if (clickerEvents == null) {
                continue;
            }

            ClickerEvent clickerEvent = clickerEvents.get(ClickerEventType.COOKIE_EXPLOSION);
            if (clickerEvent == null || clickerEvent.getTime() <= 0) {
                continue;
            }

            CookieExplosionEvent cookieExplosionEvent = (CookieExplosionEvent) clickerEvent;
            ArrayList<Item> items = cookieExplosionEvent.getItems();
            if (!items.contains(item)) {
                continue;
            }

            event.setCancelled(true);

            if (!activeClickerEvents.getKey().equals(uuid)) {
                return;
            }

            player.playPickupItemAnimation(item, item.getItemStack().getAmount());
            item.remove();
            items.remove(item);

            ClickerStatsProfile clickerStatsProfile = clickerPlayerManager.getStatsProfile(uuid);
            long cookiesPerCookie = clickerStatsProfile.getPerClick() * pluginConfig.getCookiesPerCookieMultiplier();
            clickerStatsProfile.addCookies(cookiesPerCookie);
            hologramManager.spawnHologram(player, new MovingHologram(plugin, languageConfig.getReplacedLanguage(PluginLanguage.COLLECTED_COOKIE, uuid)
                    .replace("%collectedcookies%", languageConfig.formatNumber(cookiesPerCookie)), 20, 0.10), item.getLocation());
            player.sendActionBar(Text.component(languageConfig.getReplacedLanguage(PluginLanguage.COOKIE_COLLECT_MESSAGE, uuid)
                    .replace("%collectedcookies%", languageConfig.formatNumber(cookiesPerCookie))));
            return;
        }
    }

    @EventHandler
    public void onItemMergeEvent(ItemMergeEvent event) {
        Item item = event.getEntity();
        for (HashMap<ClickerEventType, ClickerEvent> clickerEvents : clickerEventManager.getActiveEvents().values()) {
            if (clickerEvents == null) {
                continue;
            }

            ClickerEvent clickerEvent = clickerEvents.get(ClickerEventType.COOKIE_EXPLOSION);
            if (clickerEvent == null || clickerEvent.getTime() <= 0) {
                continue;
            }

            CookieExplosionEvent cookieExplosionEvent = (CookieExplosionEvent) clickerEvent;
            if (cookieExplosionEvent.getItems().contains(item)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
