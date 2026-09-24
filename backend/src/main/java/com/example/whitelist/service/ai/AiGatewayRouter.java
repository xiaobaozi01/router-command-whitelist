package com.example.whitelist.service.ai;

import com.example.whitelist.common.BusinessException;
import com.example.whitelist.config.AiProperties;
import com.example.whitelist.dto.AiStatusResponse;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiGatewayRouter {
    private static final List<String> HTTP_PROTOCOLS = List.of("openai", "anthropic");
    private static final List<String> AUTH_MODES = List.of("auto", "bearer", "x-api-key", "none");
    private static final List<String> OUTPUT_MODES = List.of("auto", "json-schema", "json-object", "prompt-only");
    private static final List<String> THINKING_MODES = List.of("auto", "enabled", "disabled");

    private final AiProperties properties;
    private final List<AiGatewayClient> clients;

    public AiGatewayRouter(AiProperties properties, List<AiGatewayClient> clients) {
        this.properties = properties;
        this.clients = clients;
    }

    public AiStatusResponse status() {
        if (!properties.enabled()) {
            return new AiStatusResponse(false, false, properties.protocol(), properties.model(), "AI 功能未启用");
        }
        if (!List.of("mock", "openai", "anthropic").contains(properties.protocol())) {
            return unavailable("不支持的 AI 协议：" + properties.protocol());
        }
        if (!AUTH_MODES.contains(properties.authMode())) {
            return unavailable("AI 鉴权方式配置无效");
        }
        if (!OUTPUT_MODES.contains(properties.structuredOutputMode())) {
            return unavailable("AI 结构化输出模式配置无效");
        }
        if (!THINKING_MODES.contains(properties.thinkingMode())) {
            return unavailable("AI 思考模式配置无效");
        }
        if ("mock".equals(properties.protocol())) {
            return new AiStatusResponse(true, true, "mock", "mock", "本地模拟 AI 已就绪");
        }
        if (properties.baseUrl().isBlank() || !validHttpUrl(properties.baseUrl())) {
            return unavailable("AI 服务地址未配置或格式无效");
        }
        if (properties.model().isBlank()) {
            return unavailable("AI 模型名称未配置");
        }
        return new AiStatusResponse(true, true, properties.protocol(), properties.model(), "AI 服务配置已就绪");
    }

    public String generate(AiGatewayRequest request) {
        AiStatusResponse status = status();
        if (!status.available()) {
            throw new BusinessException(503, status.message());
        }
        return clients.stream()
                .filter(client -> client.protocol().equals(properties.protocol()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(503, "AI 协议适配器不可用"))
                .generate(request);
    }

    private AiStatusResponse unavailable(String message) {
        return new AiStatusResponse(true, false, properties.protocol(), properties.model(), message);
    }

    private boolean validHttpUrl(String value) {
        if (!HTTP_PROTOCOLS.contains(properties.protocol())) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
