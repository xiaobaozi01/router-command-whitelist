package com.example.whitelist.service.ai;

import com.example.whitelist.common.BusinessException;
import com.example.whitelist.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AnthropicCompatibleClient implements AiGatewayClient {
    private final AiProperties properties;
    private final HttpAiGatewaySupport http;

    public AnthropicCompatibleClient(AiProperties properties, HttpAiGatewaySupport http) {
        this.properties = properties;
        this.http = http;
    }

    @Override
    public String protocol() {
        return "anthropic";
    }

    @Override
    public String generate(AiGatewayRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("max_tokens", properties.maxOutputTokens());
        body.put("system", request.systemPrompt());
        body.put("messages", List.of(Map.of("role", "user", "content", request.userPrompt())));
        addThinking(body);
        addOutputFormat(body, request);

        JsonNode response = http.post(properties.anthropicPath(), body, headers -> {
            headers.set("anthropic-version", properties.anthropicVersion());
            http.applyAuthentication(headers, "x-api-key");
        });
        JsonNode content = response == null ? null : response.path("content");
        if (content == null || !content.isArray()) {
            throw new BusinessException(502, "AI 服务未返回消息内容");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : content) {
            if ("text".equals(item.path("type").asText()) && item.path("text").isTextual()) {
                text.append(item.path("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new BusinessException(502, "AI 服务返回了空内容");
        }
        return text.toString();
    }

    private void addThinking(Map<String, Object> body) {
        if (!"auto".equals(properties.thinkingMode())) {
            body.put("thinking", Map.of("type", properties.thinkingMode()));
        }
    }

    private void addOutputFormat(Map<String, Object> body, AiGatewayRequest request) {
        switch (properties.structuredOutputMode()) {
            case "json-schema" -> body.put("output_config", Map.of(
                    "format", Map.of("type", "json_schema", "schema", request.schema())
            ));
            case "auto", "json-object", "prompt-only" -> {
                // Anthropic-compatible 内部服务不一定实现 output_config，依靠提示词和本地校验。
            }
            default -> throw new BusinessException(503, "AI 结构化输出模式配置无效");
        }
    }
}
