package com.example.whitelist.dto;

import java.util.List;

public record CommandApprovalSnapshot(
        String expressionHtml,
        String expressionText,
        String description,
        String regexTemplate,
        boolean matchStart,
        boolean matchEnd,
        String expandedRegex,
        List<OptionItem> currentViews,
        OptionItem targetView,
        List<OptionItem> scenes,
        Long version
) {
}
