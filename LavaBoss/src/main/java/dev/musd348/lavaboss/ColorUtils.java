package dev.musd348.lavaboss;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static Component format(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<color:#" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        String converted = sb.toString();

        converted = converted
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&l", "<bold>")
                .replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>")
                .replace("&o", "<italic>")
                .replace("&r", "<reset>");

        try {
            return MINI_MESSAGE.deserialize(converted);
        } catch (Exception e) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        }
    }

    public static Component colorize(String text) {
        return format(text);
    }

    public static Component formatFor(Player player, String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        if (BedrockUtils.isBedrockPlayer(player)) {
            String cleaned = text
                    .replaceAll("<gradient[^>]*>", "")
                    .replaceAll("</gradient>", "")
                    .replaceAll("<rainbow[^>]*>", "")
                    .replaceAll("</rainbow>", "");
            return format(cleaned);
        }

        return format(text);
    }
}