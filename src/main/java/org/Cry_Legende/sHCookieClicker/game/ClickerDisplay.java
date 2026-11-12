package org.Cry_Legende.sHCookieClicker.game;

import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.Appearance;
import org.Cry_Legende.sHCookieClicker.config.Designs;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.Cry_Legende.sHCookieClicker.hook.Heads;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Le clicker : un objet qui flotte (une tête de cookie, ou le design choisi par le joueur) et la zone invisible où
 * il faut cliquer, plus large que lui. Tout est envoyé en paquets à chaque joueur, qui voit le sien : le serveur ne
 * garde aucune entité, n'écrit rien dans le monde et le clicker n'a aucun bloc. L'objet tourne sur lui-même en
 * montant et en descendant doucement, et fait un bond à chaque clic qui compte (vu du seul joueur qui clique).
 * <p>
 * Un seul minuteur, sur le fil principal : à chaque tick il joue les clics reçus (remis par ClickerInput, sur le fil
 * réseau), tous les 10 ticks il montre l'objet aux joueurs qui s'approchent d'un clicker et le retire à ceux qui
 * s'éloignent, et il envoie à ceux qui le voient la prochaine pose quand leur objet n'a plus de mouvement en cours.
 * Le client lisse le mouvement entre deux envois : au repos, un paquet de quelques dizaines d'octets tous les
 * 5 ticks et par joueur.
 */
public final class ClickerDisplay {
    private static final int SWEEP_TICKS = 10;
    /** Un objet qui s'éloigne n'est retiré qu'un peu plus loin que celui qui apparaît : pas de clignotement. */
    private static final int HIDE_MARGIN = 8;
    private static final int POP_UP_TICKS = 1;
    private static final int POP_DOWN_TICKS = 3;
    /** Écart minimal entre deux bonds, en ticks : au plus un bond tous les 3 ticks, même à douze clics par seconde. */
    private static final int POP_GAP_TICKS = 3;
    /** Un clic droit envoie deux paquets (interaction puis interaction à un point) : le second est ignoré. */
    private static final long RIGHT_CLICK_GAP_MILLIS = 250;
    /** Clics en attente par joueur : un client qui en envoie bien plus que le jeu n'en permet est ignoré. */
    private static final int MAX_QUEUED_CLICKS = 40;

    private final SHCookieClicker plugin;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private final Queue<Click> clicks = new ConcurrentLinkedQueue<>();
    private PacketListenerCommon input;
    private BukkitTask task;

    /** Les objets montés à un joueur, par clicker. */
    private static final class View {
        final Player player;
        final Map<Clickers.Spot, Shown> shown = new ConcurrentHashMap<>();
        final AtomicInteger queued = new AtomicInteger();
        volatile long rightAt;

        View(Player player) {
            this.player = player;
        }
    }

    /** Un objet et sa zone de clic, montés à un joueur. */
    private static final class Shown {
        final int entityId;
        final int hitboxId;
        final Look look;
        /** Tick du prochain envoi de pose. */
        int nextFrame;
        /** 1 entre le sommet du bond et le retour au repos. */
        int phase;
        /** Tick du dernier bond demandé (fil principal) et du dernier bond joué. */
        volatile int popRequested = Integer.MIN_VALUE;
        int popPlayed = Integer.MIN_VALUE;

        Shown(int entityId, int hitboxId, Look look) {
            this.entityId = entityId;
            this.hitboxId = hitboxId;
            this.look = look;
        }

        /** Premier envoi de pose dès le prochain passage du minuteur. */
        Shown starting(int now) {
            nextFrame = now;
            return this;
        }
    }

    private record Click(View view, Clickers.Spot spot, boolean left) {
    }

