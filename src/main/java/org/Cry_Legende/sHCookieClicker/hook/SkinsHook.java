package org.Cry_Legende.sHCookieClicker.hook;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.Cry_Legende.sHSkins.api.SHSkinsAPI;

import java.util.UUID;

/** Pont vers SHSkins : cette classe n'est chargée que si SHSkins est activé ({@link Heads} vérifie avant). */
final class SkinsHook {

    private SkinsHook() {
    }

    static PlayerProfile profile(UUID uuid, String name) {
        return SHSkinsAPI.createProfile(uuid, name);
    }
}
