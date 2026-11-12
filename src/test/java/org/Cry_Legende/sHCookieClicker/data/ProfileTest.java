package org.Cry_Legende.sHCookieClicker.data;

import org.Cry_Legende.sHCookieClicker.config.Upgrade;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileTest {
    private final List<Profile> dirty = new ArrayList<>();
    private final ProfileStore store = new ProfileStore(dirty::add);
    // Amélioration 1 de la boutique : 30 + n × 230, +2 par clic
    private final Upgrade hoe = new Upgrade(1, null, 30, 230, 2);

    @Test
    void pricesFollowTheShopRule() {
        assertEquals(30, hoe.priceAt(0));
        assertEquals(260, hoe.priceAt(1));
        assertEquals(490, hoe.priceAt(2));
        // dix achats depuis 0 : 10 × 30 + 230 × (0 + ... + 9)
        assertEquals(10 * 30 + 230 * 45, hoe.priceOf(0, 10));
    }

    @Test
    void buyOneThenTen() {
        Profile profile = store.getOrCreate(UUID.randomUUID(), "Test");
        profile.modify(Profile.Field.COOKIES, Profile.Operation.SET, 100_000_000);
        assertEquals(1, profile.buyUpgrade(ShopKind.DEFAULT, hoe, 1));
        assertEquals(3, profile.perClick());
        assertEquals(99_999_970, profile.cookies());
        assertEquals(10, profile.buyUpgrade(ShopKind.DEFAULT, hoe, 10));
        assertEquals(11, profile.purchases(ShopKind.DEFAULT, 1));
        assertEquals(1 + 11 * 2, profile.perClick());
        assertEquals(100_000_000 - hoe.priceOf(0, 11), profile.cookies());
    }

    @Test
    void multiBuyStopsWhenCookiesRunOut() {
        Profile profile = store.getOrCreate(UUID.randomUUID(), "Pauvre");
        profile.modify(Profile.Field.COOKIES, Profile.Operation.SET, 30 + 260 + 100);
        assertEquals(2, profile.buyUpgrade(ShopKind.DEFAULT, hoe, 10));
        assertEquals(100, profile.cookies());
        assertEquals(0, profile.buyUpgrade(ShopKind.DEFAULT, hoe, 10));
        assertEquals(100, profile.cookies());
    }

    @Test
    void designsAreBoughtOnce() {
        Profile profile = store.getOrCreate(UUID.randomUUID(), "Design");
        profile.modify(Profile.Field.COOKIES, Profile.Operation.SET, 250_000);
        assertTrue(profile.buyDesign(ShopKind.BLOCK_DESIGN, 1, 100_000));
        assertEquals(1, profile.selected(ShopKind.BLOCK_DESIGN));
        assertFalse(profile.buyDesign(ShopKind.BLOCK_DESIGN, 1, 100_000));
        assertEquals(150_000, profile.cookies());
        assertFalse(profile.buyDesign(ShopKind.BLOCK_DESIGN, 2, 200_000));
        profile.select(ShopKind.BLOCK_DESIGN, 0);
        assertEquals(0, profile.selected(ShopKind.BLOCK_DESIGN));
        assertTrue(profile.owns(ShopKind.BLOCK_DESIGN, 1));
    }

    @Test
    void clicksAndModify() {
        Profile profile = store.getOrCreate(UUID.randomUUID(), "Clic");
        assertEquals(1, profile.click(1));
        assertEquals(2, profile.click(2));
        assertEquals(3, profile.cookies());
        assertEquals(2, profile.clickerClicks());
        assertEquals(0, profile.modify(Profile.Field.COOKIES, Profile.Operation.REMOVE, 50));
        assertEquals(10, profile.collect(10));
    }

    @Test
    void dirtyOnlyOnceUntilSaved() {
        Profile profile = store.getOrCreate(UUID.randomUUID(), "Sauve");
        profile.click(1);
        profile.click(1);
        assertEquals(1, dirty.size());
        Profile.Row row = profile.takeForSave();
        assertNull(row.purchases(), "achats inchangés : pas réécrits");
        assertFalse(profile.isDirty());
        profile.buyDesign(ShopKind.MENU_DESIGN, 3, 0);
        assertEquals(2, dirty.size());
        assertEquals(1L, profile.takeForSave().purchases().get(ShopKind.MENU_DESIGN).get(3));
    }
}
