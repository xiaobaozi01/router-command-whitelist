package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.whitelist.dto.AiFormatCommandRequest;
import com.example.whitelist.dto.AiFormatCommandResponse;
import com.example.whitelist.dto.AiGenerateRegexRequest;
import com.example.whitelist.dto.AiGenerateRegexResponse;
import com.example.whitelist.service.AiAssistantService;
import com.example.whitelist.util.RichTextUtils;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-assistant;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.ai.enabled=true",
        "app.ai.protocol=mock"
})
class AiAssistantServiceTest {
    @Autowired
    private AiAssistantService aiAssistantService;

    @Test
    void formatsWithoutChangingCommandText() {
        AiFormatCommandResponse response = aiAssistantService.formatCommand(
                new AiFormatCommandRequest(
                        "<p>display ip interface INTERFACE_NAME</p>",
                        "查看接口信息",
                        List.of(),
                        null));

        assertThat(RichTextUtils.toPlainText(response.formattedHtml()))
                .isEqualTo("display ip interface INTERFACE_NAME");
        assertThat(response.formattedHtml()).contains("<strong>").contains("<em>INTERFACE_NAME</em>");
        assertThat(response.warnings()).isNotEmpty();
    }

    @Test
    void generatesAndValidatesRegexAndTestCases() {
        AiGenerateRegexResponse response = aiAssistantService.generateRegex(
                new AiGenerateRegexRequest(
                        "<p>display ip interface INTERFACE_NAME</p>",
                        "查看接口信息",
                        "",
                        true,
                        true,
                        List.of(),
                        null));

        assertThat(response.preview().valid()).isTrue();
        assertThat(response.preview().results()).extracting(item -> item.matched())
                .containsExactly(true, false);
        assertThat(response.positiveCases()).containsExactly("display ip interface INTERFACE_NAME");
    }

    @Test
    void validatesWithUserBoundarySettingsWithoutAskingAiToChooseThem() {
        AiGenerateRegexResponse response = aiAssistantService.generateRegex(
                new AiGenerateRegexRequest(
                        "<p><strong>display</strong> <em>VALUE</em></p>",
                        "测试用户边界设置",
                        "",
                        false,
                        false,
                        List.of(),
                        null));

        assertThat(response.preview().valid()).isTrue();
        assertThat(response.preview().expandedRegex()).doesNotStartWith("^").doesNotEndWith("$");
    }

    @Test
    void excludesStrikethroughContentFromGeneratedRegex() {
        AiGenerateRegexResponse response = aiAssistantService.generateRegex(
                new AiGenerateRegexRequest(
                        "<p><strong>display</strong> <s>ipv6</s> <em>interface</em></p>",
                        "ipv6 是设备支持但本系统不支持的部分",
                        "",
                        true,
                        true,
                        List.of(),
                        null));

        assertThat(response.regexTemplate()).contains("display interface").doesNotContain("ipv6");
        assertThat(response.positiveCases()).containsExactly("display interface");
    }

    @Test
    void reportsMockAvailability() {
        assertThat(aiAssistantService.status().available()).isTrue();
        assertThat(aiAssistantService.status().protocol()).isEqualTo("mock");
    }
}
