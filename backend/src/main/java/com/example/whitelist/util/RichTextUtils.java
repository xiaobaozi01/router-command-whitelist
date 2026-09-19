package com.example.whitelist.util;

import org.springframework.web.util.HtmlUtils;

public final class RichTextUtils {
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
}
