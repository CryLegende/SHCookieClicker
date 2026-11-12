package org.Cry_Legende.sHCookieClicker.game;

import net.kyori.adventure.title.Title;
import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.Designs;
import org.Cry_Legende.sHCookieClicker.config.Messages;
import org.Cry_Legende.sHCookieClicker.config.Settings;
import org.Cry_Legende.sHCookieClicker.config.SoundSet;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.Cry_Legende.sHCookieClicker.menu.HomeMenu;
import org.Cry_Legende.sHCookieClicker.network.NetworkMode;
import org.Cry_Legende.sHCookieClicker.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Le jeu : arrivée et départ des joueurs, clics sur le clicker. Un clic qui compte coûte une addition dans le
 * profil, un son et, au plus toutes les 100 ms, la barre d'action ; la base n'est jamais touchée.
 */
public final class ClickService {
    private static final Title.Times TITLE_TIMES = Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(1));

    private final SHCookieClicker plugin;

    public ClickService(SHCookieClicker plugin) {
        this.plugin = plugin;
    }

    // ---- connexion

    public void join(Player player) {
        Session session = plugin.sessions().open(player);
        NetworkMode network = plugin.network();
        Profile known = plugin.profiles().get(player.getUniqueId());
        if (network == null) {
            if (known != null) {
                plugin.profiles().rename(known, player.getName());
            }
            session.loaded = true;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.look().show(player);
                }
            }, 20);
            return;
        }
        // Mode réseau : le profil est relu en base une fois que l'autre serveur a fini de l'écrire
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            network.awaitArrival(uuid, name);
            network.reload(uuid, name);
            plugin.sync(() -> {
                if (player.isOnline() && plugin.sessions().get(uuid) == session) {
                    session.loaded = true;
                    plugin.look().show(player);
                }
            });
        });
    }

    /**
     * Départ (ou arrêt du plugin) : l'état de jeu disparaît, la progression reste en mémoire et s'écrit tout de
     * suite. En mode réseau, l'autre serveur attend la fin de cette écriture avant de lire le profil.
     */
    public void leave(Player player, boolean save) {
        UUID uuid = player.getUniqueId();
        Session session = plugin.sessions().close(uuid);
        if (session != null) {
            plugin.feedback().forget(session);
            Explosions.Explosion explosion = session.explosion;
            if (explosion != null) {
                plugin.sync(() -> plugin.explosions().finish(session, explosion));
            }
        }
        plugin.particles().stop(uuid);
        if (!save) {
            return;
        }
        NetworkMode network = plugin.network();
        CompletableFuture<Void> barrier = network != null ? network.beginQuit(uuid) : null;
        Profile profile = plugin.profiles().get(uuid);
        if (profile == null || !profile.isDirty()) {
            if (barrier != null) {
                barrier.complete(null);
            }
            return;
        }
        CompletableFuture<Void> written = plugin.saver().flushNow();
        if (barrier != null) {
            written.whenComplete((value, error) -> barrier.complete(null));
        }
    }

    // ---- clics

    /** Clic gauche sur un clicker, sur le fil du monde du clicker. */
    public void leftClick(Player player, Block block) {
        Session session = plugin.sessions().get(player);
        if (session == null || !session.loaded) {
            return;
        }
        Settings settings = plugin.settings();
        long now = System.currentTimeMillis();
        if (!session.designSent) {
            session.designSent = true;
            plugin.look().show(player);
        }
        boolean tooFast = session.fill(now, settings.maxClicksPerSecond);
        if (session.idle(now, settings.afkMillis)) {
            plugin.feedback().denied(player, session, "barre.afk");
            return;
        }
        if (tooFast) {
            plugin.feedback().denied(player, session, "barre.trop-rapide");
            return;
        }
        Profile profile = plugin.profiles().getOrCreate(player.getUniqueId(), player.getName());
        playEffect(player, session, profile, block, now);
        boolean golden = session.golden(now);
        profile.click(golden ? settings.goldenMultiplier : 1);
        plugin.ranking().markChanged();
        settings.sounds.play(player, SoundSet.Action.CLIC);
        if (golden) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(settings.goldenChance) == 0) {
            startGolden(player, session, now);
            return;
        }
        if (session.explosion == null && random.nextInt(settings.explosionChance) == 0) {
            title(player, "explosion.titre", "explosion.sous-titre");
            settings.sounds.play(player, SoundSet.Action.EXPLOSION);
            plugin.explosions().start(player, session, block);
        }
        plugin.feedback().clicked(player, session, profile);
    }

    private void startGolden(Player player, Session session, long now) {
        Settings settings = plugin.settings();
        session.goldenUntil = now + settings.goldenMillis;
        session.goldenFrame = 0;
        title(player, "cookie-dore.titre", "cookie-dore.sous-titre");
        settings.sounds.play(player, SoundSet.Action.COOKIE_DORE);
        plugin.feedback().startGolden(session);
    }

    private void playEffect(Player player, Session session, Profile profile, Block block, long now) {
        long id = profile.selected(ShopKind.HIT_PARTICLE_DESIGN);
        if (id <= 0 || now < session.effectReadyAt) {
            return;
        }
        Designs.Effect design = plugin.settings().effect(id);
        if (design == null) {
            return;
        }
        session.effectReadyAt = now + design.animation().durationMillis();
        plugin.particles().play(player, block.getLocation(), design);
    }

    private void title(Player player, String titleKey, String subtitleKey) {
        Messages messages = plugin.messages();
        player.showTitle(Title.title(Text.of(messages.raw(titleKey)), Text.of(messages.raw(subtitleKey)), TITLE_TIMES));
    }

    /** Clic droit sur un clicker : le menu. Un bloc en main a pu s'afficher côté client, le design est renvoyé. */
    public void rightClick(Player player, boolean blockInHand) {
        Session session = plugin.sessions().get(player);
        if (session == null || !session.loaded) {
            return;
        }
        if (blockInHand || !session.designSent) {
            session.designSent = true;
            plugin.look().show(player);
        }
        plugin.settings().sounds.play(player, SoundSet.Action.OUVERTURE);
        new HomeMenu(plugin, player).open();
    }

    // ---- pose d'un clicker

    public void startPlacing(Player player, int number) {
        Session session = plugin.sessions().get(player);
        if (session != null) {
            session.placing = number;
        }
    }

    /** Clic pendant une pose : clic droit sur un bloc = nouveau clicker, autre clic = annulation. */
    boolean placing(Player player, Block block, boolean rightClick) {
        Session session = plugin.sessions().get(player);
        if (session == null || session.placing <= 0) {
            return false;
        }
        int number = session.placing;
        session.placing = 0;
        Messages messages = plugin.messages();
        if (!rightClick || block == null) {
            messages.send(player, "clickers.pose-annulee");
            return true;
        }
        if (plugin.clickers().isClicker(block)) {
            messages.send(player, "clickers.deja");
            return true;
        }
        plugin.sync(() -> {
            try {
                Clickers.write(new File(plugin.getDataFolder(), "locations.yml"), number, block);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Écriture de locations.yml impossible", exception);
                return;
            }
            plugin.reloadClickers();
            plugin.holograms().restart();
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.look().show(online);
            }
            messages.send(player, "clickers.ajoute", "numero", String.valueOf(number));
        });
        return true;
    }
}
