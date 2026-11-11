package de.zillolp.cookieclicker.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.Cry_Legende.sHSkins.api.SHSkinsAPI;

import java.util.UUID;

/** Pont vers SHSkins. Cette classe n'est chargée que si SHSkins est installé : seule {@link PlayerHeads} l'appelle. */
final class SHSkinsHook {

    private SHSkinsHook() {
    }

    static PlayerProfile profile(UUID uuid, String name) {
        return SHSkinsAPI.createProfile(uuid, name);
    }
}
