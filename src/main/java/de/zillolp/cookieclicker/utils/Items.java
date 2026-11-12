package de.zillolp.cookieclicker.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Objets des menus : nom et description sans italique, infobulle réduite au nom et à la description. */
public final class Items {
    private static final Map<String, PlayerProfile> TEXTURE_PROFILES = new ConcurrentHashMap<>();

    private Items() {
    }

    public static ItemStack build(Material material, String name, List<String> lore) {
        return build(material, name, lore, false, null);
    }

    /**
     * @param glint   reflet d'enchantement (design sélectionné)
     * @param profile profil d'une tête (ignoré si l'objet n'est pas une tête de joueur)
     */
    public static ItemStack build(Material material, String name, List<String> lore, boolean glint, PlayerProfile profile) {
        ItemStack itemStack = new ItemStack(material == null ? Material.BARRIER : material);
        itemStack.editMeta(meta -> {
            if (name != null) {
                meta.displayName(Text.item(name));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.lore(Text.items(lore));
            }
            if (glint) {
                meta.setEnchantmentGlintOverride(true);
            }
            hideTooltip(meta);
            if (profile != null && meta instanceof SkullMeta skull) {
                skull.setOwnerProfile(profile);
            }
        });
        return itemStack;
    }

    private static void hideTooltip(ItemMeta meta) {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    }

    /** Profil de tête d'une texture (adresse textures.minecraft.net ou identifiant de texture), sans aucune requête réseau. */
    public static PlayerProfile textureProfile(String texture) {
        if (texture == null || texture.isBlank()) {
            return null;
        }
        String url = texture.startsWith("http") ? texture : "http://textures.minecraft.net/texture/" + texture;
        return TEXTURE_PROFILES.computeIfAbsent(url, key -> {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(key.getBytes()), "");
            try {
                URL skin = URI.create(key).toURL();
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(skin);
                profile.setTextures(textures);
            } catch (MalformedURLException | IllegalArgumentException exception) {
                return profile;
            }
            return profile;
        });
    }
}
