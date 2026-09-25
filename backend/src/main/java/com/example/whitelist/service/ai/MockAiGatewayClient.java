package com.example.whitelist.service.ai;

import com.example.whitelist.common.BusinessException;
import com.example.whitelist.util.RichTextUtils;
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
                case "analyze-approval" -> objectMapper.writeValueAsString(approvalAnalysis(request.payload()));
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
        String expression = RichTextUtils.toPlainTextWithoutStrikethrough(
                String.valueOf(payload.getOrDefault("expressionHtml", "")));
        List<String> negativeCases = new ArrayList<>();
        negativeCases.add("invalid-command");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supportedExpressionText", expression);
        result.put("regexTemplate", Pattern.quote(expression));
        result.put("positiveCases", List.of(expression));
        result.put("negativeCases", negativeCases);
        result.put("explanation", "模拟模式：生成与当前命令文本完全匹配的 Java 正则。");
        result.put("warnings", List.of("当前结果由本地 Mock 机械移除删除线，仅用于验证流程；复杂语法请接入真实模型确认。"));
        return result;
    }

    private Map<String, Object> approvalAnalysis(Map<String, Object> payload) {
        String requestType = String.valueOf(payload.getOrDefault("申请类型", ""));
        @SuppressWarnings("unchecked")
        List<String> changedFields = (List<String>) payload.getOrDefault("本次涉及内容", List.of());
        boolean boundaryChanged = changedFields.contains("开头匹配边界") || changedFields.contains("结尾匹配边界");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskLevel", boundaryChanged ? "HIGH" : "MEDIUM");
        result.put("recommendation", "REVIEW");
        result.put("summary", switch (requestType) {
            case "新增命令" -> "申请新增一条命令白名单规则。";
            case "修改命令" -> "申请修改现有命令，本次涉及：" + String.join("、", changedFields) + "。";
            case "删除命令" -> "申请删除一条现有命令白名单规则。";
            default -> "审批申请包含待核对的命令变更。";
        });
        result.put("recommendationReason", "修改命令".equals(requestType)
                ? "模拟模式无法判断真实设备语义，建议管理员结合申请原因人工复核。"
                : "模拟模式无法判断真实设备语义，建议管理员核对命令及匹配范围。");
        result.put("riskPoints", "删除命令".equals(requestType)
                ? List.of("删除后该规则将不再参与白名单匹配。")
                : List.of());
        result.put("checklist", List.of("核对命令表达式与匹配范围", "核对所在视图和目标视图"));
        result.put("warnings", List.of("当前结论由本地 Mock 生成，接入公司 Agent 后将使用真实模型。"));
        return result;
    }
}
