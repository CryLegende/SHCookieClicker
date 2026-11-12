package org.Cry_Legende.sHCookieClicker.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les clickers de locations.yml (« locations.CookieClicker.Clicker-n »). Savoir si un bloc est un clicker se fait
 * en une recherche, sans parcourir la liste.
 */
public final class Clickers {
    private static final String SECTION = "locations.CookieClicker";

    /** Un clicker : son numéro et son bloc. */
    public record Spot(int number, String world, int x, int y, int z) {
        public Location location() {
            World bukkitWorld = Bukkit.getWorld(world);
            return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z);
        }

        public long key() {
            return Block.getBlockKey(x, y, z);
        }
    }

    private final List<Spot> spots;
    private final Map<String, Set<Long>> blocks = new HashMap<>();
    private final Map<String, Set<Long>> chunks = new HashMap<>();

    private Clickers(List<Spot> spots) {
        this.spots = Collections.unmodifiableList(spots);
        for (Spot spot : spots) {
            blocks.computeIfAbsent(spot.world(), key -> new HashSet<>()).add(spot.key());
            chunks.computeIfAbsent(spot.world(), key -> new HashSet<>()).add(chunkKey(spot.x() >> 4, spot.z() >> 4));
        }
    }

    public static Clickers load(YamlConfiguration file) {
        List<Spot> spots = new ArrayList<>();
        ConfigurationSection section = file.getConfigurationSection(SECTION);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Spot spot = read(file, SECTION + "." + key, number(key));
                if (spot != null) {
                    spots.add(spot);
                }
            }
        }
        spots.sort((a, b) -> Integer.compare(a.number(), b.number()));
        return new Clickers(spots);
    }

    private static int number(String key) {
        try {
            return Integer.parseInt(key.replace("Clicker-", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    // Deux écritures existent : la Location sérialisée de CookieClicker 4.x (« ==: org.bukkit.Location ») et les clés world/x/y/z
    private static Spot read(YamlConfiguration file, String path, int number) {
        Object value = file.get(path);
        if (value instanceof Location location) {
            String world = location.isWorldLoaded() && location.getWorld() != null ? location.getWorld().getName() : file.getString(path + ".world", "world");
            return new Spot(number, world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
        if (!(value instanceof ConfigurationSection section)) {
            return null;
        }
        return new Spot(number, section.getString("world", "world"), (int) Math.floor(section.getDouble("x")),
                (int) Math.floor(section.getDouble("y")), (int) Math.floor(section.getDouble("z")));
    }

    public List<Spot> all() {
        return spots;
    }

    public boolean isEmpty() {
        return spots.isEmpty();
    }

    public boolean isClicker(Block block) {
        Set<Long> keys = blocks.get(block.getWorld().getName());
        return keys != null && keys.contains(block.getBlockKey());
    }

    public boolean hasClickers(World world) {
        return blocks.containsKey(world.getName());
    }

    public boolean isClickerChunk(World world, int chunkX, int chunkZ) {
        Set<Long> keys = chunks.get(world.getName());
        return keys != null && keys.contains(chunkKey(chunkX, chunkZ));
    }

    public Spot find(int number) {
        for (Spot spot : spots) {
            if (spot.number() == number) {
                return spot;
            }
        }
        return null;
    }

    /** Vrai si le joueur est à moins de {@code distance} blocs d'un clicker de son monde. */
    public boolean near(Location location, double distance) {
        String world = location.getWorld().getName();
        double max = distance * distance;
        for (Spot spot : spots) {
            if (spot.world().equals(world)) {
                double dx = spot.x() + 0.5 - location.getX();
                double dy = spot.y() + 0.5 - location.getY();
                double dz = spot.z() + 0.5 - location.getZ();
                if (dx * dx + dy * dy + dz * dz <= max) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long chunkKey(int x, int z) {
        return ((long) z << 32) | (x & 0xFFFFFFFFL);
    }

    /** Ajoute ou retire un clicker dans locations.yml (le reste du fichier n'est pas touché). */
    public static void write(File file, int number, Block block) throws IOException {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = SECTION + ".Clicker-" + number;
        if (block == null) {
            config.set(path, null);
        } else {
            config.set(path, null);
            config.set(path + ".world", block.getWorld().getName());
            config.set(path + ".x", (double) block.getX());
            config.set(path + ".y", (double) block.getY());
            config.set(path + ".z", (double) block.getZ());
            config.set(path + ".yaw", 0.0);
            config.set(path + ".pitch", 0.0);
        }
        config.save(file);
    }
}
