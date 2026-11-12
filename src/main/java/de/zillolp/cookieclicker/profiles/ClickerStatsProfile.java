package de.zillolp.cookieclicker.profiles;

import de.zillolp.cookieclicker.enums.ShopType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Progression d'un joueur : cookies, cookies par clic, designs choisis et achats de chaque boutique. */
public class ClickerStatsProfile {
    private final UUID uuid;
    private final Map<ShopType, Map<Integer, Long>> purchases = new ConcurrentHashMap<>();
    private final AtomicLong cookies = new AtomicLong(0L);
    private final AtomicLong perClick = new AtomicLong(1L);
    private final AtomicLong clickerClicks = new AtomicLong(0L);
    private final AtomicLong blockDesign = new AtomicLong(0L);
    private final AtomicLong particleDesign = new AtomicLong(0L);
    private final AtomicLong menuDesign = new AtomicLong(0L);
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private volatile String name;

    public ClickerStatsProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        markDirty();
    }

    public long getCookies() {
        return cookies.get();
    }

    public void setCookies(long cookies) {
        this.cookies.set(cookies);
        markDirty();
    }

    public void addCookies(long cookies) {
        this.cookies.accumulateAndGet(cookies, ClickerStatsProfile::saturatedAdd);
        markDirty();
    }

    public void removeCookies(long cookies) {
        addCookies(-cookies);
    }

    public long getPerClick() {
        return perClick.get();
    }

    public void setPerClick(long perClick) {
        this.perClick.set(perClick);
        markDirty();
    }

    public void addPerClick(long perClick) {
        this.perClick.accumulateAndGet(perClick, ClickerStatsProfile::saturatedAdd);
        markDirty();
    }

    public long getClickerClicks() {
        return clickerClicks.get();
    }

    public void setClickerClicks(long clickerClicks) {
        this.clickerClicks.set(clickerClicks);
        markDirty();
    }

    public void addClickerClicks(long clickerClicks) {
        this.clickerClicks.accumulateAndGet(clickerClicks, ClickerStatsProfile::saturatedAdd);
        markDirty();
    }

    private static long saturatedAdd(long current, long added) {
        if (added > 0 && current > Long.MAX_VALUE - added) {
            return Long.MAX_VALUE;
        }
        if (added < 0 && current < Long.MIN_VALUE - added) {
            return Long.MIN_VALUE;
        }
        return current + added;
    }

    public long getBlockDesign() {
        return blockDesign.get();
    }

    public void setBlockDesign(long blockDesign) {
        this.blockDesign.set(blockDesign);
        markDirty();
    }

    public long getParticleDesign() {
        return particleDesign.get();
    }

    public void setParticleDesign(long particleDesign) {
        this.particleDesign.set(particleDesign);
        markDirty();
    }

    public long getMenuDesign() {
        return menuDesign.get();
    }

    public void setMenuDesign(long menuDesign) {
        this.menuDesign.set(menuDesign);
        markDirty();
    }

    /** Design sélectionné d'une boutique de design, 0 si aucun. */
    public long getSelectedDesign(ShopType shopType) {
        return switch (shopType) {
            case BLOCK_DESIGN -> blockDesign.get();
            case HIT_PARTICLE_DESIGN -> particleDesign.get();
            case MENU_DESIGN -> menuDesign.get();
            default -> 0L;
        };
    }

    public void setSelectedDesign(ShopType shopType, long id) {
        switch (shopType) {
            case BLOCK_DESIGN -> setBlockDesign(id);
            case HIT_PARTICLE_DESIGN -> setParticleDesign(id);
            case MENU_DESIGN -> setMenuDesign(id);
            default -> {
            }
        }
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void markDirty() {
        dirty.set(true);
    }

    public void markClean() {
        dirty.set(false);
    }

    /** Nombre d'achats d'un article : le nombre de fois qu'une amélioration a été prise, 1 pour un design acheté. */
    public long getPurchases(ShopType shopType, int id) {
        return purchases.getOrDefault(shopType, Map.of()).getOrDefault(id, 0L);
    }

    public void setPurchases(ShopType shopType, int id, long amount) {
        purchases.computeIfAbsent(shopType, key -> new ConcurrentHashMap<>()).put(id, amount);
        markDirty();
    }

    public void addPurchase(ShopType shopType, int id) {
        purchases.computeIfAbsent(shopType, key -> new ConcurrentHashMap<>()).merge(id, 1L, Long::sum);
        markDirty();
    }

    public boolean owns(ShopType shopType, int id) {
        return getPurchases(shopType, id) > 0;
    }

    /** Copie des achats, pour la sauvegarde. */
    public Map<ShopType, Map<Integer, Long>> getPurchaseSnapshot() {
        Map<ShopType, Map<Integer, Long>> copy = new HashMap<>();
        purchases.forEach((shopType, items) -> copy.put(shopType, new HashMap<>(items)));
        return copy;
    }

    public void clearPurchases() {
        purchases.clear();
        markDirty();
    }
}
