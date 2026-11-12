package de.zillolp.cookieclicker.manager;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.config.customconfigs.PluginConfig;
import de.zillolp.cookieclicker.customparticles.*;
import de.zillolp.cookieclicker.enums.CustomParticleEffectType;
import de.zillolp.cookieclicker.enums.ShopType;
import de.zillolp.cookieclicker.profiles.ClickerStatsProfile;
import de.zillolp.cookieclicker.utils.Items;
import de.zillolp.cookieclicker.utils.ReflectionUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;

import java.util.HashMap;
import java.util.UUID;

/** Designs du joueur appliqués au clicker (bloc, effet de coup). Les blocs sont envoyés au seul joueur, sur le fil principal. */
public class DesignManager {
    private final CookieClicker plugin;
    private final PluginConfig pluginConfig;
    private final ClickerPlayerManager clickerPlayerManager;
    private final HashMap<UUID, HashMap<CustomParticleEffectType, CustomParticleEffect>> customParticleEffects = new HashMap<>();

    public DesignManager(CookieClicker plugin) {
        this.plugin = plugin;
        pluginConfig = plugin.getPluginConfig();
        clickerPlayerManager = plugin.getClickerPlayerManager();
    }

    /** Envoie au joueur le bloc de son design de clicker, s'il en a un de sélectionné. */
    public void sendClickerBlockDesign(Player player) {
        if (player == null) {
            return;
        }
        ShopType shopType = ShopType.BLOCK_DESIGN;
        ClickerStatsProfile statsProfile = clickerPlayerManager.getStatsProfile(player.getUniqueId());
        long blockDesign = statsProfile.getBlockDesign();
        if (blockDesign <= 0 || !(pluginConfig.hasShopItem(shopType, (int) blockDesign))) {
            return;
        }
        String typeName = pluginConfig.getTypeName(shopType, blockDesign);
        ReflectionUtil reflectionUtil = plugin.getReflectionUtil();
        if (typeName.equalsIgnoreCase("PLAYER_HEAD")) {
            sendSkull(player, Items.textureProfile(pluginConfig.getSkullTexture(shopType, blockDesign)));
            return;
        }
        if (typeName.equalsIgnoreCase("REAL_PLAYER_HEAD")) {
            sendSkull(player, reflectionUtil.getRealPlayerProfile(player));
            return;
        }
        BlockData blockData = pluginConfig.getBlockType(shopType, blockDesign).createBlockData();
        for (Location location : plugin.getCookieClickerManager().getClickerLocations()) {
            player.sendBlockChange(location, blockData);
        }
    }

    private void sendSkull(Player player, PlayerProfile playerProfile) {
        BlockData blockData = Material.PLAYER_HEAD.createBlockData();
        ReflectionUtil reflectionUtil = plugin.getReflectionUtil();
        for (Location location : plugin.getCookieClickerManager().getClickerLocations()) {
            reflectionUtil.sendSkullBlock(player, location, blockData, playerProfile);
        }
    }

    /** Rend au joueur le vrai bloc de chaque clicker. */
    public void resetClickerBlocks(Player player) {
        ReflectionUtil reflectionUtil = plugin.getReflectionUtil();
        for (Location location : plugin.getCookieClickerManager().getClickerLocations()) {
            Block block = location.getBlock();
            if (block.getType() != Material.PLAYER_HEAD) {
                player.sendBlockChange(location, block.getBlockData());
                continue;
            }
            reflectionUtil.sendSkullBlock(player, location, block.getBlockData(), ((Skull) block.getState()).getOwnerProfile());
        }
    }

    public void sendParticleEffect(Player player, Location location, long particleDesign) {
        if (player == null || location == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        CustomParticleEffectType customParticleEffectType = pluginConfig.getCustomParticleEffectType(ShopType.HIT_PARTICLE_DESIGN, particleDesign);
        CustomParticleEffect customParticleEffect = createNewEffect(particleDesign, customParticleEffectType, player, location);
        if (customParticleEffect == null) {
            return;
        }
        stopSpecificEffect(uuid, customParticleEffectType);
        customParticleEffects.computeIfAbsent(uuid, key -> new HashMap<>()).put(customParticleEffectType, customParticleEffect);
        customParticleEffect.start();
    }

    private void stopSpecificEffect(UUID uuid, CustomParticleEffectType customParticleEffectType) {
        HashMap<CustomParticleEffectType, CustomParticleEffect> effects = customParticleEffects.get(uuid);
        if (effects == null) {
            return;
        }
        CustomParticleEffect customParticleEffect = effects.get(customParticleEffectType);
        if (customParticleEffect != null) {
            customParticleEffect.stop();
        }
    }

    private CustomParticleEffect createNewEffect(long particleDesign, CustomParticleEffectType customParticleEffectType, Player player, Location location) {
        ShopType shopType = ShopType.HIT_PARTICLE_DESIGN;
        boolean coloredDust = pluginConfig.hasColoredDust(shopType, particleDesign);
        Particle particle = pluginConfig.getParticle(shopType, particleDesign);
        Particle secondParticle = pluginConfig.getSecondParticle(shopType, particleDesign);
        Color color = pluginConfig.getColor(shopType, particleDesign);
        return switch (customParticleEffectType) {
            case SPIRAL -> new SpiralEffect(plugin, player, location.clone().add(0.5, -1, 0.5), coloredDust, particle, color);
            case CIRCLE_RINGS -> new CircleRingsEffect(plugin, player, location.clone().add(0.5, 0.25, 0.5), coloredDust, particle, color);
            case EXPLOSION_WAVE -> new ExplosionWaveEffect(plugin, player, location.clone().add(0.5, -1, 0.5), coloredDust, particle, color);
            case STAR_BURST -> new StarBurstEffect(plugin, player, location.clone().add(0.5, -1, 0.5), coloredDust, particle, color);
            case MAGICAL_WHIRLWIND -> new MagicalWhirlwindEffect(plugin, player, location.clone().add(0.5, -0.5, 0.5), coloredDust, particle, secondParticle, color);
        };
    }

    public void stopParticleEffects(UUID uuid) {
        if (uuid == null) {
            return;
        }
        HashMap<CustomParticleEffectType, CustomParticleEffect> effects = customParticleEffects.remove(uuid);
        if (effects == null) {
            return;
        }
        for (CustomParticleEffect customParticleEffect : effects.values()) {
            if (customParticleEffect != null) {
                customParticleEffect.stop();
            }
        }
    }
}
