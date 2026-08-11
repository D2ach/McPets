package top.morndream.mcPets.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 支持 MiniMessage、&amp; 颜色码、&amp;#RRGGBB / #RRGGBB。
 * 不改写 gradient / hex 标签内部的颜色值，避免 GUI/前缀解析失败。
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP =
            LegacyComponentSerializer.legacyAmpersand();
    private static final Pattern HEX_AMP = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern STANDALONE_HEX = Pattern.compile("#([A-Fa-f0-9]{6})");
    private static final Pattern AMP_CODE = Pattern.compile(
            "&([0-9a-fk-orA-FK-OR])");

    private Text() {
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String normalized = normalize(input);
        if (normalized.indexOf('<') >= 0) {
            try {
                return MINI.deserialize(normalized);
            } catch (Exception ignored) {
                try {
                    // 归一化偶发破坏标签时，回退原始输入再试 MiniMessage
                    return MINI.deserialize(input);
                } catch (Exception ignored2) {
                    // fall through
                }
            }
        }
        return LEGACY_AMP.deserialize(normalized);
    }

    /**
     * 占位符以独立 Component 插入，避免把渐变名字符串直接塞进外层颜色标签
     * （例如 green 包裹 name）导致标签栈错乱、末尾出现字面量闭合标签。
     */
    public static Component parse(String input, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return parse(input);
        }
        final String original = input == null ? "" : input;
        String template = original;
        TagResolver.Builder resolvers = TagResolver.builder();
        boolean any = false;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String key = e.getKey();
            if (key.isEmpty()) {
                continue;
            }
            String token = "{" + key + "}";
            if (!template.contains(token)) {
                continue;
            }
            // 用 Set 重载注册动态标签，避开 Placeholder.component 的 @TagPattern IDE 警告
            String tag = "ph_" + key.toLowerCase(Locale.ROOT).replace('-', '_');
            String value = e.getValue();
            template = template.replace(token, "<" + tag + ">");
            resolvers.resolver(inserting(tag, parse(value != null ? value : "")));
            any = true;
        }
        if (!any) {
            return parse(template);
        }
        TagResolver resolver = resolvers.build();
        String normalized = normalize(template);
        try {
            return MINI.deserialize(normalized, resolver);
        } catch (Exception ignored) {
            try {
                return MINI.deserialize(template, resolver);
            } catch (Exception ignored2) {
                // 最后回退：旧的字符串替换（可能仍有嵌套问题，但总比丢消息好）
                String fallback = original;
                for (Map.Entry<String, String> e : placeholders.entrySet()) {
                    String value = e.getValue();
                    fallback = fallback.replace("{" + e.getKey() + "}", value != null ? value : "");
                }
                return parse(fallback);
            }
        }
    }

    /** 动态标签插入（走 Set 重载，避免自写匿名类触发 @NullMarked 形参警告）。 */
    private static TagResolver inserting(String tagName, Component value) {
        return TagResolver.resolver(Set.of(tagName), (_, _) -> Tag.selfClosingInserting(value));
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
        if (input == null || input.isEmpty()) {
            return "";
        }
        String s = HEX_AMP.matcher(input).replaceAll("<#$1>");
        s = replaceOutsideTags(s, STANDALONE_HEX, m -> "<#" + m.group(1) + ">");
        s = replaceOutsideTags(s, AMP_CODE, Text::ampToMini);
        return s;
    }

    private static String ampToMini(Matcher m) {
        return switch (m.group(1).charAt(0)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a', 'A' -> "<green>";
            case 'b', 'B' -> "<aqua>";
            case 'c', 'C' -> "<red>";
            case 'd', 'D' -> "<light_purple>";
            case 'e', 'E' -> "<yellow>";
            case 'f', 'F' -> "<white>";
            case 'l', 'L' -> "<bold>";
            case 'o', 'O' -> "<italic>";
            case 'n', 'N' -> "<underlined>";
            case 'm', 'M' -> "<strikethrough>";
            case 'k', 'K' -> "<obfuscated>";
            case 'r', 'R' -> "<reset>";
            default -> m.group();
        };
    }

    /**
     * 只在 MiniMessage / HTML 风格标签之外做替换，避免动到 gradient 标签内的 hex。
     */
    private static String replaceOutsideTags(String input, Pattern pattern,
                                             java.util.function.Function<Matcher, String> replacer) {
        StringBuilder out = new StringBuilder(input.length() + 16);
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '<') {
                int end = input.indexOf('>', i + 1);
                if (end < 0) {
                    out.append(input, i, input.length());
                    break;
                }
                out.append(input, i, end + 1);
                i = end + 1;
                continue;
            }
            int nextTag = input.indexOf('<', i);
            int chunkEnd = nextTag < 0 ? input.length() : nextTag;
            String chunk = input.substring(i, chunkEnd);
            Matcher m = pattern.matcher(chunk);
            StringBuilder sb = new StringBuilder(chunk.length());
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(m)));
            }
            m.appendTail(sb);
            out.append(sb);
            i = chunkEnd;
        }
        return out.toString();
    }
}
