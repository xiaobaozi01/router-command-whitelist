package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.whitelist.common.BusinessException;
import com.example.whitelist.dto.AiApprovalAnalysisResponse;
import com.example.whitelist.dto.AiFormatCommandRequest;
import com.example.whitelist.dto.CommandApprovalResponse;
import com.example.whitelist.dto.CommandApprovalSnapshot;
import com.example.whitelist.dto.AiGenerateRegexRequest;
import com.example.whitelist.dto.AiGenerateRegexResponse;
import com.example.whitelist.dto.OptionItem;
import com.example.whitelist.mapper.RegexFragmentMapper;
import com.example.whitelist.mapper.ViewDefinitionMapper;
import com.example.whitelist.service.AiAssistantService;
import com.example.whitelist.service.RegexEngineService;
import com.example.whitelist.service.ai.AiGatewayRequest;
import com.example.whitelist.service.ai.AiGatewayRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiAssistantRegexRepairTest {
    private static final String STRIKETHROUGH_SEMANTICS =
            "删除线（HTML s 标签）具有唯一的业务含义：该部分语法由华为设备支持，但本系统因业务原因不支持。";
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
                .contains(STRIKETHROUGH_SEMANTICS)
                .doesNotContain("系统正则片段", "${NAME}");
    }

    @Test
    void usesSharedStrikethroughSemanticsWhenFormatting() throws Exception {
        when(gateway.generate(any())).thenReturn(objectMapper.writeValueAsString(Map.of(
                "formattedHtml", "<p><strong>display</strong> <s>ipv6</s></p>",
                "explanation", "保留用户标记的删除线。",
                "warnings", List.of()
        )));

        service.formatCommand(new AiFormatCommandRequest(
                "<p><strong>display</strong> <s>ipv6</s></p>",
                "ipv6 是设备支持但本系统不支持的部分",
                List.of(), null));

        ArgumentCaptor<AiGatewayRequest> requests = ArgumentCaptor.forClass(AiGatewayRequest.class);
        verify(gateway).generate(requests.capture());
        assertThat(requests.getValue().systemPrompt())
                .contains(STRIKETHROUGH_SEMANTICS)
                .contains("不得擅自移除已有 s 或改变其范围");
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
    void letsAiInterpretStrikethroughWithoutBackendDerivedText() throws Exception {
        when(gateway.generate(any())).thenReturn(modelResult(
                "display\\s+interface",
                "display interface",
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

        assertThat(response.supportedExpressionText()).isEqualTo("display interface");
        assertThat(response.regexTemplate()).isEqualTo("display\\s+interface");
        ArgumentCaptor<AiGatewayRequest> requests = ArgumentCaptor.forClass(AiGatewayRequest.class);
        verify(gateway).generate(requests.capture());
        assertThat(requests.getAllValues().getFirst().userPrompt())
                .contains("\"expressionHtml\":\"<p><strong>display</strong> <s>ipv6</s> <em>interface</em></p>\"")
                .doesNotContain("supportedExpressionText", "excludedTexts", "expressionText");
    }

    @Test
    void acceptsAiSemanticRemovalOfAnAlternativeBranch() throws Exception {
        String supported = "ospf bfd { min-rx-interval receive-interval | "
                + "min-tx-interval transmit-interval | frr-binding } *";
        when(gateway.generate(any())).thenReturn(modelResult(
                "ospf\\s+bfd\\s+frr-binding",
                supported,
                "ospf bfd frr-binding",
                "ospf bfd detect-multiplier 3"));
        String html = "<p><strong>ospf bfd</strong> { "
                + "<strong>min-rx-interval</strong> <em>receive-interval</em> | "
                + "<strong>min-tx-interval</strong> <em>transmit-interval</em> | "
                + "<s><strong>detect-multiplier</strong> <em>multiplier-value</em></s> | "
                + "<strong>frr-binding</strong> } *</p>";

        AiGenerateRegexResponse response = service.generateRegex(new AiGenerateRegexRequest(
                html, "配置 OSPF BFD", "", true, true, List.of(), null));

        assertThat(response.supportedExpressionText()).isEqualTo(supported).doesNotContain("| |", "detect-multiplier");
        ArgumentCaptor<AiGatewayRequest> requests = ArgumentCaptor.forClass(AiGatewayRequest.class);
        verify(gateway).generate(requests.capture());
        assertThat(requests.getValue().userPrompt())
                .contains("<s><strong>detect-multiplier</strong> <em>multiplier-value</em></s>")
                .doesNotContain("supportedExpressionText", "excludedTexts");
        assertThat(requests.getValue().systemPrompt())
                .contains("不得留下连续、开头或结尾的竖线")
                .contains("嵌套选项应递归处理");
    }

    @Test
    void explainsStrikethroughSemanticsWhenAnalyzingApproval() throws Exception {
        when(gateway.generate(any())).thenReturn(objectMapper.writeValueAsString(Map.of(
                "riskLevel", "HIGH",
                "recommendation", "REVIEW",
                "summary", "命令格式及删除线范围发生变化。",
                "recommendationReason", "需要确认系统支持范围是否被意外扩大。",
                "riskPoints", List.of("原本不支持的 ipv6 可能进入实际匹配范围。"),
                "checklist", List.of("确认删除线变化符合业务预期。"),
                "warnings", List.of("当前信息不足以确认设备实际行为。")
        )));
        CommandApprovalSnapshot before = approvalSnapshot(
                "<p>display <s>ipv6</s> interface</p>");
        CommandApprovalSnapshot proposed = approvalSnapshot(
                "<p>display ipv6 interface</p>");
        CommandApprovalResponse approval = new CommandApprovalResponse(
                12L, "UPDATE", "PENDING", 5L, 1L, before, proposed,
                "支持 IPv6", 3L, "developer", "开发人员", LocalDateTime.now(),
                null, null, null, null, null, 0L);

        AiApprovalAnalysisResponse response = service.analyzeApproval(approval);

        ArgumentCaptor<AiGatewayRequest> requests = ArgumentCaptor.forClass(AiGatewayRequest.class);
        verify(gateway).generate(requests.capture());
        AiGatewayRequest request = requests.getValue();
        assertThat(request.systemPrompt())
                .contains(STRIKETHROUGH_SEMANTICS)
                .contains("审批对象是本系统中的命令白名单规则数据，不是路由器设备")
                .contains("绝不表示向路由器下发命令、修改设备配置、从设备删除命令")
                .contains("不得仅因申请类型是修改或删除就提高风险等级")
                .contains("删除线内容是否意外进入实际正则")
                .contains("不要解释输入或输出的数据结构")
                .doesNotContain(
                        "changedFields", "regexTemplate", "expressionFormatting", "所属场景",
                        "业务背景", "业务场景", "使用对象", "上线计划");
        assertThat(request.userPrompt())
                .contains("\"申请原因\":\"支持 IPv6\"")
                .doesNotContain("需求说明（业务原因）")
                .contains("\"命令表达式（含格式标记）\":\"<p>display <s>ipv6</s> interface</p>\"")
                .contains("\"命令表达式（含格式标记）\":\"<p>display ipv6 interface</p>\"")
                .contains("\"本次涉及内容\":[\"命令格式及删除线范围\"]")
                .doesNotContain("expressionHtml", "changedFields", "expressionFormatting",
                        "supportedExpressionText", "excludedTexts", "所属场景", "内部巡检分类");
        assertThat(response.summary()).isEqualTo("命令格式及删除线范围发生变化。");
        assertThat(response.recommendationReason()).isEqualTo("需要确认系统支持范围是否被意外扩大。");
        assertThat(response.riskPoints()).containsExactly("原本不支持的 ipv6 可能进入实际匹配范围。");
        assertThat(response.checklist()).containsExactly("确认删除线变化符合业务预期。");
        assertThat(response.warnings()).containsExactly("当前信息不足以确认设备实际行为。");
    }

    @Test
    void doesNotSendOrRequestReasonsForCreateAndDeleteApprovals() throws Exception {
        when(gateway.generate(any())).thenReturn(objectMapper.writeValueAsString(Map.of(
                "riskLevel", "LOW",
                "recommendation", "APPROVE",
                "summary", "命令及匹配范围未发现明显问题。",
                "recommendationReason", "技术检查未发现需要阻止审批的问题。",
                "riskPoints", List.of(),
                "checklist", List.of("确认命令表达式与正则匹配范围一致。"),
                "warnings", List.of()
        )));
        CommandApprovalSnapshot snapshot = approvalSnapshot("<p>display version</p>");
        CommandApprovalResponse createApproval = new CommandApprovalResponse(
                21L, "CREATE", "PENDING", null, null, null, snapshot,
                "新增命令", 3L, "developer", "开发人员", LocalDateTime.now(),
                null, null, null, null, null, 0L);
        CommandApprovalResponse deleteApproval = new CommandApprovalResponse(
                22L, "DELETE", "PENDING", 8L, 1L, snapshot, snapshot,
                "删除命令", 3L, "developer", "开发人员", LocalDateTime.now(),
                null, null, null, null, null, 0L);

        service.analyzeApproval(createApproval);
        service.analyzeApproval(deleteApproval);

        ArgumentCaptor<AiGatewayRequest> requests = ArgumentCaptor.forClass(AiGatewayRequest.class);
        verify(gateway, times(2)).generate(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.systemPrompt())
                    .contains("新增或删除命令不需要申请原因")
                    .contains("不得要求补充原因或实际用途");
            assertThat(request.userPrompt()).doesNotContain("\"申请原因\"");
        });
        assertThat(requests.getAllValues().get(0).userPrompt()).contains("\"申请类型\":\"新增命令\"");
        assertThat(requests.getAllValues().get(1).userPrompt()).contains("\"申请类型\":\"删除命令\"");
    }

    private CommandApprovalSnapshot approvalSnapshot(String expressionHtml) {
        return new CommandApprovalSnapshot(
                expressionHtml, "display ipv6 interface", "查看接口",
                "display\\s+ipv6\\s+interface", true, true,
                "^display\\s+ipv6\\s+interface$", List.of(), null,
                List.of(new OptionItem(9L, "内部巡检分类")), 1L);
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
        return modelResult(regexTemplate, positiveCase, positiveCase, negativeCase);
    }

    private String modelResult(
            String regexTemplate,
            String supportedExpressionText,
            String positiveCase,
            String negativeCase
    ) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "supportedExpressionText", supportedExpressionText,
                "regexTemplate", regexTemplate,
                "positiveCases", List.of(positiveCase),
                "negativeCases", List.of(negativeCase),
                "explanation", "test",
                "warnings", List.of()
        ));
    }
}
