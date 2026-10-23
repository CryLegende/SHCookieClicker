package de.zillolp.cookieclicker.clickerevents;

import de.zillolp.cookieclicker.CookieClicker;
import de.zillolp.cookieclicker.enums.ClickerEventType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public class CookieExplosionEvent extends ClickerEvent {
    private final CookieClicker plugin;
    private final ArrayList<Item> items = new ArrayList<>();

    public CookieExplosionEvent(CookieClicker plugin, Player player, ClickerEventType clickerEventType, double time) {
        super(player, clickerEventType, time);
        this.plugin = plugin;
    }

    // Version SH : contour lumineux par l'API (blanc). L'original le colorait en or par un paquet d'équipe NMS
    // envoyé au seul joueur ; une équipe Bukkit serait enregistrée avec le scoreboard du monde à chaque explosion.
    public void addGlowing(Item item) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (item.isValid()) {
                item.setGlowing(true);
            }
        }, 20);
    }

    public ArrayList<Item> getItems() {
        return items;
    }
}
