package de.zillolp.cookieclicker.utils;

import de.zillolp.cookieclicker.CookieClicker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Version SH : toutes les méthodes passent par l'API Paper. L'original envoyait des paquets NMS (crânes factices,
 * profils, connexion du joueur) par réflexion, ce que Paper 26.x ne permet plus (plus de noms Spigot ni de
 * paquets versionnés). Les méthodes publiques gardent leur signature pour le reste du plugin.
 */
public class ReflectionUtil {
    private final CookieClicker plugin;
    private final BukkitScheduler bukkitScheduler;
    private final Logger logger;

    public ReflectionUtil(CookieClicker plugin) {
        this.plugin = plugin;
        bukkitScheduler = plugin.getServer().getScheduler();
        logger = plugin.getLogger();
    }

    public Object invokeMethod(Object instance, String methodName) {
        Class<?> instanceClass = instance.getClass();
        try {
            Method method = instanceClass.getMethod(methodName);
            method.setAccessible(true);

            return method.invoke(instance);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            logger.log(Level.SEVERE, "Error invoking method " + methodName + " on " + instanceClass.getName(), exception);
            return null;
        }
    }

    public void sendRealPlayerSkullBlock(Player player, Location location, BlockData blockData) {
        sendSkullBlock(player, location, blockData, getRealPlayerProfile(player));
    }

    // crâne affiché à un seul joueur : bloc factice puis données du bloc (profil et texture)
    public void sendSkullBlock(Player player, Location location, BlockData blockData, PlayerProfile playerProfile) {
        if (location.getWorld() == null) {
            return;
        }
        BlockState blockState = blockData.createBlockState();
        player.sendBlockChange(location, blockData);
        if (blockState instanceof Skull skull && playerProfile != null) {
            skull.setOwnerProfile(playerProfile);
            player.sendBlockUpdate(location, skull);
        }
    }

    public boolean isSpigot() {
        String serverName = Bukkit.getServer().getName();
        return serverName.equalsIgnoreCase("spigot") || serverName.equalsIgnoreCase("CraftBukkit");
    }

    public boolean isSameLocation(Location location, Location location1) {
        if (location == null || location1 == null) {
            return false;
        }
        World world = location.getWorld();
        World world1 = location1.getWorld();
        if (world == null || world1 == null) {
            return false;
        }
        return world.getName().equals(world1.getName()) && location.getX() == location1.getX() && location.getY() == location1.getY() && location.getZ() == location1.getZ();
    }

    // profil du joueur connecté : le skin de SHSkins, sinon celui reçu à sa connexion
    public com.destroystokyo.paper.profile.PlayerProfile getRealPlayerProfile(Player player) {
        com.destroystokyo.paper.profile.PlayerProfile profile = PlayerHeads.profile(player.getUniqueId(), player.getName());
        if (profile.hasTextures()) {
            return profile;
        }
        return player.getPlayerProfile();
    }

    public CompletableFuture<PlayerProfile> getPlayerProfile(String url, String name) {
        CompletableFuture<PlayerProfile> completableFuture = new CompletableFuture<>();
        bukkitScheduler.runTaskAsynchronously(plugin, () -> {
            PlayerProfile playerProfile = Bukkit.createPlayerProfile(UUID.randomUUID(), name);
            PlayerTextures playerTextures = playerProfile.getTextures();
            URL urlObject = null;
            try {
                urlObject = URI.create(url).toURL();
            } catch (MalformedURLException | IllegalArgumentException exception) {
                completableFuture.complete(playerProfile);
            }
            if (urlObject != null) {
                playerTextures.setSkin(urlObject);
            }
            playerProfile.setTextures(playerTextures);
            completableFuture.complete(playerProfile);
        });
        return completableFuture;
    }

    // les configs gardent les anciens noms d'enum (ENTITY_PLAYER_LEVELUP) ; en 26.x les sons sont un registre
    public Object getSound(String soundName) {
        String key = soundName.toLowerCase(Locale.ROOT);
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(key));
        if (sound == null) {
            sound = Registry.SOUNDS.get(NamespacedKey.minecraft(key.replace('_', '.')));
        }
        if (sound == null) {
            for (Sound candidate : Registry.SOUNDS) {
                if (candidate.getKey().getKey().replace('.', '_').equalsIgnoreCase(soundName)) {
                    sound = candidate;
                    break;
                }
            }
        }
        if (sound == null) {
            logger.log(Level.SEVERE, "Error getting Sound " + soundName);
        }
        return sound;
    }

    public Inventory getOpenInventory(Player player) {
        return player.getOpenInventory().getTopInventory();
    }

    public String getInventoryTitle(InventoryEvent event) {
        return event.getView().getTitle();
    }
}
