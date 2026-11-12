package de.zillolp.cookieclicker.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Textes des fichiers de configuration : codes &amp; et #rrggbb, et composants sans italique pour les objets. */
public final class Text {
    private static final Pattern HEX = Pattern.compile("#[a-fA-F0-9]{6}");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Text() {
    }

    /** Texte avec les codes de couleur appliqués (§). */
    public static String color(String message) {
        Matcher matcher = HEX.matcher(message);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            StringBuilder hex = new StringBuilder("&x");
            for (char digit : matcher.group().substring(1).toCharArray()) {
                hex.append('&').append(digit);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(hex.toString()));
        }
        matcher.appendTail(result);
        return ChatColor.translateAlternateColorCodes('&', result.toString());
    }

    /** Composant d'un nom ou d'une ligne de description : l'italique par défaut des objets est retiré. */
    public static Component item(String message) {
        return LEGACY.deserialize(color(message)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> items(List<String> lines) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(item(line));
        }
        return components;
    }

    /** Composant d'un titre de menu ou d'un hologramme. */
    public static Component component(String message) {
        return LEGACY.deserialize(color(message));
    }
}
