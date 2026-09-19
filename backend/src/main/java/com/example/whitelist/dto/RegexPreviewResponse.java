package com.example.whitelist.dto;

import java.util.List;

public record RegexPreviewResponse(
        boolean valid,
        String expandedRegex,
        String error,
        List<TestLineResult> results
) {
    public record TestLineResult(int lineNumber, String command, boolean matched) {
    }
}
