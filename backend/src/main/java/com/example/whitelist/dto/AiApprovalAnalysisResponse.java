package com.example.whitelist.dto;

import java.util.List;

public record AiApprovalAnalysisResponse(
        String riskLevel,
        String recommendation,
        String summary,
        String recommendationReason,
        List<String> riskPoints,
        List<String> checklist,
        List<String> warnings
) {
}
