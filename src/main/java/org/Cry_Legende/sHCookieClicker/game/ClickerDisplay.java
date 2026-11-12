package org.Cry_Legende.sHCookieClicker.game;

import com.github.retrooper.packetevents.event.PacketListenerCommon;
import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.Appearance;
import org.Cry_Legende.sHCookieClicker.config.Designs;
import org.Cry_Legende.sHCookieClicker.config.MovingCookie;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.Cry_Legende.sHCookieClicker.hook.Heads;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
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
 * <p>
 * Cookie mobile (/shcookieclicker moving, pour les tournois) : la zone de clic rétrécit et l'objet de chaque joueur
 * change de place tous les quelques clics qui comptent. À chaque changement, la zone de clic est remplacée par une
 * neuve : un clic envoyé avant que le joueur ait vu la nouvelle place vise l'ancienne et ne compte pas.
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
    /** Cookie mobile en cours, ou null. */
    private volatile Moving moving;
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

    /** Cookie mobile : heure de fin (ms) et places possibles autour de chaque clicker. */
    private record Moving(long until, Map<Clickers.Spot, List<Places.Offset>> places) {
        List<Places.Offset> at(Clickers.Spot spot) {
            return places.getOrDefault(spot, List.of());
        }
    }

    /** Un objet et sa zone de clic, montés à un joueur. */
    private static final class Shown {
        final int entityId;
        /** Zone de clic, remplacée à chaque changement de place du cookie mobile. */
        volatile int hitboxId;
        /** Zone remplacée : ses clics en retard sont bloqués sans compter. */
        volatile int oldHitboxId = Integer.MIN_VALUE;
        final Look look;
        /** Cookie mobile : place de l'objet, clics qui comptent à cette place, changement de place demandé. */
        Places.Offset offset = Places.Offset.ZERO;
        int hits;
        boolean jumpRequested;
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

        /** Le même objet à la même place, sous un autre aspect (changement de design). */
        Shown restyled(int newEntityId, Look newLook, int now) {
            Shown copy = new Shown(newEntityId, hitboxId, newLook);
            copy.oldHitboxId = oldHitboxId;
            copy.offset = offset;
            copy.hits = hits;
            return copy.starting(now);
        }
    }

    private record Click(View view, Clickers.Spot spot, int hitboxId, boolean left) {
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

    /** Rechargement de la configuration ou des clickers : tout est remonté avec les nouveaux réglages. */
    public void reload() {
        Moving current = moving;
        if (current != null) {
            moving = new Moving(current.until(), places());
        }
        clearAll();
        int now = Bukkit.getCurrentTick();
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player, now);
        }
    }

    // ---- cookie mobile (fil principal)

    /** Cookie mobile pendant {@code seconds} secondes, ou jusqu'à {@link #stopMoving()}. */
    public void startMoving(long seconds) {
        moving = new Moving(System.currentTimeMillis() + seconds * 1000L, places());
        reload();
    }

    /** Fin du cookie mobile ; faux s'il n'était pas en cours. */
    public boolean stopMoving() {
        if (moving == null) {
            return false;
        }
        moving = null;
        reload();
        return true;
    }

    /** Places possibles autour de chaque clicker, d'après les blocs du monde. */
    private Map<Clickers.Spot, List<Places.Offset>> places() {
        MovingCookie settings = plugin.settings().movingCookie;
        Appearance look = plugin.settings().appearance;
        Map<Clickers.Spot, List<Places.Offset>> places = new HashMap<>();
        for (Clickers.Spot spot : plugin.clickers().all()) {
            World world = Bukkit.getWorld(spot.world());
            if (world == null) {
                continue;
            }
            List<Places.Offset> around = Places.around(settings, spot.x() + 0.5, spot.y() + 0.5 + look.height(), spot.z() + 0.5,
                    (x, y, z) -> !world.getBlockAt(x, y, z).isPassable());
            if (around.isEmpty()) {
                plugin.getLogger().warning("Cookie mobile : aucune place libre autour du clicker " + spot.number() + ", il reste à sa place.");
            }
            places.put(spot, List.copyOf(around));
        }
        return places;
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

    /**
     * Un clic qui compte sur ce clicker (fil principal) : l'objet du joueur fait un bond ; en cookie mobile, il change
     * de place tous les « clics-par-place » clics.
     */
    public void pop(Player player, Clickers.Spot spot) {
        View view = views.get(player.getUniqueId());
        Shown shown = view == null ? null : view.shown.get(spot);
        if (shown == null) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        if ((long) now - shown.popRequested >= POP_GAP_TICKS) {
            shown.popRequested = now;
        }
        if (moving != null && ++shown.hits >= plugin.settings().movingCookie.clicksPerPlace()) {
            shown.hits = 0;
            shown.jumpRequested = true;
        }
    }

    // ---- clics (fil réseau)

    /** Vrai si cette entité est la zone de clic (actuelle ou tout juste remplacée) d'un clicker de ce joueur. */
    boolean owns(UUID uuid, int entityId) {
        View view = views.get(uuid);
        if (view == null) {
            return false;
        }
        for (Shown shown : view.shown.values()) {
            if (shown.hitboxId == entityId || shown.oldHitboxId == entityId) {
                return true;
            }
        }
        return false;
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
        clicks.add(new Click(view, spot, hitboxId, left));
    }

    private void playClicks() {
        for (Click click; (click = clicks.poll()) != null; ) {
            click.view.queued.decrementAndGet();
            Player player = click.view.player;
            if (!player.isOnline() || views.get(player.getUniqueId()) != click.view) {
                continue;
            }
            Shown shown = click.view.shown.get(click.spot);
            if (shown == null || shown.hitboxId != click.hitboxId) {
                // Zone remplacée depuis : le clic visait l'ancienne place du cookie mobile
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
        Moving current = moving;
        if (current != null && System.currentTimeMillis() >= current.until()) {
            stopMoving();
        }
        Appearance look = plugin.settings().appearance;
        int now = Bukkit.getCurrentTick();
        playClicks();
        if (now % SWEEP_TICKS == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                update(player, now);
            }
        }
        for (View view : views.values()) {
            for (Map.Entry<Clickers.Spot, Shown> entry : view.shown.entrySet()) {
                animate(view.player, entry.getKey(), entry.getValue(), look, now);
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
                    view.shown.put(spot, shown.restyled(spawnObject(player, spot, wanted, look, shown.offset, now), wanted, now));
                }
            } else if (distance <= (double) radius * radius) {
                Moving current = moving;
                Places.Offset offset = current == null ? Places.Offset.ZERO
                        : Places.next(current.at(spot), Places.Offset.ZERO, 0, ThreadLocalRandom.current());
                int hitbox = ClickerPackets.newEntityId();
                spawnHitbox(player, spot, hitbox, offset, look, current != null);
                Shown fresh = new Shown(spawnObject(player, spot, wanted, look, offset, now), hitbox, wanted);
                fresh.offset = offset;
                view.shown.put(spot, fresh.starting(now));
            }
        }
    }

    /** Montre l'objet au joueur, à la pose du moment ; rend son numéro d'entité. */
    private int spawnObject(Player player, Clickers.Spot spot, Look wanted, Appearance look, Places.Offset offset, int now) {
        int id = ClickerPackets.newEntityId();
        ClickerPackets.spawn(player, id, spot.x() + 0.5, spot.y() + 0.5 + look.height(), spot.z() + 0.5, wanted,
                Pose.at(look, wanted.isBlock(), now, 0).shifted(offset));
        return id;
    }

    /** Zone de clic centrée sur l'objet : la taille du cookie mobile s'il est en cours, sinon celle de l'apparence. */
    private void spawnHitbox(Player player, Clickers.Spot spot, int id, Places.Offset offset, Appearance look, boolean small) {
        float width = small ? plugin.settings().movingCookie.hitSize() : look.hitWidth();
        float height = small ? plugin.settings().movingCookie.hitSize() : look.hitHeight();
        double centerY = spot.y() + 0.5 + look.height() + offset.y();
        ClickerPackets.spawnHitbox(player, id, spot.x() + 0.5 + offset.x(), centerY - height / 2.0, spot.z() + 0.5 + offset.z(),
                width, height);
    }

    /**
     * Envoie la prochaine pose : le changement de place demandé (cookie mobile), sinon le bond demandé puis son retour
     * au repos, sinon le repos.
     */
    private void animate(Player player, Clickers.Spot spot, Shown shown, Appearance look, int now) {
        if (shown.jumpRequested) {
            shown.jumpRequested = false;
            Moving current = moving;
            if (current != null) {
                jump(player, spot, shown, current, look, now);
                return;
            }
        }
        int requested = shown.popRequested;
        if (requested != shown.popPlayed) {
            shown.popPlayed = requested;
            ClickerPackets.move(player, shown.entityId,
                    Pose.at(look, shown.look.isBlock(), now + POP_UP_TICKS, 1).shifted(shown.offset), POP_UP_TICKS);
            shown.phase = 1;
            shown.nextFrame = now + POP_UP_TICKS;
            return;
        }
        if (now < shown.nextFrame) {
            return;
        }
        int ticks = shown.phase == 1 ? POP_DOWN_TICKS : look.stepTicks();
        ClickerPackets.move(player, shown.entityId, Pose.at(look, shown.look.isBlock(), now + ticks, 0).shifted(shown.offset), ticks);
        shown.phase = 0;
        shown.nextFrame = now + ticks;
    }

    /** Cookie mobile : l'objet glisse vers une autre place, une zone de clic neuve l'y attend. */
    private void jump(Player player, Clickers.Spot spot, Shown shown, Moving current, Appearance look, int now) {
        MovingCookie settings = plugin.settings().movingCookie;
        shown.offset = Places.next(current.at(spot), shown.offset, settings.minDistance(), ThreadLocalRandom.current());
        int old = shown.hitboxId;
        int fresh = ClickerPackets.newEntityId();
        spawnHitbox(player, spot, fresh, shown.offset, look, true);
        shown.oldHitboxId = old;
        shown.hitboxId = fresh;
        ClickerPackets.destroy(player, old);
        int ticks = settings.slideTicks();
        ClickerPackets.move(player, shown.entityId, Pose.at(look, shown.look.isBlock(), now + ticks, 0).shifted(shown.offset), ticks);
        shown.popPlayed = shown.popRequested;
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
