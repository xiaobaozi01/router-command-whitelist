package com.example.whitelist.service.ai;

import com.example.whitelist.common.BusinessException;
import com.example.whitelist.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleClient implements AiGatewayClient {
    private final AiProperties properties;
    private final HttpAiGatewaySupport http;

    public OpenAiCompatibleClient(AiProperties properties, HttpAiGatewaySupport http) {
        this.properties = properties;
        this.http = http;
    }

    @Override
    public String protocol() {
        return "openai";
    }

    @Override
    public String generate(AiGatewayRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())
        ));
        body.put("max_tokens", properties.maxOutputTokens());
        addThinking(body);
        addResponseFormat(body, request);

        JsonNode response = http.post(properties.openaiPath(), body,
                headers -> http.applyAuthentication(headers, "bearer"));
        JsonNode message = response == null ? null : response.path("choices").path(0).path("message");
        if (message == null || message.isMissingNode()) {
            throw new BusinessException(502, "AI 服务未返回消息内容");
        }
        JsonNode refusal = message.path("refusal");
        if (refusal.isTextual() && !refusal.asText().isBlank()) {
            throw new BusinessException(422, "AI 拒绝了本次请求：" + refusal.asText());
        }
        String content = textContent(message.path("content"));
        if (content.isBlank()) {
            String finishReason = response.path("choices").path(0).path("finish_reason").asText();
            String detail = finishReason.isBlank() ? "" : "（finish_reason=" + finishReason + "）";
            throw new BusinessException(502, "AI 服务返回了空内容" + detail);
        }
        return content;
    }

    private void addThinking(Map<String, Object> body) {
        if (!"auto".equals(properties.thinkingMode())) {
            body.put("thinking", Map.of("type", properties.thinkingMode()));
        }
    }

    private void addResponseFormat(Map<String, Object> body, AiGatewayRequest request) {
        switch (properties.structuredOutputMode()) {
            case "json-schema" -> body.put("response_format", Map.of(
                    "type", "json_schema",
                    "json_schema", Map.of(
                            "name", request.schemaName(),
                            "strict", true,
                            "schema", request.schema()
                    )
            ));
            case "json-object" -> body.put("response_format", Map.of("type", "json_object"));
            case "auto" -> body.put("response_format", Map.of("type", "json_object"));
            case "prompt-only" -> {
                // 兼容仅实现基础 Chat Completions 的内部服务。
            }
            default -> throw new BusinessException(503, "AI 结构化输出模式配置无效");
        }
    }

    private String textContent(JsonNode content) {
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : content) {
            if (item.path("text").isTextual()) {
                text.append(item.path("text").asText());
            }
        }
        return text.toString();
    }
}
