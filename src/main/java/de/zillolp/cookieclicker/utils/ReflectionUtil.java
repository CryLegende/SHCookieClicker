package de.zillolp.cookieclicker.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Version SH : toutes les méthodes passent par l'API Paper. L'original envoyait des paquets NMS (crânes factices,
 * profils, connexion du joueur) par réflexion, ce que Paper 26.x ne permet plus (plus de noms Spigot ni de
 * paquets versionnés). Les méthodes publiques gardent leur signature pour le reste du plugin.
 */
public class ReflectionUtil {
    private final int MAX_API_CALLS_PER_MINUTE = 600;
    private final CookieClicker plugin;
    private final BukkitScheduler bukkitScheduler;
    private final Logger logger;
    private final Semaphore apiRateLimiter = new Semaphore(MAX_API_CALLS_PER_MINUTE);
    private final Map<String, String> playerTextures = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 500;
        }
    };
    private long lastRateLimitReset = System.currentTimeMillis();

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

    // profil réel du joueur (skin appliqué par SkinsRestorer compris)
    public PlayerProfile getRealPlayerProfile(Player player) {
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

    public CompletableFuture<String> getTextureURL(String playerName) {
        String inputName = playerName.toLowerCase();
        CompletableFuture<String> completableFuture = new CompletableFuture<>();

        synchronized (playerTextures) {
            if (playerTextures.containsKey(inputName)) {
                completableFuture.complete(playerTextures.get(inputName));
                return completableFuture;
            }
        }

        bukkitScheduler.runTaskAsynchronously(plugin, () -> {
            try {
                resetRateLimiterIfNeeded();

                if (!apiRateLimiter.tryAcquire()) {
                    logger.log(Level.WARNING, "API rate limit reached, skipping texture fetch for: " + playerName);
                    completableFuture.complete(null);
                    return;
                }

                try {
                    URL uuidURL = URI.create("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + playerName).toURL();
                    try (InputStreamReader uuidReader = new InputStreamReader(uuidURL.openStream())) {
                        JsonObject uuidJson = JsonParser.parseReader(uuidReader).getAsJsonObject();
                        if (uuidJson == null || !uuidJson.has("id")) {
                            completableFuture.complete(null);
                            return;
                        }
                        String uuid = uuidJson.get("id").getAsString();

                        URL profileURL = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid).toURL();
                        try (InputStreamReader profileReader = new InputStreamReader(profileURL.openStream())) {
                            JsonObject profileResponse = JsonParser.parseReader(profileReader).getAsJsonObject();
                            if (profileResponse == null || !profileResponse.has("properties")) {
                                completableFuture.complete(null);
                                return;
                            }

                            String base64Encoded = profileResponse.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
                            String decodedJson = new String(Base64.getDecoder().decode(base64Encoded));

                            JsonObject textureObject = JsonParser.parseString(decodedJson).getAsJsonObject();
                            String textureURL = textureObject.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();

                            synchronized (playerTextures) {
                                playerTextures.put(inputName, textureURL);
                            }
                            completableFuture.complete(textureURL);
                        }
                    }
                } finally {
                    apiRateLimiter.release();
                }
            } catch (IOException exception) {
                if (exception.getMessage() != null && exception.getMessage().contains("404")) {
                    completableFuture.complete(null);
                } else {
                    logger.log(Level.SEVERE, "Error getting TextureURL for player: " + playerName, exception);
                    completableFuture.complete(null);
                }
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Error getting TextureURL for player: " + playerName, exception);
                completableFuture.complete(null);
            }
        });
        return completableFuture;
    }

    private synchronized void resetRateLimiterIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRateLimitReset >= 60000) {
            int availablePermits = apiRateLimiter.availablePermits();
            int usedPermits = MAX_API_CALLS_PER_MINUTE - availablePermits;
            apiRateLimiter.release(usedPermits);
            lastRateLimitReset = currentTime;
        }
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
