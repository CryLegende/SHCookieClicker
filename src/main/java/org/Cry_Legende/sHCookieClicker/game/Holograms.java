package org.Cry_Legende.sHCookieClicker.game;

import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hologrammes : un texte au-dessus de chaque clicker (le même pour tous, une seule entité par clicker) et le gain
 * d'un cookie ramassé, qui s'élève au-dessus de lui, vu du seul joueur. Rien n'est enregistré dans le monde.
 */
public final class Holograms {
    private static final long CHECK_TICKS = 100;
    private static final int GAIN_TICKS = 20;

    private final SHCookieClicker plugin;
    private final Map<Clickers.Spot, TextDisplay> clickerTexts = new HashMap<>();
    private final Set<TextDisplay> gains = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public Holograms(SHCookieClicker plugin) {
        this.plugin = plugin;
    }

    /** Textes des clickers, si hologrammes.clicker est activé : posés quand le tronçon est chargé, vérifiés toutes les 5 s. */
    public void start() {
        if (!plugin.settings().clickerHologram) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::ensureClickerTexts, 1, CHECK_TICKS);
    }

    public void restart() {
        stopClickerTexts();
        start();
    }

    public void stop() {
        stopClickerTexts();
        for (TextDisplay display : gains) {
            display.remove();
        }
        gains.clear();
    }

    private void stopClickerTexts() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clickerTexts.values().forEach(TextDisplay::remove);
        clickerTexts.clear();
    }

    private void ensureClickerTexts() {
        String text = String.join("\n", plugin.messages().lines("hologrammes.clicker"));
        for (Clickers.Spot spot : plugin.clickers().all()) {
            TextDisplay display = clickerTexts.get(spot);
            if (display != null && display.isValid()) {
                continue;
            }
            World world = Bukkit.getWorld(spot.world());
            if (world == null || !world.isChunkLoaded(spot.x() >> 4, spot.z() >> 4)) {
                continue;
            }
            Location at = new Location(world, spot.x() + 0.5, spot.y() + 1.6, spot.z() + 0.5);
            clickerTexts.put(spot, world.spawn(at, TextDisplay.class, created -> {
                created.setPersistent(false);
                created.setBillboard(Display.Billboard.CENTER);
                created.text(Text.of(text));
            }));
        }
    }

    /** Fil du monde du joueur : le gain apparaît sur le cookie ramassé et monte d'un bloc et demi. */
    void gain(Player player, Location at, long gain) {
        if (!plugin.settings().gainHolograms) {
            return;
        }
        String text = plugin.messages().raw("hologrammes.gain", "gain", plugin.number(gain));
        if (text.isEmpty()) {
            return;
        }
        TextDisplay display = at.getWorld().spawn(at.clone().add(0, 0.4, 0), TextDisplay.class, created -> {
            created.setPersistent(false);
            created.setVisibleByDefault(false);
            created.setBillboard(Display.Billboard.CENTER);
            created.setSeeThrough(true);
            created.setTeleportDuration(GAIN_TICKS);
            created.text(Text.of(text));
        });
        player.showEntity(plugin, display);
        gains.add(display);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.teleport(display.getLocation().add(0, 1.5, 0));
            }
        }, 2);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            gains.remove(display);
            display.remove();
        }, GAIN_TICKS + 4);
    }
}
