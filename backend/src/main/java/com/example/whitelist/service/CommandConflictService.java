package com.example.whitelist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.whitelist.common.BusinessException;
import com.example.whitelist.dto.CommandApprovalSnapshot;
import com.example.whitelist.dto.CommandConflictCheckRequest;
import com.example.whitelist.dto.CommandConflictCheckResponse;
import com.example.whitelist.dto.OptionItem;
import com.example.whitelist.entity.CommandApprovalRequest;
import com.example.whitelist.entity.CommandCurrentView;
import com.example.whitelist.entity.CommandRule;
import com.example.whitelist.entity.CommandScene;
import com.example.whitelist.entity.Scene;
import com.example.whitelist.entity.ViewDefinition;
import com.example.whitelist.mapper.CommandApprovalRequestMapper;
import com.example.whitelist.mapper.CommandCurrentViewMapper;
import com.example.whitelist.mapper.CommandRuleMapper;
import com.example.whitelist.mapper.CommandSceneMapper;
import com.example.whitelist.mapper.SceneMapper;
import com.example.whitelist.mapper.ViewDefinitionMapper;
import com.example.whitelist.util.RichTextUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CommandConflictService {
    private static final int MAX_AI_CANDIDATES = 100;

    private final CommandRuleMapper commandMapper;
    private final CommandCurrentViewMapper currentViewMapper;
    private final CommandSceneMapper commandSceneMapper;
    private final CommandApprovalRequestMapper approvalMapper;
    private final ViewDefinitionMapper viewMapper;
    private final SceneMapper sceneMapper;
    private final RegexEngineService regexEngineService;
    private final CommandConflictAiService aiService;
    private final ObjectMapper objectMapper;

    public CommandConflictService(
            CommandRuleMapper commandMapper,
            CommandCurrentViewMapper currentViewMapper,
            CommandSceneMapper commandSceneMapper,
            CommandApprovalRequestMapper approvalMapper,
            ViewDefinitionMapper viewMapper,
            SceneMapper sceneMapper,
            RegexEngineService regexEngineService,
            CommandConflictAiService aiService,
            ObjectMapper objectMapper
    ) {
        this.commandMapper = commandMapper;
        this.currentViewMapper = currentViewMapper;
        this.commandSceneMapper = commandSceneMapper;
        this.approvalMapper = approvalMapper;
        this.viewMapper = viewMapper;
        this.sceneMapper = sceneMapper;
        this.regexEngineService = regexEngineService;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    public CommandConflictCheckResponse check(CommandConflictCheckRequest request) {
        String expressionText = RichTextUtils.toPlainText(request.expressionHtml());
        if (expressionText.isBlank()) {
            throw new BusinessException(400, "命令行表达式不能为空");
        }
        boolean matchStart = request.matchStart() == null || request.matchStart();
        boolean matchEnd = request.matchEnd() == null || request.matchEnd();
        String expandedRegex = regexEngineService.expandAndValidate(
                request.regexTemplate(), matchStart, matchEnd);
        Pattern subjectPattern = Pattern.compile(expandedRegex);

        LinkedHashSet<Long> requestedViewIds = new LinkedHashSet<>(request.currentViewIds());
        Map<Long, ViewDefinition> requestedViews = viewMapper.selectByIds(requestedViewIds).stream()
                .collect(Collectors.toMap(ViewDefinition::getId, Function.identity()));
        if (requestedViews.size() != requestedViewIds.size()) {
            throw new BusinessException(400, "选择的命令所在视图不存在或已被删除");
        }
        LinkedHashSet<Long> requestedSceneIds = new LinkedHashSet<>(request.sceneIds());
        Map<Long, Scene> requestedScenes = sceneMapper.selectByIds(requestedSceneIds).stream()
                .collect(Collectors.toMap(Scene::getId, Function.identity()));
        if (requestedScenes.size() != requestedSceneIds.size()) {
            throw new BusinessException(400, "选择的所属场景不存在或已被删除");
        }
        ViewDefinition subjectTarget = request.targetViewId() == null
                ? null : viewMapper.selectById(request.targetViewId());
        if (request.targetViewId() != null && subjectTarget == null) {
            throw new BusinessException(400, "选择的命令进入视图不存在或已被删除");
        }

        List<String> warnings = new ArrayList<>();
        CandidateLoad loaded = loadCandidates(request, requestedViewIds, requestedSceneIds, warnings);
        List<Candidate> candidates = loaded.candidates();
        Map<String, Candidate> candidatesByKey = candidates.stream()
                .collect(Collectors.toMap(Candidate::key, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, CommandConflictCheckResponse.Item> results = new LinkedHashMap<>();

        for (Candidate candidate : candidates) {
            if (expandedRegex.equals(candidate.expandedRegex())) {
                results.put(candidate.key(), toItem(candidate, "EXACT", List.of(), "CONFIRMED", request.targetViewId()));
            }
        }

        List<Candidate> aiCandidates = candidates.stream()
                .filter(candidate -> !results.containsKey(candidate.key()))
                .sorted(Comparator.comparingInt((Candidate candidate) ->
                        similarityScore(expressionText, candidate.expressionText())).reversed())
                .limit(MAX_AI_CANDIDATES)
                .toList();
        if (candidates.size() - results.size() > MAX_AI_CANDIDATES) {
            warnings.add("同视图候选较多，AI 已优先检测语义最接近的 " + MAX_AI_CANDIDATES + " 条；完全相同的正则仍已全量检查。");
        }

        boolean aiUsed = false;
        if (!aiCandidates.isEmpty()) {
            try {
                List<CommandConflictAiService.Recall> recalled = aiService.recall(
                        new CommandConflictAiService.Subject(
                                expressionText,
                                normalize(request.description()),
                                expandedRegex,
                                subjectTarget == null ? null : subjectTarget.getName()),
                        aiCandidates.stream().map(this::toAiCandidate).toList());
                aiUsed = true;
                for (CommandConflictAiService.Recall recall : recalled) {
                    Candidate candidate = candidatesByKey.get(recall.candidateKey());
                    if (candidate == null || results.containsKey(candidate.key())) continue;
                    List<String> commonMatches = recall.examples().stream()
                            .filter(example -> subjectPattern.matcher(example).find()
                                    && candidate.pattern().matcher(example).find())
                            .distinct()
                            .toList();
                    String relation = commonMatches.isEmpty()
                            ? "SEMANTIC_SIMILAR"
                            : normalizedVerifiedRelation(recall.relation());
                    String confidence = relation.equals("OVERLAP") ? "CONFIRMED" : "SUSPECTED";
                    results.put(candidate.key(), toItem(
                            candidate, relation, commonMatches, confidence, request.targetViewId()));
                }
            } catch (BusinessException exception) {
                warnings.add("AI 候选召回暂时不可用，本次仅完成了正则完全重复检查：" + exception.getMessage());
            }
        }

        List<CommandConflictCheckResponse.Item> sorted = results.values().stream()
                .sorted(Comparator
                        .comparingInt((CommandConflictCheckResponse.Item item) -> riskOrder(item.riskLevel()))
                        .thenComparing(CommandConflictCheckResponse.Item::sourceType)
                        .thenComparing(CommandConflictCheckResponse.Item::sourceId))
                .toList();
        return new CommandConflictCheckResponse(candidates.size(), aiUsed, warnings, sorted);
    }

    private CandidateLoad loadCandidates(
            CommandConflictCheckRequest request,
            Set<Long> requestedViewIds,
            Set<Long> requestedSceneIds,
            List<String> warnings
    ) {
        LinkedHashMap<String, Candidate> result = new LinkedHashMap<>();
        List<CommandCurrentView> matchingRelations = currentViewMapper.selectList(
                new LambdaQueryWrapper<CommandCurrentView>()
                        .in(CommandCurrentView::getViewId, requestedViewIds));
        Set<Long> viewMatchedIds = matchingRelations.stream().map(CommandCurrentView::getCommandId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<CommandScene> matchingSceneRelations = commandSceneMapper.selectList(
                new LambdaQueryWrapper<CommandScene>()
                        .in(CommandScene::getSceneId, requestedSceneIds));
        Set<Long> sceneMatchedIds = matchingSceneRelations.stream().map(CommandScene::getCommandId)
                .collect(Collectors.toSet());
        Set<Long> activeIds = viewMatchedIds.stream()
                .filter(sceneMatchedIds::contains)
                .filter(id -> !Objects.equals(id, request.commandId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!activeIds.isEmpty()) {
            List<CommandRule> commands = commandMapper.selectList(new LambdaQueryWrapper<CommandRule>()
                    .in(CommandRule::getId, activeIds)
                    .orderByAsc(CommandRule::getId));
            Map<Long, List<OptionItem>> viewsByCommand = loadCurrentViews(activeIds);
            Set<Long> targetIds = commands.stream().map(CommandRule::getTargetViewId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, ViewDefinition> targets = targetIds.isEmpty() ? Map.of() : viewMapper.selectByIds(targetIds)
                    .stream().collect(Collectors.toMap(ViewDefinition::getId, Function.identity()));
            RegexEngineService.RegexExpander expander = regexEngineService.createExpander();
            for (CommandRule command : commands) {
                boolean start = command.getMatchStart() == null || command.getMatchStart();
                boolean end = command.getMatchEnd() == null || command.getMatchEnd();
                String expanded = expander.expandAndValidate(command.getRegexTemplate(), start, end);
                ViewDefinition target = command.getTargetViewId() == null ? null : targets.get(command.getTargetViewId());
                Candidate candidate = new Candidate(
                        "E:" + command.getId(), "EFFECTIVE", command.getId(), command.getId(),
                        command.getExpressionText(), normalize(command.getDescription()), command.getRegexTemplate(),
                        expanded, Pattern.compile(expanded), viewsByCommand.getOrDefault(command.getId(), List.of()),
                        target == null ? null : new OptionItem(target.getId(), target.getName()));
                result.put(candidate.key(), candidate);
            }
        }

        List<CommandApprovalRequest> pending = approvalMapper.selectList(
                new LambdaQueryWrapper<CommandApprovalRequest>()
                        .eq(CommandApprovalRequest::getStatus, CommandApprovalService.STATUS_PENDING)
                        .in(CommandApprovalRequest::getRequestType,
                                CommandApprovalService.TYPE_CREATE, CommandApprovalService.TYPE_UPDATE)
                        .orderByAsc(CommandApprovalRequest::getId));
        Set<Long> supersededActiveIds = new LinkedHashSet<>();
        for (CommandApprovalRequest approval : pending) {
            if (Objects.equals(approval.getId(), request.approvalRequestId())) continue;
            CommandApprovalSnapshot snapshot;
            try {
                snapshot = objectMapper.readValue(approval.getProposedSnapshot(), CommandApprovalSnapshot.class);
            } catch (JsonProcessingException exception) {
                warnings.add("待审批单 #" + approval.getId() + " 的快照无法读取，本次已跳过。");
                continue;
            }
            boolean sharesView = snapshot.currentViews().stream().map(OptionItem::id).anyMatch(requestedViewIds::contains);
            boolean sharesScene = snapshot.scenes().stream().map(OptionItem::id).anyMatch(requestedSceneIds::contains);
            if (!sharesView || !sharesScene) continue;
            if (CommandApprovalService.TYPE_UPDATE.equals(approval.getRequestType())
                    && approval.getTargetCommandId() != null) {
                supersededActiveIds.add(approval.getTargetCommandId());
            }
            Candidate candidate = new Candidate(
                    "P:" + approval.getId(), "PENDING", approval.getId(), approval.getTargetCommandId(),
                    snapshot.expressionText(), normalize(snapshot.description()), snapshot.regexTemplate(),
                    snapshot.expandedRegex(), Pattern.compile(snapshot.expandedRegex()), snapshot.currentViews(),
                    snapshot.targetView());
            result.put(candidate.key(), candidate);
        }
        supersededActiveIds.forEach(id -> result.remove("E:" + id));
        return new CandidateLoad(List.copyOf(result.values()));
    }

    private Map<Long, List<OptionItem>> loadCurrentViews(Set<Long> commandIds) {
        List<CommandCurrentView> relations = currentViewMapper.selectList(
                new LambdaQueryWrapper<CommandCurrentView>().in(CommandCurrentView::getCommandId, commandIds));
        Set<Long> viewIds = relations.stream().map(CommandCurrentView::getViewId).collect(Collectors.toSet());
        Map<Long, ViewDefinition> views = viewIds.isEmpty() ? Map.of() : viewMapper.selectByIds(viewIds).stream()
                .collect(Collectors.toMap(ViewDefinition::getId, Function.identity()));
        return relations.stream().filter(relation -> views.containsKey(relation.getViewId()))
                .collect(Collectors.groupingBy(
                        CommandCurrentView::getCommandId,
                        LinkedHashMap::new,
                        Collectors.mapping(relation -> {
                            ViewDefinition view = views.get(relation.getViewId());
                            return new OptionItem(view.getId(), view.getName());
                        }, Collectors.toList())));
    }

    private CommandConflictAiService.Candidate toAiCandidate(Candidate candidate) {
        return new CommandConflictAiService.Candidate(
                candidate.key(), candidate.sourceType(), candidate.expressionText(), candidate.description(),
                candidate.expandedRegex(), candidate.targetView() == null ? null : candidate.targetView().name());
    }

    private CommandConflictCheckResponse.Item toItem(
            Candidate candidate,
            String relation,
            List<String> evidence,
            String confidence,
            Long subjectTargetViewId
    ) {
        boolean targetConflict = !relation.equals("SEMANTIC_SIMILAR")
                && !Objects.equals(subjectTargetViewId,
                        candidate.targetView() == null ? null : candidate.targetView().id());
        String riskType;
        String riskLevel;
        String message;
        if (targetConflict) {
            riskType = "TARGET_VIEW_CONFLICT";
            riskLevel = "HIGH";
            message = "两条规则的匹配范围存在交集，但命令进入视图不同。";
        } else if (relation.equals("EXACT") || relation.equals("EQUIVALENT")) {
            riskType = "DUPLICATE";
            riskLevel = "MEDIUM";
            message = relation.equals("EXACT")
                    ? "实际匹配正则完全相同，疑似重复命令。"
                    : "两条规则疑似接受基本一致的命令范围。";
        } else if (relation.equals("NEW_CONTAINS_EXISTING") || relation.equals("EXISTING_CONTAINS_NEW")) {
            riskType = "REDUNDANT";
            riskLevel = "MEDIUM";
            message = relation.equals("NEW_CONTAINS_EXISTING")
                    ? "新规则疑似覆盖该已有规则的匹配范围。"
                    : "新规则的匹配范围疑似已被该规则覆盖。";
        } else if (relation.equals("OVERLAP")) {
            riskType = "MATCH_RANGE_OVERLAP";
            riskLevel = "MEDIUM";
            message = "已找到同时匹配两条规则的命令样例。";
        } else {
            riskType = "SEMANTIC_SIMILAR";
            riskLevel = "LOW";
            message = "AI 发现命令语义相似，但后端未确认正则存在交集。";
        }
        return new CommandConflictCheckResponse.Item(
                candidate.sourceType(), candidate.sourceId(), candidate.commandId(),
                candidate.expressionText(), candidate.description(), candidate.regexTemplate(),
                candidate.expandedRegex(), candidate.currentViews(), candidate.targetView(), relation,
                riskType, riskLevel, confidence, evidence, message);
    }

    private String normalizedVerifiedRelation(String aiRelation) {
        return switch (aiRelation) {
            case "EQUIVALENT" -> "EQUIVALENT";
            case "NEW_CONTAINS_EXISTING" -> "NEW_CONTAINS_EXISTING";
            case "EXISTING_CONTAINS_NEW" -> "EXISTING_CONTAINS_NEW";
            default -> "OVERLAP";
        };
    }

    private int similarityScore(String left, String right) {
        String normalizedLeft = normalizeForSimilarity(left);
        String normalizedRight = normalizeForSimilarity(right);
        if (normalizedLeft.equals(normalizedRight)) return 1000;
        String[] leftParts = normalizedLeft.split(" ");
        String[] rightParts = normalizedRight.split(" ");
        int sharedPrefix = 0;
        while (sharedPrefix < leftParts.length && sharedPrefix < rightParts.length
                && leftParts[sharedPrefix].equals(rightParts[sharedPrefix])) {
            sharedPrefix++;
        }
        Set<String> leftTokens = new HashSet<>(java.util.Arrays.asList(leftParts));
        long sharedTokens = java.util.Arrays.stream(rightParts).filter(leftTokens::contains).distinct().count();
        return sharedPrefix * 20 + (int) sharedTokens;
    }

    private String normalizeForSimilarity(String value) {
        return normalize(value).toLowerCase().replaceAll("\\s+", " ");
    }

    private int riskOrder(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record CandidateLoad(List<Candidate> candidates) {
    }

    private record Candidate(
            String key,
            String sourceType,
            Long sourceId,
            Long commandId,
            String expressionText,
            String description,
            String regexTemplate,
            String expandedRegex,
            Pattern pattern,
            List<OptionItem> currentViews,
            OptionItem targetView
    ) {
    }
}
