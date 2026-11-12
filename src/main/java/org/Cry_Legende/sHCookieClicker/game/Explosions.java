package org.Cry_Legende.sHCookieClicker.game;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.Settings;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Explosion de cookies : des cookies jaillissent du clicker, vus et ramassables par le seul joueur. Ils ne sont
 * jamais enregistrés dans le monde, ne fusionnent pas entre eux (chaque objet porte un numéro différent) et
 * disparaissent à la fin de l'explosion. Un cookie ramassé rapporte des cookies au lieu d'aller dans l'inventaire.
 */
public final class Explosions {
    private static final int PICKUP_DELAY_TICKS = 30;
    private static final long GLOW_DELAY_TICKS = 20;

    private final SHCookieClicker plugin;
    private final NamespacedKey key;
    private final AtomicInteger counter = new AtomicInteger();
    private final Map<UUID, Explosion> drops = new ConcurrentHashMap<>();

    /** Une explosion en cours. */
    static final class Explosion {
        final UUID owner;
        final List<Item> items = new ArrayList<>();
        BukkitTask end;

        Explosion(UUID owner) {
            this.owner = owner;
        }
    }

    public Explosions(SHCookieClicker plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "explosion");
    }

    /** Fil du monde du clicker : les cookies y sont créés. */
    void start(Player player, Session session, Block clicker) {
        Settings settings = plugin.settings();
        Explosion explosion = new Explosion(player.getUniqueId());
        Location origin = clicker.getLocation().add(0.5, 1, 0.5);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < settings.explosionCookies; index++) {
            ItemStack stack = new ItemStack(Material.COOKIE);
            int number = counter.incrementAndGet();
            stack.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, number));
            Item item = origin.getWorld().dropItemNaturally(origin, stack, dropped -> {
                dropped.setVisibleByDefault(false);
                dropped.setPersistent(false);
                dropped.setOwner(player.getUniqueId());
                dropped.setCanMobPickup(false);
                dropped.setInvulnerable(true);
                dropped.setPickupDelay(PICKUP_DELAY_TICKS);
            });
            player.showEntity(plugin, item);
            item.setVelocity(new Vector(random.nextDouble(-0.2, 0.2), 1, random.nextDouble(-0.2, 0.2)));
            explosion.items.add(item);
            drops.put(item.getUniqueId(), explosion);
        }
        session.explosion = explosion;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Item item : explosion.items) {
                if (item.isValid()) {
                    item.setGlowing(true);
                }
            }
        }, GLOW_DELAY_TICKS);
        explosion.end = Bukkit.getScheduler().runTaskLater(plugin, () -> finish(session, explosion), settings.explosionSeconds * 20L);
    }

    /** Fin de l'explosion (délai écoulé, départ du joueur) : les cookies restants disparaissent. */
    void finish(Session session, Explosion explosion) {
        if (explosion == null) {
            return;
        }
        if (explosion.end != null) {
            explosion.end.cancel();
        }
        for (Item item : explosion.items) {
            drops.remove(item.getUniqueId());
            item.remove();
        }
        explosion.items.clear();
        if (session != null && session.explosion == explosion) {
            session.explosion = null;
        }
    }

    /** Ramassage d'un objet : seuls les cookies d'une explosion sont concernés. */
    void pickup(PlayerAttemptPickupItemEvent event) {
        if (drops.isEmpty()) {
            return;
        }
        Item item = event.getItem();
        Explosion explosion = drops.get(item.getUniqueId());
        if (explosion == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(explosion.owner)) {
            return;
        }
        drops.remove(item.getUniqueId());
        explosion.items.remove(item);
        player.playPickupItemAnimation(item, 1);
        Location at = item.getLocation();
        item.remove();
        Profile profile = plugin.profiles().getOrCreate(player.getUniqueId(), player.getName());
        long gain = profile.collect(plugin.settings().explosionMultiplier);
        plugin.ranking().markChanged();
        plugin.holograms().gain(player, at, gain);
        Session session = plugin.sessions().get(player);
        if (session != null) {
            plugin.feedback().collected(player, session, profile, gain);
        }
    }

    /** Un entonnoir ne ramasse jamais un cookie d'explosion. */
    boolean isExplosionItem(Item item) {
        return !drops.isEmpty() && drops.containsKey(item.getUniqueId());
    }

    /** Arrêt du plugin : plus aucun cookie d'explosion dans le monde. */
    public void clear() {
        for (Explosion explosion : new ArrayList<>(drops.values())) {
            finish(null, explosion);
        }
        drops.clear();
    }
}
