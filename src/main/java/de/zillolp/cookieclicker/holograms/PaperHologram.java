package de.zillolp.cookieclicker.holograms;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.interfaces.Hologram;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.UUID;

/**
 * Hologramme vu par un seul joueur, avec l'API Paper (version SH) : un TextDisplay non sauvegardé, caché aux autres
 * joueurs. Remplace les modules NMS Hologram_v1_xx_Rx (ArmorStand factice envoyé en paquets), qui ne compilent plus
 * en 26.x. Les appels arrivent aussi de tâches asynchrones (MovingHologram, ClickerHandler) : l'entité n'est
 * manipulée que sur le thread principal.
 */
public class PaperHologram implements Hologram {
    // le nom d'un ArmorStand marqueur s'affiche 0,5 bloc au-dessus de sa position ; le texte d'un TextDisplay
    // part de sa position
    private static final double NAME_OFFSET = 0.5;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final CookieClicker plugin;
    private final UUID uuid;
    private String line;
    private Location spawnLocation;
    private volatile Location currentLocation;
    private TextDisplay textDisplay;

    public PaperHologram(CookieClicker plugin, String line) {
        this.plugin = plugin;
        uuid = UUID.randomUUID();
        this.line = line;
    }

    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private Component text(String line) {
        return LEGACY.deserialize(ChatColor.translateAlternateColorCodes('&', line));
    }

    @Override
    public void spawn(Player player, Location location) {
        World world = location.getWorld();
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
        }
        spawnLocation = new Location(world, location.getX(), location.getY(), location.getZ());
        currentLocation = spawnLocation.clone();
        Location displayLocation = spawnLocation.clone().add(0, NAME_OFFSET, 0);
        sync(() -> {
            if (textDisplay != null || !player.isOnline()) {
                return;
            }
            textDisplay = displayLocation.getWorld().spawn(displayLocation, TextDisplay.class, display -> {
                display.setPersistent(false);
                display.setVisibleByDefault(false);
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(true);
                display.setTeleportDuration(1);
                display.text(text(line));
            });
            player.showEntity(plugin, textDisplay);
        });
    }

    @Override
    public void destroy(Player player) {
        sync(() -> {
            if (textDisplay == null) {
                return;
            }
            textDisplay.remove();
            textDisplay = null;
        });
    }

    @Override
    public void changeLine(Player player, String line) {
        this.line = line;
        sync(() -> {
            if (textDisplay != null && textDisplay.isValid()) {
                textDisplay.text(text(line));
            }
        });
    }

    @Override
    public void moveHologram(Player player, Location location) {
        currentLocation = location.clone();
        Location displayLocation = location.clone().add(0, NAME_OFFSET, 0);
        sync(() -> {
            if (textDisplay != null && textDisplay.isValid()) {
                textDisplay.teleport(displayLocation);
            }
        });
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public Location getSpawnLocation() {
        return spawnLocation;
    }

    @Override
    public Location getCurrentLocation() {
        return currentLocation;
    }
}
