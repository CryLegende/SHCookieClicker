package org.Cry_Legende.sHCookieClicker.game;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.Appearance;
import org.Cry_Legende.sHCookieClicker.config.Designs;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.Cry_Legende.sHCookieClicker.hook.Heads;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'objet qui flotte au-dessus de chaque clicker : une tête de cookie, ou le design choisi par le joueur. Il est
 * envoyé en paquets à chaque joueur, qui voit le sien : le serveur ne garde aucune entité et n'écrit rien dans le
 * monde. Il tourne sur lui-même en montant et en descendant doucement, et fait un bond à chaque clic qui compte
 * (vu du seul joueur qui clique).
 * <p>
 * Un seul minuteur, sur le fil principal : tous les 10 ticks il montre l'objet aux joueurs qui s'approchent d'un
 * clicker et le retire à ceux qui s'éloignent ; à chaque tick il envoie, à ceux qui le voient, la prochaine pose
 * quand leur objet n'a plus de mouvement en cours. Le client lisse le mouvement entre deux envois : au repos, un
 * paquet de quelques dizaines d'octets tous les 5 ticks et par joueur.
 */
public final class ClickerDisplay {
    private static final int SWEEP_TICKS = 10;
    /** Un objet qui s'éloigne n'est retiré qu'un peu plus loin que celui qui apparaît : pas de clignotement. */
    private static final int HIDE_MARGIN = 8;
    private static final int POP_UP_TICKS = 1;
    private static final int POP_DOWN_TICKS = 3;
    /** Écart minimal entre deux bonds, en ticks : au plus un bond tous les 3 ticks, même à douze clics par seconde. */
    private static final int POP_GAP_TICKS = 3;

    private final SHCookieClicker plugin;
    private final boolean available;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private BukkitTask task;

    /** Les objets montés à un joueur, par clicker. */
    private static final class View {
        final Player player;
        final Map<Clickers.Spot, Shown> shown = new ConcurrentHashMap<>();

        View(Player player) {
            this.player = player;
        }
    }

    /** Un objet monté à un joueur. */
    private static final class Shown {
        final int entityId;
        final Look look;
        /** Tick du prochain envoi de pose. */
        int nextFrame;
        /** 1 entre le sommet du bond et le retour au repos. */
        int phase;
        /** Tick du dernier bond demandé (fil du monde du joueur) et du dernier bond joué (fil principal). */
        volatile int popRequested = Integer.MIN_VALUE;
        int popPlayed = Integer.MIN_VALUE;

        Shown(int entityId, Look look) {
            this.entityId = entityId;
            this.look = look;
        }
    }

    public ClickerDisplay(SHCookieClicker plugin, boolean available) {
        this.plugin = plugin;
        this.available = available;
    }

    public void start() {
        if (available && task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        }
    }

    /** Arrêt du plugin : les objets disparaissent chez les joueurs. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearAll();
    }

    /** Rechargement de la configuration : tout est remonté avec les nouveaux réglages. */
    public void reload() {
        if (!available) {
            return;
        }
        clearAll();
        start();
        tick();
    }

    private void clearAll() {
        for (View view : views.values()) {
            for (Map.Entry<Clickers.Spot, Shown> entry : view.shown.entrySet()) {
                ClickerPackets.destroy(view.player, entry.getValue().entityId);
            }
        }
        views.clear();
    }

    /** Le joueur change de monde, meurt ou part : son client a déjà oublié les entités. */
    public void forget(Player player) {
        views.remove(player.getUniqueId());
    }

    /** Le joueur vient de changer de design : l'objet est remplacé sans attendre le prochain passage. */
    public void refresh(Player player) {
        if (available) {
            plugin.sync(() -> update(player, Bukkit.getCurrentTick()));
        }
    }

    /** Un clic qui compte sur ce clicker : l'objet du joueur fait un bond. */
    public void pop(Player player, Block block) {
        View view = views.get(player.getUniqueId());
        if (view == null) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        long key = block.getBlockKey();
        String world = block.getWorld().getName();
        for (Map.Entry<Clickers.Spot, Shown> entry : view.shown.entrySet()) {
            Clickers.Spot spot = entry.getKey();
            Shown shown = entry.getValue();
            if (spot.key() == key && spot.world().equals(world) && (long) now - shown.popRequested >= POP_GAP_TICKS) {
                shown.popRequested = now;
            }
        }
    }

    // ---- minuteur

    private void tick() {
        Appearance look = plugin.settings().appearance;
        int now = Bukkit.getCurrentTick();
        if (!look.enabled()) {
            if (!views.isEmpty()) {
                clearAll();
            }
            return;
        }
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
        if (!look.enabled() || session == null || !session.isLoaded() || !clickers.hasClickers(player.getWorld())) {
            if (view != null) {
                for (Shown shown : view.shown.values()) {
                    ClickerPackets.destroy(player, shown.entityId);
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
                ClickerPackets.destroy(player, entry.getValue().entityId);
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
                    ClickerPackets.destroy(player, shown.entityId);
                    view.shown.remove(spot);
                } else if (!shown.look.key().equals(wanted.key())) {
                    ClickerPackets.destroy(player, shown.entityId);
                    view.shown.put(spot, spawn(player, spot, wanted, look, now));
                }
            } else if (distance <= (double) radius * radius) {
                view.shown.put(spot, spawn(player, spot, wanted, look, now));
            }
        }
    }

    private Shown spawn(Player player, Clickers.Spot spot, Look wanted, Appearance look, int now) {
        int id = ClickerPackets.newEntityId();
        ClickerPackets.spawn(player, id, spot.x() + 0.5, spot.y() + 0.5 + look.height(), spot.z() + 0.5, wanted,
                Pose.at(look, wanted.isBlock(), now, 0));
        Shown shown = new Shown(id, wanted);
        shown.nextFrame = now;
        return shown;
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
