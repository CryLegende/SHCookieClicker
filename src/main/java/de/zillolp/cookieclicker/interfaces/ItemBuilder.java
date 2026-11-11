package de.zillolp.cookieclicker.interfaces;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public interface ItemBuilder {

    ItemStack build(Material material, String displayName, int amount);

    ItemStack build(Material material, String displayName, int amount, boolean hideItemInfos);

    ItemStack build(Material material, String displayName, int amount, String[] lore, boolean hideItemInfos);

    ItemStack build(Material material, String displayName, int amount, String[] lore, boolean hasEnchantment, boolean hideItemInfos, String textureURL, PlayerProfile playerProfile);

    ItemStack build(Material material, String displayName, int amount, boolean hideItemInfos, String textureURL);

    ItemStack build(Material material, String displayName, int amount, String textureURL);
}
