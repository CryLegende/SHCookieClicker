package org.Cry_Legende.sHCookieClicker.hook;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profils de têtes. La tête d'un joueur prend son skin dans SHSkins quand il est installé (le réseau tourne en
 * UUID « hors ligne », inconnus de Mojang), sinon le profil reçu à sa connexion. Aucune requête réseau.
 */
public final class Heads {
    private static final Map<String, PlayerProfile> TEXTURES = new ConcurrentHashMap<>();

    private Heads() {
    }

    public static PlayerProfile of(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("SHSkins")) {
            try {
                PlayerProfile profile = SkinsHook.profile(player.getUniqueId(), player.getName());
                if (profile != null && profile.hasTextures()) {
                    return profile;
                }
            } catch (LinkageError ignored) {
                // SHSkins d'une autre version : le profil du joueur suffit
            }
        }
        return player.getPlayerProfile();
    }

    /** Tête à texture (adresse textures.minecraft.net ou identifiant de texture). */
    public static PlayerProfile texture(String texture) {
        String url = texture.startsWith("http") ? texture : "http://textures.minecraft.net/texture/" + texture;
        return TEXTURES.computeIfAbsent(url, key -> {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), "");
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + key + "\"}}}";
            profile.setProperty(new ProfileProperty("textures", Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8))));
            return profile;
        });
    }
}
