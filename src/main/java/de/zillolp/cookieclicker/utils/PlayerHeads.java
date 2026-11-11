package de.zillolp.cookieclicker.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Profil d'un joueur du réseau pour une tête ou un crâne, connecté ou non. Le skin vient de SHSkins quand il est
 * installé ; sans lui, le profil n'a que l'UUID et le pseudo (le client affiche alors Steve ou Alex), mais le serveur
 * ne fait jamais de recherche Mojang.
 */
public final class PlayerHeads {

    private PlayerHeads() {
    }

    /**
     * Profil du joueur avec ses textures quand SHSkins les connaît.
     *
     * @param uuid UUID du joueur, ou null pour celui de son pseudo sur le réseau
     * @param name pseudo du joueur, ou null si seul l'UUID est connu
     */
    public static PlayerProfile profile(UUID uuid, String name) {
        if (Bukkit.getPluginManager().isPluginEnabled("SHSkins")) {
            return SHSkinsHook.profile(uuid, name);
        }
        UUID id = uuid != null ? uuid : offlineUuid(name);
        return Bukkit.createProfile(id, name);
    }

    /** UUID d'un joueur sur le réseau SkyHand (mode offline) : md5 de « OfflinePlayer:pseudo ». */
    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
