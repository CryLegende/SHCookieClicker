package de.zillolp.cookieclicker.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * Outils sur les blocs et les têtes, tous par l'API Paper. L'original envoyait des paquets NMS (crânes factices,
 * profils, connexion du joueur) par réflexion, ce que Paper 26.x ne permet plus (plus de noms Spigot ni de
 * paquets versionnés).
 */
public class ReflectionUtil {

    public void sendRealPlayerSkullBlock(Player player, Location location, BlockData blockData) {
        sendSkullBlock(player, location, blockData, getRealPlayerProfile(player));
    }

    // crâne affiché à un seul joueur : bloc factice puis données du bloc (profil et texture)
    public void sendSkullBlock(Player player, Location location, BlockData blockData, org.bukkit.profile.PlayerProfile playerProfile) {
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

    public boolean isSameLocation(Location location, Location location1) {
        if (location == null || location1 == null) {
            return false;
        }
        World world = location.getWorld();
        World world1 = location1.getWorld();
        if (world == null || world1 == null) {
            return false;
        }
        return world.getName().equals(world1.getName()) && location.getBlockX() == location1.getBlockX() && location.getBlockY() == location1.getBlockY() && location.getBlockZ() == location1.getBlockZ();
    }

    // profil du joueur connecté : le skin de SHSkins, sinon celui reçu à sa connexion
    public PlayerProfile getRealPlayerProfile(Player player) {
        PlayerProfile profile = PlayerHeads.profile(player.getUniqueId(), player.getName());
        if (profile.hasTextures()) {
            return profile;
        }
        return player.getPlayerProfile();
    }
}
