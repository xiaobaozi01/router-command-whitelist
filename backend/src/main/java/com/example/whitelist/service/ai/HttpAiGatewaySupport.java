package com.example.whitelist.service.ai;

import com.example.whitelist.common.BusinessException;
import com.example.whitelist.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpAiGatewaySupport {
    private final AiProperties properties;
    private final RestClient restClient;

    @Autowired
    public HttpAiGatewaySupport(AiProperties properties) {
        this.properties = properties;
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public HttpAiGatewaySupport(AiProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public JsonNode post(String path, Map<String, Object> body, Consumer<HttpHeaders> headers) {
        try {
            return restClient.post()
                    .uri(endpoint(path))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new BusinessException(502, "AI 服务请求失败（HTTP " + exception.getStatusCode().value() + "）");
        } catch (ResourceAccessException exception) {
            throw new BusinessException(504, "AI 服务连接或响应超时");
        } catch (RestClientException exception) {
            throw new BusinessException(502, "AI 服务响应无法读取");
        }
    }

    public void applyAuthentication(HttpHeaders headers, String defaultMode) {
        String mode = "auto".equals(properties.authMode()) ? defaultMode : properties.authMode();
        if (properties.apiKey().isBlank() || "none".equals(mode)) {
            return;
        }
        if ("bearer".equals(mode)) {
            headers.setBearerAuth(properties.apiKey());
            return;
        }
        if ("x-api-key".equals(mode)) {
            headers.set("x-api-key", properties.apiKey());
            return;
        }
        throw new BusinessException(503, "AI 鉴权方式配置无效");
    }

    private URI endpoint(String path) {
        String baseUrl = properties.baseUrl();
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + normalizedPath
                : baseUrl + normalizedPath);
    }
}
