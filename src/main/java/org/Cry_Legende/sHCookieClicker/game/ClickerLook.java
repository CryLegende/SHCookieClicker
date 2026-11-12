package org.Cry_Legende.sHCookieClicker.game;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.Cry_Legende.sHCookieClicker.SHCookieClicker;
import org.Cry_Legende.sHCookieClicker.config.Designs;
import org.Cry_Legende.sHCookieClicker.data.Profile;
import org.Cry_Legende.sHCookieClicker.data.ShopKind;
import org.Cry_Legende.sHCookieClicker.hook.Heads;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * Design « bloc » : le joueur voit son bloc à la place du clicker, envoyé à lui seul. Le jeu oublie ce bloc quand
 * il recharge le tronçon : il est renvoyé à chaque envoi du tronçon du clicker.
 */
public final class ClickerLook {
    private final SHCookieClicker plugin;

    public ClickerLook(SHCookieClicker plugin) {
        this.plugin = plugin;
    }

    /** Envoie au joueur le bloc de son design sur chaque clicker de son monde. */
    public void show(Player player) {
        Designs.Block design = selected(player);
        if (design == null || !plugin.clickers().hasClickers(player.getWorld())) {
            return;
        }
        for (Clickers.Spot spot : plugin.clickers().all()) {
            if (spot.world().equals(player.getWorld().getName())) {
                send(player, new Location(player.getWorld(), spot.x(), spot.y(), spot.z()), design);
            }
        }
    }

    /** Rend au joueur le vrai bloc de chaque clicker de son monde. */
    public void reset(Player player) {
        for (Clickers.Spot spot : plugin.clickers().all()) {
            if (!spot.world().equals(player.getWorld().getName())) {
                continue;
            }
            Location location = new Location(player.getWorld(), spot.x(), spot.y(), spot.z());
            Block block = location.getBlock();
            player.sendBlockChange(location, block.getBlockData());
            BlockState state = block.getState(false);
            if (state instanceof TileState tile) {
                player.sendBlockUpdate(location, tile);
            }
        }
    }

    private Designs.Block selected(Player player) {
        Profile profile = plugin.profiles().get(player.getUniqueId());
        if (profile == null) {
            return null;
        }
        long id = profile.selected(ShopKind.BLOCK_DESIGN);
        return id <= 0 ? null : plugin.settings().block(id);
    }

    private void send(Player player, Location location, Designs.Block design) {
        if (!design.isHead()) {
            player.sendBlockChange(location, design.block().createBlockData());
            return;
        }
        PlayerProfile profile = design.playerHead() ? Heads.of(player) : design.texture() != null ? Heads.texture(design.texture()) : null;
        BlockData data = Material.PLAYER_HEAD.createBlockData();
        player.sendBlockChange(location, data);
        if (profile != null && data.createBlockState() instanceof Skull skull) {
            skull.setPlayerProfile(profile);
            player.sendBlockUpdate(location, skull);
        }
    }
}
