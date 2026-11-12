package de.zillolp.cookieclicker.manager;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.database.DatabaseManager;
import de.zillolp.cookieclicker.profiles.ClickerGameProfile;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Profils en mémoire : la progression de tous les joueurs de la base, et l'état de jeu des joueurs connectés. */
public class ClickerPlayerManager {
    private final CookieClicker plugin;
    private final DatabaseManager databaseManager;
    private final ConcurrentHashMap<UUID, ClickerGameProfile> clickerGameProfiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ClickerStatsProfile> clickerStatsProfiles = new ConcurrentHashMap<>();

    public ClickerPlayerManager(CookieClicker plugin) {
        this.plugin = plugin;
        databaseManager = plugin.getDatabaseManager();
    }

    /** Lit tous les joueurs de la base en deux requêtes. À appeler au démarrage. */
    public void loadProfiles() {
        databaseManager.loadAllProfiles().forEach(clickerStatsProfiles::putIfAbsent);
    }

    /** État de jeu d'un joueur qui se connecte, créé tout de suite : ses clics ne comptent qu'une fois la progression prête. */
    public ClickerGameProfile createGameProfile(UUID uuid) {
        ClickerGameProfile clickerGameProfile = new ClickerGameProfile();
        clickerGameProfiles.put(uuid, clickerGameProfile);
        return clickerGameProfile;
    }

    /**
     * Progression d'un joueur qui se connecte : celle en mémoire, ou une nouvelle (écrite en base). En mode réseau
     * elle est relue en base, l'autre serveur a pu la changer. À appeler hors du fil principal.
     */
    public ClickerStatsProfile loadPlayerProfile(UUID uuid, String name, boolean reread) {
        ClickerStatsProfile profile = clickerStatsProfiles.get(uuid);
        if (profile == null) {
            profile = reread ? databaseManager.loadProfile(uuid) : null;
            if (profile == null) {
                profile = new ClickerStatsProfile(uuid, name);
                databaseManager.saveProfile(profile);
            }
            ClickerStatsProfile existing = clickerStatsProfiles.putIfAbsent(uuid, profile);
            if (existing != null) {
                profile = existing;
            }
        } else if (reread) {
            databaseManager.reloadProfile(profile);
        }
        if (!(name.equals(profile.getName()))) {
            profile.setName(name);
        }
        return profile;
    }

    /** Départ d'un joueur : l'état de jeu disparaît, la progression reste en mémoire pour le classement. */
    public void unregisterPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        plugin.getHologramManager().deleteHolograms(player);
        plugin.getDesignManager().stopParticleEffects(uuid);
        clickerGameProfiles.remove(uuid);
    }

    public ClickerGameProfile getGameProfile(UUID uuid) {
        ClickerGameProfile clickerGameProfile = clickerGameProfiles.get(uuid);
        if (clickerGameProfile != null) {
            return clickerGameProfile;
        }
        return new ClickerGameProfile();
    }

    /** Progression d'un joueur ; un profil neuf, non enregistré, pour un joueur inconnu. */
    public ClickerStatsProfile getStatsProfile(UUID uuid) {
        ClickerStatsProfile clickerStatsProfile = clickerStatsProfiles.get(uuid);
        if (clickerStatsProfile != null) {
            return clickerStatsProfile;
        }
        return new ClickerStatsProfile(uuid, "?");
    }

    public boolean hasStatsProfile(UUID uuid) {
        return clickerStatsProfiles.containsKey(uuid);
    }

    /** Progression d'un joueur connu d'après son pseudo, sans tenir compte de la casse ; null s'il est inconnu. */
    public ClickerStatsProfile findProfile(String name) {
        for (ClickerStatsProfile profile : clickerStatsProfiles.values()) {
            if (profile.getName().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        return null;
    }

    /** Comme {@link #findProfile}, puis la base : un joueur arrivé sur un autre serveur depuis le démarrage n'est pas en mémoire. */
    public ClickerStatsProfile findOrLoadProfile(String name) {
        ClickerStatsProfile profile = findProfile(name);
        if (profile != null) {
            return profile;
        }
        Optional<UUID> uuid = databaseManager.findUuidByName(name);
        if (uuid.isEmpty()) {
            return null;
        }
        profile = databaseManager.loadProfile(uuid.get());
        if (profile == null) {
            return null;
        }
        ClickerStatsProfile existing = clickerStatsProfiles.putIfAbsent(uuid.get(), profile);
        return existing != null ? existing : profile;
    }

    public ConcurrentHashMap<UUID, ClickerStatsProfile> getClickerStatsProfiles() {
        return clickerStatsProfiles;
    }
}
