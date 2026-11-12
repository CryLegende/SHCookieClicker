package org.Cry_Legende.sHCookieClicker.data;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Les progressions de tous les joueurs, lues une fois au démarrage : ensuite la mémoire fait foi et la base ne
 * sert qu'à écrire. Un joueur n'a de profil qu'à partir de sa première action au clicker (ou d'une commande
 * d'administration) ; avant, les placeholders rendent les valeurs d'un joueur neuf.
 */
public final class ProfileStore {
    private final Map<UUID, Profile> byUuid = new ConcurrentHashMap<>();
    private final Map<String, Profile> byName = new ConcurrentHashMap<>();
    private final Consumer<Profile> dirtySink;

    public ProfileStore(Consumer<Profile> dirtySink) {
        this.dirtySink = dirtySink;
    }

    /** Profil vide, pas encore rangé (lecture en base). */
    Profile blank(UUID uuid, String name) {
        return new Profile(uuid, name, dirtySink);
    }

    void put(Profile profile) {
        byUuid.put(profile.uuid(), profile);
        byName.put(key(profile.name()), profile);
    }

    public Profile get(UUID uuid) {
        return byUuid.get(uuid);
    }

    /** Profil relu en base (mode réseau) : remplace les valeurs de celui en mémoire, ou y entre. */
    public Profile adopt(Profile stored) {
        Profile existing = byUuid.putIfAbsent(stored.uuid(), stored);
        if (existing == null) {
            byName.put(key(stored.name()), stored);
            return stored;
        }
        existing.replaceWith(stored);
        return existing;
    }

    /** Profil du joueur, créé à sa première action. */
    public Profile getOrCreate(UUID uuid, String name) {
        Profile profile = byUuid.get(uuid);
        if (profile != null) {
            return profile;
        }
        Profile created = byUuid.computeIfAbsent(uuid, id -> new Profile(id, name, dirtySink));
        byName.putIfAbsent(key(name), created);
        return created;
    }

    /** Joueur connu d'après son pseudo, sans tenir compte de la casse ; null s'il est inconnu. */
    public Profile find(String name) {
        return byName.get(key(name));
    }

    /** Pseudo du joueur à sa connexion : un changement de pseudo est enregistré. */
    public void rename(Profile profile, String name) {
        String old = profile.name();
        if (profile.rename(name)) {
            byName.remove(key(old), profile);
            byName.put(key(name), profile);
        }
    }

    public Collection<Profile> all() {
        return byUuid.values();
    }

    public int size() {
        return byUuid.size();
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
