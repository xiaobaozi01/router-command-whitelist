package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.whitelist.common.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiResponseTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeUnifiedSuccessResponse() throws Exception {
        String content = objectMapper.writeValueAsString(ApiResponse.success(Map.of("id", 1)));
        JsonNode json = objectMapper.readTree(content);

        assertThat(json.get("code").asInt()).isEqualTo(200);
        assertThat(json.get("message").asText()).isEqualTo("操作成功");
        assertThat(json.get("data").get("id").asInt()).isEqualTo(1);
    }

    @Test
    void shouldKeepDataFieldForEmptySuccessResponse() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(ApiResponse.success()));

        assertThat(json.has("data")).isTrue();
        assertThat(json.get("data").isNull()).isTrue();
    }
}
