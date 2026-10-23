package de.zillolp.cookieclicker.config.customconfigs;

import de.zillolp.cookieclicker.CookieClicker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;

public class LocationConfig extends CustomConfig {
    private final String mainPath = "locations.";

    public LocationConfig(CookieClicker plugin, String name) {
        super(plugin, name);
    }

    public boolean isLocation(String locationName) {
        return fileConfiguration.contains(mainPath + locationName) && getLocation(locationName) != null;
    }

    public Location getLocation(String locationName) {
        String section = mainPath + locationName;
        // Version SH : les positions écrites par CookieClicker 4.x sont des Location Bukkit sérialisées
        // (« ==: org.bukkit.Location »), que la lecture clé par clé ci-dessous voyait en 0, 0, 0. C'était le cas
        // du clicker d'Opale depuis le passage 4.8 -> 5.9.
        if (fileConfiguration.get(section) instanceof Location storedLocation) {
            Location location = storedLocation.clone();
            if (location.getWorld() == null) {
                location.setWorld(Bukkit.getWorlds().getFirst());
            }
            return location;
        }
        World world = Bukkit.getWorld(fileConfiguration.getString(section + ".world", "world"));
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
        }
        double x = fileConfiguration.getDouble(section + ".x", 0);
        double y = fileConfiguration.getDouble(section + ".y", 0);
        double z = fileConfiguration.getDouble(section + ".z", 0);
        float yaw = (float) fileConfiguration.getDouble(section + ".yaw", 0);
        float pitch = (float) fileConfiguration.getDouble(section + ".pitch", 0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public ArrayList<Location> getLocations(String section) {
        ArrayList<Location> locations = new ArrayList<>();
        for (String locationName : getConfigurationSection(mainPath + section)) {
            locations.add(getLocation(section + "." + locationName));
        }
        return locations;
    }

    public ArrayList<String> getClickerNumbers(String section) {
        ArrayList<String> locations = new ArrayList<>();
        for (String locationName : getConfigurationSection(mainPath + section)) {
            locations.add(locationName.replace("Clicker-", ""));
        }
        return locations;
    }

    public void saveLocation(String locationName, Location location) {
        World world = location.getWorld();
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
        }
        String section = mainPath + locationName;
        fileConfiguration.set(section + ".world", world.getName());
        fileConfiguration.set(section + ".x", location.getX());
        fileConfiguration.set(section + ".y", location.getY());
        fileConfiguration.set(section + ".z", location.getZ());
        fileConfiguration.set(section + ".yaw", location.getYaw());
        fileConfiguration.set(section + ".pitch", location.getPitch());
        save();
    }

    public void removeLocation(String locationName) {
        fileConfiguration.set(mainPath + locationName, null);
        save();
    }
}
