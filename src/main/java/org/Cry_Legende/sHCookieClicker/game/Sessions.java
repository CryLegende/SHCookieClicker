package org.Cry_Legende.sHCookieClicker.game;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sessions des joueurs connectés. */
public final class Sessions {
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public Session open(Player player) {
        Session session = new Session(player.getUniqueId(), System.currentTimeMillis());
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public Session get(UUID uuid) {
        return sessions.get(uuid);
    }

    public Session get(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public Session close(UUID uuid) {
        return sessions.remove(uuid);
    }

    public Collection<Session> all() {
        return sessions.values();
    }
}