    public ClickerDisplay(SHCookieClicker plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task == null) {
            input = ClickerInput.register(this);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        }
    }

    /** Arrêt du plugin : les objets disparaissent chez les joueurs. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (input != null) {
            ClickerInput.unregister(input);
            input = null;
        }
        clearAll();
        clicks.clear();
    }

    /** Rechargement de la configuration : tout est remonté avec les nouveaux réglages. */
    public void reload() {
        clearAll();
        tick();
    }

    private void clearAll() {
        for (View view : views.values()) {
            for (Shown shown : view.shown.values()) {
                destroy(view.player, shown);
            }
        }
        views.clear();
    }

    private static void destroy(Player player, Shown shown) {
        ClickerPackets.destroy(player, shown.entityId, shown.hitboxId);
    }

    /** Le joueur change de monde, meurt ou part : son client a déjà oublié les entités. */
    public void forget(Player player) {
        views.remove(player.getUniqueId());
    }

    /** Le joueur vient de changer de design : l'objet est remplacé sans attendre le prochain passage. */
    public void refresh(Player player) {
        plugin.sync(() -> update(player, Bukkit.getCurrentTick()));
    }

    /** Un clic qui compte sur ce clicker : l'objet du joueur fait un bond. */
    public void pop(Player player, Clickers.Spot spot) {
        View view = views.get(player.getUniqueId());
        Shown shown = view == null ? null : view.shown.get(spot);
        if (shown != null) {
            int now = Bukkit.getCurrentTick();
            if ((long) now - shown.popRequested >= POP_GAP_TICKS) {
                shown.popRequested = now;
            }
        }
    }

    // ---- clics (fil réseau)

    /** Vrai si cette entité est la zone de clic d'un clicker de ce joueur. */
    boolean owns(UUID uuid, int entityId) {
        View view = views.get(uuid);
        return view != null && spotOf(view, entityId) != null;
    }

    private static Clickers.Spot spotOf(View view, int hitboxId) {
        for (Map.Entry<Clickers.Spot, Shown> entry : view.shown.entrySet()) {
            if (entry.getValue().hitboxId == hitboxId) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Un clic sur la zone de clic : joué au prochain tick, sur le fil principal. */
    void click(UUID uuid, int hitboxId, boolean left) {
        View view = views.get(uuid);
        Clickers.Spot spot = view == null ? null : spotOf(view, hitboxId);
        if (spot == null) {
            return;
        }
        if (!left) {
            long now = System.currentTimeMillis();
            if (now - view.rightAt < RIGHT_CLICK_GAP_MILLIS) {
                return;
            }
            view.rightAt = now;
        }
        if (view.queued.incrementAndGet() > MAX_QUEUED_CLICKS) {
            view.queued.decrementAndGet();
            return;
        }
        clicks.add(new Click(view, spot, left));
    }

    private void playClicks() {
        for (Click click; (click = clicks.poll()) != null; ) {
            click.view.queued.decrementAndGet();
            Player player = click.view.player;
            if (!player.isOnline() || views.get(player.getUniqueId()) != click.view) {
                continue;
            }
            try {
                if (click.left) {
                    plugin.clicks().leftClick(player, click.spot);
                } else {
                    plugin.clicks().rightClick(player);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Clic de " + player.getName() + " impossible", exception);
            }
        }
    }

    // ---- minuteur

    private void tick() {
        Appearance look = plugin.settings().appearance;
        int now = Bukkit.getCurrentTick();
        playClicks();
        if (now % SWEEP_TICKS == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                update(player, now);
            }
        }
        for (View view : views.values()) {
            for (Shown shown : view.shown.values()) {
                animate(view.player, shown, look, now);
            }
        }
    }

    /** Montre, remplace ou retire les objets d'un joueur selon sa position et son design. */
    private void update(Player player, int now) {
        Appearance look = plugin.settings().appearance;
        Session session = plugin.sessions().get(player);
        Clickers clickers = plugin.clickers();
        View view = views.get(player.getUniqueId());
        if (session == null || !session.isLoaded() || !clickers.hasClickers(player.getWorld())) {
            if (view != null) {
                for (Shown shown : view.shown.values()) {
                    destroy(player, shown);
                }
                views.remove(player.getUniqueId());
            }
            return;
        }
        if (view == null) {
            view = new View(player);
            views.put(player.getUniqueId(), view);
        }
        // Un clicker retiré
        List<Clickers.Spot> spots = clickers.all();
        for (Map.Entry<Clickers.Spot, Shown> entry : new ArrayList<>(view.shown.entrySet())) {
            if (!spots.contains(entry.getKey())) {
                destroy(player, entry.getValue());
                view.shown.remove(entry.getKey());
            }
        }
        Look wanted = lookOf(player, look);
        Location at = player.getLocation();
        // Jamais plus loin que ce que le client a chargé
        int radius = Math.min(look.radius(), Math.max(16, (player.getViewDistance() - 1) * 16));
        String world = player.getWorld().getName();
        for (Clickers.Spot spot : spots) {
            if (!spot.world().equals(world)) {
                continue;
            }
            double dx = spot.x() + 0.5 - at.getX();
            double dy = spot.y() + 0.5 - at.getY();
            double dz = spot.z() + 0.5 - at.getZ();
            double distance = dx * dx + dy * dy + dz * dz;
            Shown shown = view.shown.get(spot);
            if (shown != null) {
                if (distance > (double) (radius + HIDE_MARGIN) * (radius + HIDE_MARGIN)) {
                    destroy(player, shown);
                    view.shown.remove(spot);
                } else if (!shown.look.key().equals(wanted.key())) {
                    // Même zone de clic, un autre objet
                    ClickerPackets.destroy(player, shown.entityId);
                    view.shown.put(spot, new Shown(spawnObject(player, spot, wanted, look, now), shown.hitboxId, wanted).starting(now));
                }
            } else if (distance <= (double) radius * radius) {
                int hitbox = ClickerPackets.newEntityId();
                double centerY = spot.y() + 0.5 + look.height();
                ClickerPackets.spawnHitbox(player, hitbox, spot.x() + 0.5, centerY - look.hitHeight() / 2.0, spot.z() + 0.5,
                        look.hitWidth(), look.hitHeight());
                view.shown.put(spot, new Shown(spawnObject(player, spot, wanted, look, now), hitbox, wanted).starting(now));
            }
        }
    }

    /** Montre l'objet au joueur, à la pose du moment ; rend son numéro d'entité. */
    private int spawnObject(Player player, Clickers.Spot spot, Look wanted, Appearance look, int now) {
        int id = ClickerPackets.newEntityId();
        ClickerPackets.spawn(player, id, spot.x() + 0.5, spot.y() + 0.5 + look.height(), spot.z() + 0.5, wanted,
                Pose.at(look, wanted.isBlock(), now, 0));
        return id;
    }

    /** Envoie la prochaine pose : le bond demandé d'abord, puis son retour au repos, sinon le repos. */
    private void animate(Player player, Shown shown, Appearance look, int now) {
        int requested = shown.popRequested;
        if (requested != shown.popPlayed) {
            shown.popPlayed = requested;
            ClickerPackets.move(player, shown.entityId, Pose.at(look, shown.look.isBlock(), now + POP_UP_TICKS, 1), POP_UP_TICKS);
            shown.phase = 1;
            shown.nextFrame = now + POP_UP_TICKS;
            return;
        }
        if (now < shown.nextFrame) {
            return;
        }
        int ticks = shown.phase == 1 ? POP_DOWN_TICKS : look.stepTicks();
        ClickerPackets.move(player, shown.entityId, Pose.at(look, shown.look.isBlock(), now + ticks, 0), ticks);
        shown.phase = 0;
        shown.nextFrame = now + ticks;
    }

    // ---- design du joueur

    private Look lookOf(Player player, Appearance look) {
        Look fallback = Look.ofHead("defaut:" + look.texture(), Heads.texture(look.texture()));
        Profile profile = plugin.profiles().get(player.getUniqueId());
        long id = profile == null ? 0 : profile.selected(ShopKind.BLOCK_DESIGN);
        Designs.Block design = id <= 0 ? null : plugin.settings().block(id);
        if (design == null) {
            return fallback;
        }
        if (design.playerHead()) {
            return Look.ofHead("joueur:" + player.getUniqueId(), Heads.of(player));
        }
        if (design.block() == Material.PLAYER_HEAD) {
            return design.texture() == null ? fallback : Look.ofHead("texture:" + design.texture(), Heads.texture(design.texture()));
        }
        return Look.ofBlock(design.block());
    }
}
