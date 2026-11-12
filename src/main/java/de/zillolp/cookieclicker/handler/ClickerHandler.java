package de.zillolp.cookieclicker.handler;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.clickerevents.CookieExplosionEvent;
import de.zillolp.cookieclicker.clickerevents.GoldenCookieEvent;
import de.zillolp.cookieclicker.config.customconfigs.LanguageConfig;
import de.zillolp.cookieclicker.config.customconfigs.PluginConfig;
import de.zillolp.cookieclicker.customevents.CookieClickerInteractEvent;
import de.zillolp.cookieclicker.enums.ClickerEventType;
import de.zillolp.cookieclicker.enums.PluginLanguage;
import de.zillolp.cookieclicker.enums.ShopType;
import de.zillolp.cookieclicker.enums.SoundType;
import de.zillolp.cookieclicker.manager.ClickerEventManager;
import de.zillolp.cookieclicker.manager.ClickerPlayerManager;
import de.zillolp.cookieclicker.manager.DesignManager;
import de.zillolp.cookieclicker.manager.SoundManager;
import de.zillolp.cookieclicker.menu.HomeMenu;
import de.zillolp.cookieclicker.profiles.ClickerGameProfile;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import de.zillolp.cookieclicker.utils.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Random;
import java.util.UUID;

/** Clics sur le clicker. Tout se passe sur le fil principal : la progression est en mémoire, la base n'est pas touchée. */
public class ClickerHandler {
    private static final Title.Times EVENT_TITLE_TIMES = Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(1));
    private final CookieClicker plugin;
    private final PluginConfig pluginConfig;
    private final LanguageConfig languageConfig;
    private final DesignManager designManager;
    private final SoundManager soundManager;
    private final ClickerPlayerManager clickerPlayerManager;
    private final Random random = new Random();

    public ClickerHandler(CookieClicker plugin) {
        this.plugin = plugin;
        pluginConfig = plugin.getPluginConfig();
        languageConfig = plugin.getLanguageConfig();
        designManager = plugin.getDesignManager();
        soundManager = plugin.getSoundManager();
        clickerPlayerManager = plugin.getClickerPlayerManager();
    }

    public void handleClickerInteract(CookieClickerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ClickerGameProfile clickerGameProfile = clickerPlayerManager.getGameProfile(uuid);
        if (!(clickerGameProfile.isLoaded())) {
            return;
        }
        ClickerStatsProfile clickerStatsProfile = clickerPlayerManager.getStatsProfile(uuid);
        if (!(clickerGameProfile.isFirstClicked())) {
            clickerGameProfile.setFirstClicked(true);
            designManager.sendClickerBlockDesign(player);
        }
        switch (event.getInteractType()) {
            case LEFT_CLICK, ADVENTURE_LEFT_CLICK -> click(event, player, uuid, clickerGameProfile, clickerStatsProfile);
            case HAND_RIGHT_CLICK -> {
                if (event.isBlockPlaced()) {
                    designManager.sendClickerBlockDesign(player);
                }
                soundManager.playSound(player, SoundType.OPEN_INVENTORY);
                new HomeMenu(plugin, player).open();
            }
            default -> {
            }
        }
    }

    private void click(CookieClickerInteractEvent event, Player player, UUID uuid, ClickerGameProfile clickerGameProfile, ClickerStatsProfile clickerStatsProfile) {
        clickerGameProfile.addPlayerClicksPerSecond(1);
        if (clickerGameProfile.isUnderLastPlayerMove(pluginConfig.getAFKCooldownSeconds())) {
            soundManager.playSound(player, SoundType.CLICK_DENY);
            player.sendActionBar(Text.component(languageConfig.getReplacedLanguage(PluginLanguage.AFK_MESSAGE, uuid)));
            return;
        }
        if (clickerGameProfile.isOverCPS(pluginConfig.getMaximumClicksPerSecond())) {
            soundManager.playSound(player, SoundType.CLICK_DENY);
            player.sendActionBar(Text.component(languageConfig.getReplacedLanguage(PluginLanguage.MAX_CPS, uuid)));
            return;
        }
        long particleDesign = clickerStatsProfile.getParticleDesign();
        if (clickerGameProfile.isOverLastParticleEffect() && particleDesign > 0) {
            clickerGameProfile.updateLastParticleEffect(pluginConfig.getCustomParticleEffectType(ShopType.HIT_PARTICLE_DESIGN, particleDesign).getDelay());
            designManager.sendParticleEffect(player, event.getLocation(), particleDesign);
        }

        clickerStatsProfile.addClickerClicks(1);
        soundManager.playSound(player, SoundType.CLICK_ALLOW);

        ClickerEventManager clickerEventManager = plugin.getClickerEventManager();
        if (clickerEventManager.isActive(uuid, ClickerEventType.GOLDEN_COOKIE)) {
            clickerStatsProfile.addCookies(clickerStatsProfile.getPerClick() * pluginConfig.getPerClickMultiplier());
            return;
        }

        if (random.nextInt(pluginConfig.getGoldenCookieRange()) <= 1) {
            showTitle(player, PluginLanguage.GOLDEN_COOKIE_TITLE_HEADER, PluginLanguage.GOLDEN_COOKIE_TITLE_FOOTER, uuid);
            clickerEventManager.activateEvent(uuid, new GoldenCookieEvent(player, ClickerEventType.GOLDEN_COOKIE, pluginConfig.getGoldenDurationSeconds(), event.getLocation().getBlock()));
            soundManager.playSound(player, SoundType.GOLDEN_COOKIE);
        } else if (random.nextInt(pluginConfig.getCookieExplosionRange()) <= 1 && (!(clickerEventManager.isActive(uuid, ClickerEventType.COOKIE_EXPLOSION)))) {
            showTitle(player, PluginLanguage.COOKIE_EXPLOSION_TITLE_HEADER, PluginLanguage.COOKIE_EXPLOSION_TITLE_FOOTER, uuid);
            int explosionDuration = pluginConfig.getExplosionDurationSeconds();
            CookieExplosionEvent cookieExplosionEvent = new CookieExplosionEvent(plugin, player, ClickerEventType.COOKIE_EXPLOSION, explosionDuration);
            clickerEventManager.activateEvent(uuid, cookieExplosionEvent);
            Location location = event.getLocation().clone().add(0.5, 1, 0.5);
            for (int number = 0; number < pluginConfig.getCookieAmount(); number++) {
                // cookies visibles du seul joueur dès leur apparition
                Item item = player.getWorld().dropItemNaturally(location, new org.bukkit.inventory.ItemStack(Material.COOKIE),
                        dropped -> dropped.setVisibleByDefault(false));
                player.showEntity(plugin, item);
                item.setInvulnerable(true);
                item.setPickupDelay(explosionDuration);
                item.setVelocity(new Vector(random.nextDouble(-0.2, 0.2), 1, random.nextDouble(-0.2, 0.2)));
                cookieExplosionEvent.addGlowing(item);
                cookieExplosionEvent.getItems().add(item);
            }
            soundManager.playSound(player, SoundType.COOKIE_EXPLOSION);
        }
        clickerStatsProfile.addCookies(clickerStatsProfile.getPerClick());
        player.sendActionBar(Text.component(languageConfig.getReplacedLanguage(PluginLanguage.CLICK_MESSAGE, uuid)));
    }

    private void showTitle(Player player, PluginLanguage header, PluginLanguage footer, UUID uuid) {
        Component title = Text.component(languageConfig.getReplacedLanguage(header, uuid));
        Component subtitle = Text.component(languageConfig.getReplacedLanguage(footer, uuid));
        player.showTitle(Title.title(title, subtitle, EVENT_TITLE_TIMES));
    }
}
