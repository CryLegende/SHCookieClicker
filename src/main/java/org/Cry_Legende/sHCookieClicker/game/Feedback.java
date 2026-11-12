package org.Cry_Legende.sHCookieClicker.game;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.Messages;
import org.Cry_Legende.sHCookieClicker.config.SoundSet;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Barre d'action du clicker. Un clic la met à jour au plus toutes les 100 ms ; le dernier clic d'une rafale
 * s'affiche au tick suivant. Pendant le cookie doré, la barre scintille près du clicker. Les refus (trop rapide,
 * immobile) ne sonnent et ne s'affichent qu'une fois par quart de seconde.
 */
public final class Feedback {
    private static final long BAR_MILLIS = 100;
    private static final long DENY_MILLIS = 250;
    private static final double GOLDEN_DISTANCE = 5;

    private final SHCookieClicker plugin;
    private final Set<Session> pending = ConcurrentHashMap.newKeySet();
    private final Set<Session> golden = ConcurrentHashMap.newKeySet();

    public Feedback(SHCookieClicker plugin) {
        this.plugin = plugin;
    }

    /** Après un clic qui compte. */
    void clicked(Player player, Session session, Profile profile) {
        long now = System.currentTimeMillis();
        if (now - session.barAt >= BAR_MILLIS) {
            session.barAt = now;
            session.barPending = false;
            player.sendActionBar(Text.of(plugin.messages().raw("barre.clic", "cookies", plugin.number(profile.cookies()))));
            return;
        }
        session.barPending = true;
        pending.add(session);
    }

    /** Un clic refusé : son et message, au plus quatre fois par seconde. */
    void denied(Player player, Session session, String key) {
        long now = System.currentTimeMillis();
        if (now - session.deniedAt < DENY_MILLIS) {
            return;
        }
        session.deniedAt = now;
        session.barAt = now;
        plugin.settings().sounds.play(player, SoundSet.Action.CLIC_REFUSE);
        player.sendActionBar(Text.of(plugin.messages().raw(key)));
    }

    void collected(Player player, Session session, Profile profile, long gain) {
        session.barAt = System.currentTimeMillis();
        player.sendActionBar(Text.of(plugin.messages().raw("barre.ramassage", "cookies", plugin.number(profile.cookies()), "gain", plugin.number(gain))));
    }

    void startGolden(Session session) {
        golden.add(session);
    }

    void forget(Session session) {
        pending.remove(session);
        golden.remove(session);
    }

    /** Fil principal, tous les deux ticks : ne parcourt que les joueurs qui ont une barre à envoyer. */
    public void tick() {
        if (pending.isEmpty() && golden.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Iterator<Session> iterator = pending.iterator(); iterator.hasNext(); ) {
            Session session = iterator.next();
            if (!session.barPending) {
                iterator.remove();
                continue;
            }
            if (now - session.barAt < BAR_MILLIS) {
                continue;
            }
            iterator.remove();
            session.barPending = false;
            session.barAt = now;
            Player player = Bukkit.getPlayer(session.uuid);
            Profile profile = plugin.profiles().get(session.uuid);
            if (player != null && profile != null && !session.golden(now)) {
                player.sendActionBar(Text.of(plugin.messages().raw("barre.clic", "cookies", plugin.number(profile.cookies()))));
            }
        }
        for (Iterator<Session> iterator = golden.iterator(); iterator.hasNext(); ) {
            Session session = iterator.next();
            Player player = Bukkit.getPlayer(session.uuid);
            if (player == null || !session.golden(now)) {
                iterator.remove();
                continue;
            }
            if (!plugin.clickers().near(player.getLocation(), GOLDEN_DISTANCE)) {
                continue;
            }
            player.sendActionBar(Text.of(goldenBar(session, now)));
        }
    }

    private String goldenBar(Session session, long now) {
        Messages messages = plugin.messages();
        if (session.idle(now, plugin.settings().afkMillis)) {
            return messages.raw("barre.afk");
        }
        if (session.overflowing(now, plugin.settings().maxClicksPerSecond)) {
            return messages.raw("barre.trop-rapide");
        }
        Profile profile = plugin.profiles().get(session.uuid);
        String text = Text.plain(messages.raw("barre.clic", "cookies", plugin.number(profile == null ? 0 : profile.cookies())));
        String base = messages.raw("cookie-dore.couleur");
        String shine = messages.raw("cookie-dore.reflet");
        int length = text.length();
        if (length == 0) {
            return "";
        }
        int start = session.goldenFrame % length;
        session.goldenFrame = (start + 1) % length;
        int end = Math.min(length, start + 3);
        return base + text.substring(0, start) + shine + text.substring(start, end) + base + text.substring(end);
    }
}
