package de.zillolp.cookieclicker.manager;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.holograms.PaperHologram;
import de.zillolp.cookieclicker.interfaces.Hologram;
import de.zillolp.cookieclicker.interfaces.ItemBuilder;
import de.zillolp.cookieclicker.interfaces.PacketReader;
import de.zillolp.cookieclicker.listener.PaperClickReader;
import de.zillolp.cookieclicker.utils.PaperItemBuilder;

/**
 * Version SH : une seule implémentation, sur l'API Paper (26.3 et suivantes). L'original chargeait un module NMS
 * par version (spigot_1_20_R1 à spigot_1_21_R8) et refusait toute autre version du serveur.
 * getVersionNumber/getSubVersion renvoient 1.21.11, la plus récente que le code connaît, pour que les branches
 * « version récente » restent prises.
 */
public class VersionManager {
    private static final int VERSION_NUMBER = 21;
    private static final int SUB_VERSION = 11;
    private final CookieClicker plugin;
    private PacketReader packetReader;

    public VersionManager(CookieClicker plugin) {
        this.plugin = plugin;
    }

    public boolean checkVersion() {
        return true;
    }

    public PacketReader getPacketReader() {
        if (packetReader == null) {
            packetReader = new PaperClickReader(plugin);
        }
        return packetReader;
    }

    public ItemBuilder getItemBuilder() {
        return new PaperItemBuilder(plugin);
    }

    public Hologram getHologram(String line) {
        return new PaperHologram(plugin, line);
    }

    public String[] getWrongVersionMessage() {
        return new String[0];
    }

    public int getVersionNumber() {
        return VERSION_NUMBER;
    }

    public int getSubVersion() {
        return SUB_VERSION;
    }
}
