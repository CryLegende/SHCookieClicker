package de.zillolp.cookieclicker.listener;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.manager.ClickerEventManager;
import de.zillolp.cookieclicker.manager.ClickerPlayerManager;
import de.zillolp.cookieclicker.manager.DesignManager;
import de.zillolp.cookieclicker.manager.HologramManager;
import de.zillolp.cookieclicker.network.NetworkCookies;
import de.zillolp.cookieclicker.profiles.ClickerGameProfile;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerConnectionListener implements Listener {
    private final CookieClicker plugin;
    private final ClickerPlayerManager clickerPlayerManager;
    private final ClickerEventManager clickerEventManager;
    private final DesignManager designManager;
    private final HologramManager hologramManager;
    private final BukkitScheduler bukkitScheduler;

    public PlayerConnectionListener(CookieClicker plugin) {
        this.plugin = plugin;
        clickerPlayerManager = plugin.getClickerPlayerManager();
        clickerEventManager = plugin.getClickerEventManager();
        designManager = plugin.getDesignManager();
        hologramManager = plugin.getHologramManager();
        bukkitScheduler = plugin.getServer().getScheduler();
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        ClickerGameProfile clickerGameProfile = clickerPlayerManager.createGameProfile(uuid);
        NetworkCookies network = plugin.getNetwork();
        if (network == null && clickerPlayerManager.hasStatsProfile(uuid)) {
            // Serveur seul : la progression est déjà en mémoire, la base n'est pas relue
            clickerPlayerManager.loadPlayerProfile(uuid, name, false);
            finishJoin(player, clickerGameProfile);
            return;
        }
        bukkitScheduler.runTaskAsynchronously(plugin, () -> {
            if (network != null) {
                // Mode réseau : le profil est relu en base une fois le départ de l'autre serveur confirmé
                network.awaitArrival(uuid, name);
            }
            clickerPlayerManager.loadPlayerProfile(uuid, name, network != null);
            bukkitScheduler.runTask(plugin, () -> finishJoin(player, clickerGameProfile));
        });
    }

    /** Fil principal : la progression est prête, les clics comptent et les hologrammes et le design du clicker arrivent. */
    private void finishJoin(Player player, ClickerGameProfile clickerGameProfile) {
        if (!(player.isOnline())) {
            return;
        }
        clickerGameProfile.setLoaded(true);
        hologramManager.spawnHolograms(player);
        bukkitScheduler.runTaskLater(plugin, () -> designManager.sendClickerBlockDesign(player), 4);
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ClickerStatsProfile profile = clickerPlayerManager.getStatsProfile(uuid);
        clickerEventManager.deactivateEvents(uuid);
        clickerPlayerManager.unregisterPlayer(player);
        NetworkCookies network = plugin.getNetwork();
        CompletableFuture<Void> saved = network != null ? network.beginQuit(uuid) : null;
        bukkitScheduler.runTaskAsynchronously(plugin, () -> {
            try {
                // Mode réseau : la barrière de SHNetwork attend la fin de cette sauvegarde avant que l'autre serveur ne lise
                if (profile.isDirty()) {
                    plugin.getDatabaseManager().saveProfile(profile);
                }
            } finally {
                if (saved != null) {
                    saved.complete(null);
                }
            }
        });
    }
}
