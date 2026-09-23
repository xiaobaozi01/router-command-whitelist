package com.example.whitelist.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CommandResponse(
        Long id,
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
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
