package com.example.whitelist.dto;

import java.util.List;

public record CommandAuditSnapshot(
        String expressionHtml,
        String expressionText,
        String regexTemplate,
        boolean matchStart,
        boolean matchEnd,
        String expandedRegex,
        List<OptionItem> currentViews,
        OptionItem targetView,
        List<OptionItem> scenes
) {
}
