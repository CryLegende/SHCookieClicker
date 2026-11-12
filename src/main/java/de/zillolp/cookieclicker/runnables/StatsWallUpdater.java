package de.zillolp.cookieclicker.runnables;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.config.customconfigs.LanguageConfig;
import de.zillolp.cookieclicker.manager.ClickerPlayerManager;
import de.zillolp.cookieclicker.manager.CookieClickerManager;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import de.zillolp.cookieclicker.utils.Items;
import de.zillolp.cookieclicker.utils.PlayerHeads;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StatsWallUpdater extends CustomUpdater {
    private static final String UNKNOWN_SKULL_TEXTURE = "http://textures.minecraft.net/texture/badc048a7ce78f7dad72a07da27d85c0916881e5522eeed1e3daf217a38c1a";
    private final CookieClicker plugin;
    private final LanguageConfig languageConfig;
    private final ClickerPlayerManager clickerPlayerManager;
    // Gains de cookies par clic de la période en cours (classement « time »), par pseudo
    private final ConcurrentHashMap<String, Long> cachedTimedData = new ConcurrentHashMap<>();
    // Classements publiés d'un bloc : jamais modifiés après publication, lus depuis n'importe quel fil
    private volatile Map<String, Long> sortedAlltimeData = Map.of();
    private volatile Map<String, Integer> alltimeRanks = Map.of();
    private volatile Map<String, Long> sortedTimedData = Map.of();
    private final LinkedHashMap<Integer, Location[]> allTimeLocations;
    private final LinkedHashMap<Integer, Location[]> timeLocations;

    public StatsWallUpdater(CookieClicker plugin) {
        super(plugin, true, 200);
        this.plugin = plugin;
        languageConfig = plugin.getLanguageConfig();

        CookieClickerManager cookieClickerManager = plugin.getCookieClickerManager();
        clickerPlayerManager = plugin.getClickerPlayerManager();
        allTimeLocations = cookieClickerManager.getAlltimeLocations();
        timeLocations = cookieClickerManager.getTimeLocations();

        refreshAlltime();
        updateTimeStatsWall();
    }

    @Override
    protected void tick() {
        refreshAlltime();
        runOnMainThread(() -> renderWall(allTimeLocations, sortedAlltimeData));
        if (sortedTimedData.isEmpty() && cachedTimedData.isEmpty()) {
            return;
        }
        updateTimeStatsWall();
    }

    private void runOnMainThread(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            bukkitScheduler.runTask(plugin, task);
        }
    }

    /** Classement de tous les joueurs par cookies par clic, et rang de chacun. */
    public void refreshAlltime() {
        Map<String, Long> values = new HashMap<>();
        for (ClickerStatsProfile clickerStatsProfile : clickerPlayerManager.getClickerStatsProfiles().values()) {
            if (!(clickerStatsProfile.getName().equals("?"))) {
                values.put(clickerStatsProfile.getName(), clickerStatsProfile.getPerClick());
            }
        }
        LinkedHashMap<String, Long> sorted = sort(values);
        Map<String, Integer> ranks = new HashMap<>();
        int rank = 1;
        for (String name : sorted.keySet()) {
            ranks.put(name, rank++);
        }
        sortedAlltimeData = Collections.unmodifiableMap(sorted);
        alltimeRanks = ranks;
    }

    private LinkedHashMap<String, Long> sort(Map<String, Long> values) {
        LinkedHashMap<String, Long> sorted = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).forEachOrdered(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    public void updateAlltimeStatsWall() {
        refreshAlltime();
        runOnMainThread(() -> renderWall(allTimeLocations, sortedAlltimeData));
    }

    public void updateTimeStatsWall() {
        sortedTimedData = Collections.unmodifiableMap(sort(new HashMap<>(cachedTimedData)));
        runOnMainThread(() -> renderWall(timeLocations, sortedTimedData));
    }

    /** Une amélioration achetée compte dans le classement de la période. */
    public void addTimedGain(String name, long perClick) {
        cachedTimedData.merge(name, perClick, Long::sum);
    }

    public void clearTimedData() {
        cachedTimedData.clear();
        sortedTimedData = Map.of();
    }

    /** Rang d'un joueur au classement général, 0 s'il n'y est pas. */
    public int getAlltimeRank(String name) {
        return alltimeRanks.getOrDefault(name, 0);
    }

    private void renderWall(LinkedHashMap<Integer, Location[]> wallLocations, Map<String, Long> sortedData) {
        if (wallLocations.isEmpty()) {
            return;
        }
        List<Map.Entry<String, Long>> entries = new ArrayList<>(sortedData.entrySet());
        for (Map.Entry<Integer, Location[]> statsWall : wallLocations.entrySet()) {
            int place = statsWall.getKey();
            int index = place - 1;
            if (entries.size() <= index) {
                setWall(statsWall.getValue(), null, languageConfig.getStatsWallLanguage("STATS_WALL.SIGN_LINES", getPlace(place), "?", "?"));
                continue;
            }
            Map.Entry<String, Long> entry = entries.get(index);
            setWall(statsWall.getValue(), entry.getKey(), languageConfig.getStatsWallLanguage("STATS_WALL.SIGN_LINES", getPlace(place), entry.getKey(), languageConfig.formatNumber(entry.getValue())));
        }
    }

    private void setWall(Location[] locations, String name, String[] lines) {
        Block headBlock = locations[0].getBlock();
        Block signBlock = locations[1].getBlock();
        if (headBlock.getType() == Material.AIR || signBlock.getType() == Material.AIR) {
            return;
        }
        if (signBlock.getState() instanceof Sign sign) {
            sign.setLine(0, lines[0]);
            sign.setLine(1, lines[1]);
            sign.setLine(2, lines[2]);
            sign.setLine(3, lines[3]);
            sign.update(true);
        }
        if (name != null && clickerPlayerManager.findProfile(name) != null) {
            setPlayerSkull(headBlock, name);
            return;
        }
        setUnknownSkull(headBlock);
    }

    // tête d'un joueur du classement, connecté ou non : son skin vient de SHSkins (en mémoire, sans requête réseau).
    // La tête n'est réécrite que si le pseudo ou les textures ont changé (le skin n'est pas connu au démarrage).
    private void setPlayerSkull(Block block, String name) {
        if (!(block.getState() instanceof Skull skull)) {
            return;
        }
        PlayerProfile wanted = PlayerHeads.profile(null, name);
        if (hasSameSkin(skull.getPlayerProfile(), wanted)) {
            return;
        }
        skull.setPlayerProfile(wanted);
        skull.update(true);
    }

    // tête « ? » d'une place sans joueur
    private void setUnknownSkull(Block block) {
        if (!(block.getState() instanceof Skull skull)) {
            return;
        }
        org.bukkit.profile.PlayerProfile wanted = Items.textureProfile(UNKNOWN_SKULL_TEXTURE);
        org.bukkit.profile.PlayerProfile current = skull.getOwnerProfile();
        if (current != null && wanted != null && Objects.equals(current.getTextures().getSkin(), wanted.getTextures().getSkin())) {
            return;
        }
        skull.setOwnerProfile(wanted);
        skull.update(true);
    }

    private boolean hasSameSkin(PlayerProfile current, PlayerProfile wanted) {
        if (current == null || current.getName() == null || !current.getName().equals(wanted.getName())) {
            return false;
        }
        return Objects.equals(getTextures(current), getTextures(wanted));
    }

    private String getTextures(PlayerProfile playerProfile) {
        for (ProfileProperty property : playerProfile.getProperties()) {
            if (property.getName().equals("textures")) {
                return property.getValue();
            }
        }
        return null;
    }

    private String getPlace(int place) {
        String path = "STATS_WALL.RANK_PREFIX.";
        if (place >= 1 && place <= 3) {
            path = path + place;
        } else {
            path = path + "DEFAULT";
        }
        return languageConfig.getTranslatedLanguage(path).replace("%number%", languageConfig.formatNumber((long) place));
    }

    public Map<String, Long> getSortedAlltimeData() {
        return sortedAlltimeData;
    }
}
