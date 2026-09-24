package com.example.whitelist.util;

import org.springframework.web.util.HtmlUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RichTextUtils {
    private static final Pattern HTML_TAG = Pattern.compile("(?is)<[^>]*>");
    private static final Pattern ALLOWED_FORMAT_TAG = Pattern.compile(
            "(?is)^<\\s*(/?)\\s*(p|strong|b|em|i|s|strike)\\b[^>]*>$");
    private static final Pattern STRIKETHROUGH_CONTENT = Pattern.compile("(?is)<s>(.*?)</s>");

    private RichTextUtils() {
    }

    public static String toPlainText(String html) {
        if (html == null) {
            return "";
        }
        String withBreaks = html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</div>", "\n")
                .replaceAll("(?i)</p>", "\n");
        String withoutTags = withBreaks.replaceAll("<[^>]+>", "");
        return HtmlUtils.htmlUnescape(withoutTags)
                .replace('\u00a0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .trim();
    }

    public static String sanitizeFormatting(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Matcher matcher = HTML_TAG.matcher(html.replaceAll("(?is)<!--.*?-->", ""));
        StringBuffer sanitized = new StringBuffer();
        while (matcher.find()) {
            Matcher allowed = ALLOWED_FORMAT_TAG.matcher(matcher.group());
            String replacement = "";
            if (allowed.matches()) {
                String tag = normalizeTag(allowed.group(2));
                replacement = "<" + (allowed.group(1).isEmpty() ? "" : "/") + tag + ">";
            }
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sanitized);
        String result = sanitized.toString().trim();
        if (result.isEmpty()) {
            return "";
        }
        return result.regionMatches(true, 0, "<p>", 0, 3) ? result : "<p>" + result + "</p>";
    }

    public static String toPlainTextWithoutStrikethrough(String html) {
        return toPlainText(withoutStrikethrough(html));
    }

    public static String withoutStrikethrough(String html) {
        String sanitized = sanitizeFormatting(html);
        return STRIKETHROUGH_CONTENT.matcher(sanitized).replaceAll("");
    }

    public static List<String> strikethroughTexts(String html) {
        String sanitized = sanitizeFormatting(html);
        Matcher matcher = STRIKETHROUGH_CONTENT.matcher(sanitized);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            String text = toPlainText(matcher.group(1));
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    private static String normalizeTag(String tag) {
        return switch (tag.toLowerCase(Locale.ROOT)) {
            case "b" -> "strong";
            case "i" -> "em";
            case "strike" -> "s";
            default -> tag.toLowerCase(Locale.ROOT);
        };
    }
}
