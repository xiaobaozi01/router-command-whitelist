package com.example.whitelist.dto;

import java.util.List;

public record AiGenerateRegexResponse(
        String regexTemplate,
        List<String> positiveCases,
        List<String> negativeCases,
        String explanation,
        List<String> warnings,
        RegexPreviewResponse preview
) {
}
