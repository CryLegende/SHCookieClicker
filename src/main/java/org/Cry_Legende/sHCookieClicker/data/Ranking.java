package org.Cry_Legende.sHCookieClicker.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Classement des cookies, le même que celui des menus /cookietop (ajLeaderboards sur %cookieclicker_cookies%).
 * Recalculé hors du fil du serveur, seulement quand des cookies ont changé ; la place d'un joueur se lit ensuite
 * d'un coup, depuis n'importe quel fil. Un joueur sans cookie n'a pas de place.
 */
public final class Ranking {
    private final ProfileStore store;
    private volatile Map<UUID, Integer> places = Map.of();
    private volatile boolean changed = true;

    public Ranking(ProfileStore store) {
        this.store = store;
    }

    public void markChanged() {
        changed = true;
    }

    /** Place du joueur (1 = premier), 0 s'il n'a pas de cookie. */
    public int place(UUID uuid) {
        return places.getOrDefault(uuid, 0);
    }

    public void refresh() {
        if (!changed) {
            return;
        }
        changed = false;
        record Entry(UUID uuid, String name, long cookies) {
        }
        List<Entry> entries = new ArrayList<>(store.size());
        for (Profile profile : store.all()) {
            long cookies = profile.cookies();
            if (cookies > 0) {
                entries.add(new Entry(profile.uuid(), profile.name(), cookies));
            }
        }
        entries.sort((a, b) -> a.cookies() != b.cookies() ? Long.compare(b.cookies(), a.cookies()) : a.name().compareToIgnoreCase(b.name()));
        Map<UUID, Integer> result = new HashMap<>(entries.size() * 2);
        for (int index = 0; index < entries.size(); index++) {
            result.put(entries.get(index).uuid(), index + 1);
        }
        places = result;
    }
}
