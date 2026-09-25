package com.example.whitelist.dto;

import java.util.List;

public record CommandConflictCheckResponse(
        int candidateCount,
        boolean aiUsed,
        List<String> warnings,
        List<Item> results
) {
    public record Item(
            String sourceType,
            Long sourceId,
            Long commandId,
            String expressionText,
            String description,
            String regexTemplate,
            String expandedRegex,
            List<OptionItem> currentViews,
            OptionItem targetView,
            String relation,
            String riskType,
            String riskLevel,
            String confidence,
            List<String> evidence,
            String message
    ) {
    }
}
