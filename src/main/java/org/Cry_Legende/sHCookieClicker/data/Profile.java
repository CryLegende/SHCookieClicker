package org.Cry_Legende.sHCookieClicker.data;

import org.Cry_Legende.sHCookieClicker.config.Upgrade;
import org.Cry_Legende.sHCookieClicker.util.Numbers;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Progression d'un joueur, en mémoire. Les clics arrivent du fil du monde du clicker, les menus de celui du
 * joueur, les placeholders de n'importe quel fil et la sauvegarde de son propre fil : chaque lecture et chaque
 * écriture passe par le verrou du profil (jamais disputé en pratique, donc presque gratuit).
 * Un profil modifié se signale une seule fois à la sauvegarde, jusqu'à ce qu'elle l'ait écrit.
 */
public final class Profile {
    private final UUID uuid;
    private final Consumer<Profile> dirtySink;
    private String name;
    private long cookies;
    private long perClick = 1;
    private long clickerClicks;
    private long blockDesign;
    private long particleDesign;
    private long menuDesign;
    private final EnumMap<ShopKind, Map<Integer, Long>> purchases = new EnumMap<>(ShopKind.class);
    private boolean dirty;
    private boolean purchasesDirty;

    Profile(UUID uuid, String name, Consumer<Profile> dirtySink) {
        this.uuid = uuid;
        this.name = name;
        this.dirtySink = dirtySink;
    }

    public UUID uuid() {
        return uuid;
    }

    public synchronized String name() {
        return name;
    }

    public synchronized long cookies() {
        return cookies;
    }

    public synchronized long perClick() {
        return perClick;
    }

    public synchronized long clickerClicks() {
        return clickerClicks;
    }

    public synchronized long selected(ShopKind kind) {
        return switch (kind) {
            case BLOCK_DESIGN -> blockDesign;
            case HIT_PARTICLE_DESIGN -> particleDesign;
            case MENU_DESIGN -> menuDesign;
            default -> 0;
        };
    }

    public synchronized long purchases(ShopKind kind, int id) {
        Map<Integer, Long> items = purchases.get(kind);
        return items == null ? 0 : items.getOrDefault(id, 0L);
    }

