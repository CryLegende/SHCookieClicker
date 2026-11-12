package org.Cry_Legende.sHCookieClicker.config;

import org.Cry_Legende.sHCookieClicker.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

/**
 * messages.yml. Le fichier du jar sert de valeurs par défaut : une clé absente du fichier du serveur prend le
 * texte du jar ; un texte vidé exprès n'est pas envoyé.
 */
public final class Messages {
    private final YamlConfiguration file;

    public Messages(YamlConfiguration file) {
        this.file = file;
    }

    /** Texte brut (codes & non appliqués), jetons remplacés. */
    public String raw(String key, String... pairs) {
        String text = file.getString(key);
        return text == null ? "" : Text.fill(text, pairs);
    }

    public List<String> lines(String key, String... pairs) {
        return Text.fill(file.getStringList(key), pairs);
    }

    public void send(CommandSender sender, String key, String... pairs) {
        String text = raw(key, pairs);
        if (!text.isEmpty()) {
            sender.sendMessage(Text.of(text));
        }
    }

    public void sendLines(CommandSender sender, String key, String... pairs) {
        for (String line : lines(key, pairs)) {
            if (!line.isEmpty()) {
                sender.sendMessage(Text.of(line));
            }
        }
    }
}
