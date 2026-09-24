package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.whitelist.config.AiProperties;
import com.example.whitelist.service.ai.AiGatewayRequest;
import com.example.whitelist.service.ai.AnthropicCompatibleClient;
import com.example.whitelist.service.ai.HttpAiGatewaySupport;
import com.example.whitelist.service.ai.OpenAiCompatibleClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiProtocolClientTest {
    @Test
    void sendsOpenAiCompatibleRequest() {
        AiProperties properties = properties("openai", "json-schema");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleClient client = new OpenAiCompatibleClient(
                properties, new HttpAiGatewaySupport(properties, builder.build()));
        server.expect(requestTo("http://agent.internal/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"{\\\"answer\\\":\\\"ok\\\"}\"}}]}",
                        MediaType.APPLICATION_JSON));

        String response = client.generate(request());

        assertThat(response).isEqualTo("{\"answer\":\"ok\"}");
        server.verify();
    }

    @Test
    void sendsAnthropicCompatibleRequest() {
        AiProperties properties = properties("anthropic", "json-schema");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AnthropicCompatibleClient client = new AnthropicCompatibleClient(
                properties, new HttpAiGatewaySupport(properties, builder.build()));
        server.expect(requestTo("http://agent.internal/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.system").value("system prompt"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
                .andRespond(withSuccess(
                        "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"answer\\\":\\\"ok\\\"}\"}]}",
                        MediaType.APPLICATION_JSON));

        String response = client.generate(request());

        assertThat(response).isEqualTo("{\"answer\":\"ok\"}");
        server.verify();
    }

    private AiProperties properties(String protocol, String outputMode) {
        return new AiProperties(
                true,
                protocol,
                "http://agent.internal",
                "deepseek-v4-flash",
                "test-key",
                "auto",
                outputMode,
                "disabled",
                5,
                1000,
                "/v1/chat/completions",
                "/v1/messages",
                "2023-06-01"
        );
    }

    private AiGatewayRequest request() {
        return new AiGatewayRequest(
                "test",
                "system prompt",
                "user prompt",
                "test_result",
                Map.of(
                        "type", "object",
                        "properties", Map.of("answer", Map.of("type", "string")),
                        "required", List.of("answer"),
                        "additionalProperties", false
                ),
                Map.of()
        );
    }
}