    public synchronized boolean owns(ShopKind kind, int id) {
        return purchases(kind, id) > 0;
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    // ---- jeu

    /** Un clic qui compte : les cookies par clic, multipliés pendant le cookie doré. Rend le gain. */
    public synchronized long click(long multiplier) {
        long gain = Numbers.multiply(perClick, multiplier);
        cookies = Numbers.add(cookies, gain);
        clickerClicks = Numbers.add(clickerClicks, 1);
        markDirty(false);
        return gain;
    }

    /** Un cookie d'explosion ramassé : les cookies par clic multipliés. Rend le gain. */
    public synchronized long collect(long multiplier) {
        long gain = Numbers.multiply(perClick, multiplier);
        cookies = Numbers.add(cookies, gain);
        markDirty(false);
        return gain;
    }

    /**
     * Achète jusqu'à {@code wanted} fois une amélioration, tant que les cookies suffisent.
     *
     * @return le nombre d'achats faits (0 : pas assez de cookies pour un seul)
     */
    public synchronized int buyUpgrade(ShopKind kind, Upgrade upgrade, int wanted) {
        long done = purchases(kind, upgrade.id());
        int bought = 0;
        long total = 0;
        while (bought < wanted) {
            long price = upgrade.priceAt(done + bought);
            if (Numbers.add(total, price) > cookies) {
                break;
            }
            total = Numbers.add(total, price);
            bought++;
        }
        if (bought == 0) {
            return 0;
        }
        cookies -= total;
        perClick = Numbers.add(perClick, Numbers.multiply(upgrade.gain(), bought));
        purchases.computeIfAbsent(kind, key -> new HashMap<>()).put(upgrade.id(), done + bought);
        markDirty(true);
        return bought;
    }

    /** Achète un design et le sélectionne. Faux s'il est déjà acheté ou trop cher. */
    public synchronized boolean buyDesign(ShopKind kind, int id, long price) {
        if (owns(kind, id) || cookies < price) {
            return false;
        }
        cookies -= price;
        purchases.computeIfAbsent(kind, key -> new HashMap<>()).put(id, 1L);
        setSelected(kind, id);
        markDirty(true);
        return true;
    }

    /** Sélectionne un design acheté, ou retire la sélection (0). */
    public synchronized void select(ShopKind kind, long id) {
        if (selected(kind) == id) {
            return;
        }
        setSelected(kind, id);
        markDirty(false);
    }

    private void setSelected(ShopKind kind, long id) {
        switch (kind) {
            case BLOCK_DESIGN -> blockDesign = id;
            case HIT_PARTICLE_DESIGN -> particleDesign = id;
            case MENU_DESIGN -> menuDesign = id;
            default -> {
            }
        }
    }

    // ---- administration

    public enum Field { COOKIES, PER_CLICK, CLICKER_CLICKS }

    public enum Operation { ADD, SET, REMOVE }

    /** /shcookieclicker modify : rend la nouvelle valeur, jamais sous 0. */
    public synchronized long modify(Field field, Operation operation, long amount) {
        long current = switch (field) {
            case COOKIES -> cookies;
            case PER_CLICK -> perClick;
            case CLICKER_CLICKS -> clickerClicks;
        };
        long value = switch (operation) {
            case ADD -> Numbers.add(current, amount);
            case SET -> amount;
            case REMOVE -> Numbers.add(current, -amount);
        };
        value = Math.max(0, value);
        switch (field) {
            case COOKIES -> cookies = value;
            case PER_CLICK -> perClick = value;
            case CLICKER_CLICKS -> clickerClicks = value;
        }
        markDirty(false);
        return value;
    }

    /** /shcookieclicker reset : progression d'un joueur neuf. */
    public synchronized void reset() {
        cookies = 0;
        perClick = 1;
        clickerClicks = 0;
        blockDesign = 0;
        particleDesign = 0;
        menuDesign = 0;
        purchases.clear();
        markDirty(true);
    }

    synchronized boolean rename(String newName) {
        if (newName.equals(name)) {
            return false;
        }
        name = newName;
        markDirty(false);
        return true;
    }

    private void markDirty(boolean shop) {
        purchasesDirty |= shop;
        if (!dirty) {
            dirty = true;
            dirtySink.accept(this);
        }
    }

    // ---- base de données

    /** Valeurs lues en base, sans rien marquer à écrire. */
    synchronized void load(String name, long cookies, long perClick, long clickerClicks, long blockDesign, long particleDesign, long menuDesign) {
        this.name = name;
        this.cookies = cookies;
        this.perClick = perClick;
        this.clickerClicks = clickerClicks;
        this.blockDesign = blockDesign;
        this.particleDesign = particleDesign;
        this.menuDesign = menuDesign;
    }

    synchronized void loadPurchase(ShopKind kind, int id, long amount) {
        purchases.computeIfAbsent(kind, key -> new HashMap<>()).put(id, amount);
    }

    /** Mode réseau : remplace tout par la version relue en base (l'autre serveur a pu la changer). */
    synchronized void replaceWith(Profile stored) {
        Row row = stored.row();
        load(name, row.cookies(), row.perClick(), row.clickerClicks(), row.blockDesign(), row.particleDesign(), row.menuDesign());
        purchases.clear();
        row.purchases().forEach((kind, items) -> purchases.put(kind, new HashMap<>(items)));
        dirty = false;
        purchasesDirty = false;
    }

    /** Ligne à écrire. Les achats n'y sont que s'ils ont changé depuis la dernière écriture. */
    public record Row(UUID uuid, String name, long cookies, long perClick, long clickerClicks, long blockDesign,
                      long particleDesign, long menuDesign, Map<ShopKind, Map<Integer, Long>> purchases) {
    }

    /** Copie complète, achats compris. */
    synchronized Row row() {
        return new Row(uuid, name, cookies, perClick, clickerClicks, blockDesign, particleDesign, menuDesign, copyPurchases());
    }

    /** Prend la ligne à écrire et considère le profil comme écrit ; {@link #failed} le remet à écrire en cas d'échec. */
    synchronized Row takeForSave() {
        Row row = new Row(uuid, name, cookies, perClick, clickerClicks, blockDesign, particleDesign, menuDesign,
                purchasesDirty ? copyPurchases() : null);
        dirty = false;
        purchasesDirty = false;
        return row;
    }

    synchronized void failed(boolean withPurchases) {
        markDirty(withPurchases);
    }

    private Map<ShopKind, Map<Integer, Long>> copyPurchases() {
        Map<ShopKind, Map<Integer, Long>> copy = new EnumMap<>(ShopKind.class);
        purchases.forEach((kind, items) -> copy.put(kind, new HashMap<>(items)));
        return copy;
    }
}
