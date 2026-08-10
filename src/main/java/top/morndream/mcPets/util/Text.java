package top.morndream.mcPets.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 支持 MiniMessage、& 颜色码、&#RRGGBB / #RRGGBB。
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP =
            LegacyComponentSerializer.legacyAmpersand();
    private static final Pattern HEX_AMP = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_HASH = Pattern.compile("(?<!<)#([A-Fa-f0-9]{6})");

    private Text() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String normalized = normalize(input);
        // 若含 MiniMessage 标签则优先走 MiniMessage；否则 legacy
        if (normalized.indexOf('<') >= 0 && normalized.indexOf('>') > normalized.indexOf('<')) {
            try {
                return MINI.deserialize(normalized);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return LEGACY_AMP.deserialize(normalized);
    }

    public static Component parse(String input, Map<String, String> placeholders) {
        String value = input == null ? "" : input;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                value = value.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return parse(value);
    }

    /** 去掉颜色/格式后的可见纯文本（用于字数统计）。 */
    public static String plain(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(parse(input));
    }

    /** 可见字数（Unicode code point），不含颜色符号与格式标签。 */
    public static int plainLength(String input) {
        String plain = plain(input);
        return plain.codePointCount(0, plain.length());
    }

    public static String normalize(String input) {
        String s = input;
        Matcher m1 = HEX_AMP.matcher(s);
        StringBuilder sb1 = new StringBuilder();
        while (m1.find()) {
            m1.appendReplacement(sb1, "<#" + m1.group(1) + ">");
        }
        m1.appendTail(sb1);
        s = sb1.toString();

        Matcher m2 = HEX_HASH.matcher(s);
        StringBuilder sb2 = new StringBuilder();
        while (m2.find()) {
            m2.appendReplacement(sb2, "<#" + m2.group(1) + ">");
        }
        m2.appendTail(sb2);
        s = sb2.toString();

        // & -> MiniMessage 或保留给 legacy；统一把 &x 转成 § 再让 legacy 处理较麻烦
        // 简单策略：把常见 & 色码转为 MiniMessage
        s = s.replace("&0", "<black>").replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>").replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>")
                .replace("&8", "<dark_gray>").replace("&9", "<blue>")
                .replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>")
                .replace("&e", "<yellow>").replace("&f", "<white>")
                .replace("&l", "<bold>").replace("&o", "<italic>")
                .replace("&n", "<underlined>").replace("&m", "<strikethrough>")
                .replace("&k", "<obfuscated>").replace("&r", "<reset>")
                .replace("&A", "<green>").replace("&B", "<aqua>")
                .replace("&C", "<red>").replace("&D", "<light_purple>")
                .replace("&E", "<yellow>").replace("&F", "<white>")
                .replace("&L", "<bold>").replace("&O", "<italic>")
                .replace("&N", "<underlined>").replace("&M", "<strikethrough>")
                .replace("&K", "<obfuscated>").replace("&R", "<reset>");
        return s;
    }
}
