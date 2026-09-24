package com.example.whitelist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        String protocol,
        String baseUrl,
        String model,
        String apiKey,
        String authMode,
        String structuredOutputMode,
        String thinkingMode,
        int timeoutSeconds,
        int maxOutputTokens,
        String openaiPath,
        String anthropicPath,
        String anthropicVersion
) {
    public AiProperties {
        protocol = defaultValue(protocol, "mock").toLowerCase();
        baseUrl = trim(baseUrl);
        model = trim(model);
        apiKey = trim(apiKey);
        authMode = defaultValue(authMode, "auto").toLowerCase();
        structuredOutputMode = defaultValue(structuredOutputMode, "prompt-only").toLowerCase();
        thinkingMode = defaultValue(thinkingMode, "auto").toLowerCase();
        timeoutSeconds = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
        maxOutputTokens = maxOutputTokens <= 0 ? 2000 : maxOutputTokens;
        openaiPath = normalizePath(defaultValue(openaiPath, "/v1/chat/completions"));
        anthropicPath = normalizePath(defaultValue(anthropicPath, "/v1/messages"));
        anthropicVersion = defaultValue(anthropicVersion, "2023-06-01");
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizePath(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }
}
