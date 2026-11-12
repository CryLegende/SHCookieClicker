package org.Cry_Legende.sHCookieClicker.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/** Textes des fichiers de configuration : codes & et &#RRGGBB, jetons {nom} remplacés par des valeurs. */
public final class Text {
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().build();

    private Text() {
    }

    public static Component of(String text) {
        return AMPERSAND.deserialize(text);
    }

    /** Nom ou ligne de description d'un objet : sans l'italique que le jeu met par défaut. */
    public static Component item(String text) {
        return of(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> items(List<String> lines) {
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(item(line));
        }
        return components;
    }

    /** Texte sans aucune couleur. */
    public static String plain(String text) {
        return PlainTextComponentSerializer.plainText().serialize(of(text));
    }

    /** Remplace les jetons : fill("{a} et {b}", "a", "1", "b", "2"). */
    public static String fill(String text, String... pairs) {
        if (text == null || text.indexOf('{') < 0) {
            return text;
        }
        String result = text;
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            result = result.replace("{" + pairs[index] + "}", pairs[index + 1]);
        }
        return result;
    }

    public static List<String> fill(List<String> lines, String... pairs) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(fill(line, pairs));
        }
        return result;
    }
}
