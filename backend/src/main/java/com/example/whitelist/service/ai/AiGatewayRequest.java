package com.example.whitelist.service.ai;

import java.util.Map;

public record AiGatewayRequest(
        String operation,
        String systemPrompt,
        String userPrompt,
        String schemaName,
        Map<String, Object> schema,
        Map<String, Object> payload
) {
}
