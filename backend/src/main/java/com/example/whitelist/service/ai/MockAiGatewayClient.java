package com.example.whitelist.service.ai;

import com.example.whitelist.common.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class MockAiGatewayClient implements AiGatewayClient {
    private static final Pattern PARAMETER_TOKEN = Pattern.compile(
            "(?:<[^>]+>|\\[[A-Z][A-Z0-9_-]*]|[A-Z][A-Z0-9_-]{1,})");
    private static final Pattern TOKEN = Pattern.compile("\\s+|\\S+");
    private final ObjectMapper objectMapper;

    public MockAiGatewayClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String protocol() {
        return "mock";
    }

    @Override
    public String generate(AiGatewayRequest request) {
        try {
            return switch (request.operation()) {
                case "format-command" -> objectMapper.writeValueAsString(format(request.payload()));
                case "generate-regex" -> objectMapper.writeValueAsString(regex(request.payload()));
                default -> throw new BusinessException(500, "未知的模拟 AI 操作");
            };
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "模拟 AI 结果生成失败");
        }
    }

    private Map<String, Object> format(Map<String, Object> payload) {
        String expression = String.valueOf(payload.getOrDefault("expressionText", ""));
        StringBuilder html = new StringBuilder("<p>");
        var matcher = TOKEN.matcher(expression);
        while (matcher.find()) {
            String token = matcher.group();
            String escaped = HtmlUtils.htmlEscape(token);
            if (token.isBlank()) {
                html.append(escaped);
            } else if (PARAMETER_TOKEN.matcher(token).matches()) {
                html.append("<em>").append(escaped).append("</em>");
            } else {
                html.append("<strong>").append(escaped).append("</strong>");
            }
        }
        html.append("</p>");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("formattedHtml", html.toString());
        result.put("explanation", "模拟模式：固定命令词加粗，大写参数或尖括号参数使用斜体。");
        result.put("warnings", List.of("当前结果由本地 Mock 生成，接入公司 Agent 后将使用真实模型。"));
        return result;
    }

    private Map<String, Object> regex(Map<String, Object> payload) {
        String expression = String.valueOf(payload.getOrDefault(
                "supportedExpressionText",
                payload.getOrDefault("expressionText", "")));
        List<String> negativeCases = new ArrayList<>();
        negativeCases.add("invalid-command");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("regexTemplate", Pattern.quote(expression));
        result.put("positiveCases", List.of(expression));
        result.put("negativeCases", negativeCases);
        result.put("explanation", "模拟模式：生成与当前命令文本完全匹配的 Java 正则。");
        result.put("warnings", List.of("当前结果由本地 Mock 生成，接入公司 Agent 后将使用真实模型。"));
        return result;
    }
}
