package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.whitelist.common.BusinessException;
import com.example.whitelist.dto.AiGenerateRegexRequest;
import com.example.whitelist.dto.AiGenerateRegexResponse;
import com.example.whitelist.mapper.RegexFragmentMapper;
import com.example.whitelist.mapper.ViewDefinitionMapper;
import com.example.whitelist.service.AiAssistantService;
import com.example.whitelist.service.RegexEngineService;
import com.example.whitelist.service.ai.AiGatewayRequest;
import com.example.whitelist.service.ai.AiGatewayRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiAssistantRegexRepairTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiGatewayRouter gateway = mock(AiGatewayRouter.class);
    private AiAssistantService service;

    @BeforeEach
    void setUp() {
        RegexFragmentMapper fragmentMapper = mock(RegexFragmentMapper.class);
        when(fragmentMapper.selectList(any())).thenReturn(List.of());
        service = new AiAssistantService(
                gateway,
                mock(ViewDefinitionMapper.class),
                new RegexEngineService(fragmentMapper),
                objectMapper
        );
    }

    @Test
    void asksAiToRepairARegexThatMatchesItsNegativeCase() throws Exception {
        when(gateway.generate(any()))
                .thenReturn(modelResult("ospf\\s+\\d+"))
                .thenReturn(modelResult("ospf\\s+1\\s+router-id\\s+1\\.1\\.1\\.1"));

        AiGenerateRegexResponse response = service.generateRegex(request());

        assertThat(response.regexTemplate())
                .isEqualTo("ospf\\s+1\\s+router-id\\s+1\\.1\\.1\\.1");
        assertThat(response.preview().results()).extracting(item -> item.matched())
                .containsExactly(true, false);

        ArgumentCaptor<AiGatewayRequest> requests = ArgumentCaptor.forClass(AiGatewayRequest.class);
        verify(gateway, times(2)).generate(requests.capture());
        AiGatewayRequest repairRequest = requests.getAllValues().get(1);
        assertThat(repairRequest.systemPrompt()).contains("对上一次生成结果的修正请求");
        assertThat(repairRequest.userPrompt())
                .contains("反例“ospf 1 router-id”仍会被正则匹配")
                .doesNotContain("matchStart", "matchEnd", "availableFragments");
        assertThat(requests.getAllValues().getFirst().systemPrompt())
                .contains("如果参数是纯数字，必须使用 \\d+")
                .contains("不得使用 \\S+、.+、.*")
                .doesNotContain("系统正则片段", "${NAME}");
    }

    @Test
    void retriesThreeTimesWithoutExposingRetriesToTheUser() throws Exception {
        when(gateway.generate(any())).thenReturn(modelResult("ospf\\s+\\d+"));

        assertThatThrownBy(() -> service.generateRegex(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 生成的正则未通过校验")
                .hasMessageNotContaining("ospf 1 router-id")
                .hasMessageNotContaining("重试")
                .hasMessageNotContaining("修正次数")
                .hasMessageNotContaining("尝试");
        verify(gateway, times(4)).generate(any());
    }

    @Test
    void leavesStrikethroughSemanticsToAiAndUserReview() throws Exception {
        when(gateway.generate(any())).thenReturn(modelResult(
                "display\\s+(?:ipv6\\s+)?interface",
                "display interface",
                "invalid-command"));

        AiGenerateRegexResponse response = service.generateRegex(new AiGenerateRegexRequest(
                "<p><strong>display</strong> <s>ipv6</s> <em>interface</em></p>",
                "ipv6 是本系统不支持的部分",
                "",
                true,
                true,
                List.of(),
                null));

        assertThat(response.regexTemplate()).isEqualTo("display\\s+(?:ipv6\\s+)?interface");
        ArgumentCaptor<AiGatewayRequest> requests = ArgumentCaptor.forClass(AiGatewayRequest.class);
        verify(gateway).generate(requests.capture());
        assertThat(requests.getAllValues().getFirst().userPrompt())
                .contains("\"expressionHtml\":\"<p><strong>display</strong> <s>ipv6</s> <em>interface</em></p>\"")
                .contains("\"supportedExpressionText\":\"display interface\"")
                .contains("\"excludedTexts\":[\"ipv6\"]");
    }

    private AiGenerateRegexRequest request() {
        return new AiGenerateRegexRequest(
                "<p>ospf 1 router-id 1.1.1.1</p>",
                "配置 OSPF Router ID",
                "",
                true,
                false,
                List.of(),
                null
        );
    }

    private String modelResult(String regexTemplate) throws Exception {
        return modelResult(
                regexTemplate,
                "ospf 1 router-id 1.1.1.1",
                "ospf 1 router-id");
    }

    private String modelResult(String regexTemplate, String positiveCase, String negativeCase) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "regexTemplate", regexTemplate,
                "positiveCases", List.of(positiveCase),
                "negativeCases", List.of(negativeCase),
                "explanation", "test",
                "warnings", List.of()
        ));
    }
}
