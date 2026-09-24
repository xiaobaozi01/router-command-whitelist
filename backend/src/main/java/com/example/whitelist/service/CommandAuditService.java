package com.example.whitelist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.whitelist.auth.AuthContext;
import com.example.whitelist.auth.CurrentUser;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.CommandAuditEventResponse;
import com.example.whitelist.dto.CommandAuditSnapshot;
import com.example.whitelist.dto.OptionItem;
import com.example.whitelist.entity.CommandAuditEvent;
import com.example.whitelist.entity.CommandCurrentView;
import com.example.whitelist.entity.CommandRule;
import com.example.whitelist.entity.CommandScene;
import com.example.whitelist.entity.Scene;
import com.example.whitelist.entity.ViewDefinition;
import com.example.whitelist.mapper.CommandAuditEventMapper;
import com.example.whitelist.mapper.CommandCurrentViewMapper;
import com.example.whitelist.mapper.CommandRuleMapper;
import com.example.whitelist.mapper.CommandSceneMapper;
import com.example.whitelist.mapper.SceneMapper;
import com.example.whitelist.mapper.ViewDefinitionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class CommandAuditService {
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_FRAGMENT_IMPACT = "FRAGMENT_IMPACT";
    public static final String SOURCE_WEB = "WEB";
    public static final String SOURCE_SYSTEM = "SYSTEM";

    private static final List<String> ALL_FIELDS = List.of(
            "expression", "regexTemplate", "matchStart", "matchEnd",
            "expandedRegex", "currentViews", "targetView", "scenes");

    private final CommandAuditEventMapper auditMapper;
    private final CommandRuleMapper commandMapper;
    private final CommandSceneMapper commandSceneMapper;
    private final CommandCurrentViewMapper currentViewMapper;
    private final SceneMapper sceneMapper;
    private final ViewDefinitionMapper viewMapper;
    private final RegexEngineService regexEngineService;
    private final ObjectMapper objectMapper;

    public CommandAuditService(
            CommandAuditEventMapper auditMapper,
            CommandRuleMapper commandMapper,
            CommandSceneMapper commandSceneMapper,
            CommandCurrentViewMapper currentViewMapper,
            SceneMapper sceneMapper,
            ViewDefinitionMapper viewMapper,
            RegexEngineService regexEngineService,
            ObjectMapper objectMapper
    ) {
        this.auditMapper = auditMapper;
        this.commandMapper = commandMapper;
        this.commandSceneMapper = commandSceneMapper;
        this.currentViewMapper = currentViewMapper;
        this.sceneMapper = sceneMapper;
        this.viewMapper = viewMapper;
        this.regexEngineService = regexEngineService;
        this.objectMapper = objectMapper;
    }

    public CommandAuditSnapshot capture(Long commandId) {
        CommandRule command = commandMapper.selectById(commandId);
        return command == null ? null : capture(command);
    }

    public CommandAuditSnapshot capture(CommandRule command) {
        List<OptionItem> scenes = commandSceneMapper.selectList(
                        new LambdaQueryWrapper<CommandScene>().eq(CommandScene::getCommandId, command.getId()))
                .stream()
                .map(relation -> sceneMapper.selectById(relation.getSceneId()))
                .filter(Objects::nonNull)
                .map(this::toOption)
                .sorted(Comparator.comparing(OptionItem::id))
                .toList();
        List<OptionItem> currentViews = currentViewMapper.selectList(
                        new LambdaQueryWrapper<CommandCurrentView>()
                                .eq(CommandCurrentView::getCommandId, command.getId()))
                .stream()
                .map(relation -> viewMapper.selectById(relation.getViewId()))
                .filter(Objects::nonNull)
                .map(this::toOption)
                .sorted(Comparator.comparing(OptionItem::id))
                .toList();
        ViewDefinition target = command.getTargetViewId() == null
                ? null
                : viewMapper.selectById(command.getTargetViewId());
        boolean matchStart = command.getMatchStart() == null || command.getMatchStart();
        boolean matchEnd = command.getMatchEnd() == null || command.getMatchEnd();
        String expandedRegex = regexEngineService.createExpander().expandAndValidate(
                command.getRegexTemplate(), matchStart, matchEnd);
        return new CommandAuditSnapshot(
                command.getExpressionHtml(), command.getExpressionText(), command.getRegexTemplate(),
                matchStart, matchEnd, expandedRegex, currentViews,
                target == null ? null : toOption(target), scenes);
    }

    public List<String> changedFields(CommandAuditSnapshot before, CommandAuditSnapshot after) {
        if (before == null || after == null) {
            return ALL_FIELDS;
        }
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(before.expressionHtml(), after.expressionHtml())
                || !Objects.equals(before.expressionText(), after.expressionText())) {
            fields.add("expression");
        }
        addIfChanged(fields, "regexTemplate", before.regexTemplate(), after.regexTemplate());
        addIfChanged(fields, "matchStart", before.matchStart(), after.matchStart());
        addIfChanged(fields, "matchEnd", before.matchEnd(), after.matchEnd());
        addIfChanged(fields, "expandedRegex", before.expandedRegex(), after.expandedRegex());
        addIfChanged(fields, "currentViews", before.currentViews(), after.currentViews());
        addIfChanged(fields, "targetView", before.targetView(), after.targetView());
        addIfChanged(fields, "scenes", before.scenes(), after.scenes());
        return List.copyOf(fields);
    }

    public boolean record(
            Long commandId,
            String action,
            CommandAuditSnapshot before,
            CommandAuditSnapshot after,
            String reason,
            String source
    ) {
        List<String> fields = changedFields(before, after);
        if (fields.isEmpty()) {
            return false;
        }
        CurrentUser actor = AuthContext.get();
        CommandAuditEvent event = new CommandAuditEvent();
        event.setCommandId(commandId);
        event.setAction(action);
        event.setActorUserId(actor == null ? null : actor.id());
        event.setActorUsername(actor == null ? "系统" : actor.username());
        event.setActorDisplayName(actor == null ? "系统" : actor.displayName());
        event.setOccurredAt(LocalDateTime.now());
        event.setChangeReason(normalizeReason(reason));
        event.setChangedFields(write(fields));
        event.setBeforeSnapshot(before == null ? null : write(before));
        event.setAfterSnapshot(after == null ? null : write(after));
        event.setSource(source);
        auditMapper.insert(event);
        return true;
    }

    public List<CommandAuditEventResponse> list(Long commandId) {
        return auditMapper.selectList(new LambdaQueryWrapper<CommandAuditEvent>()
                        .eq(CommandAuditEvent::getCommandId, commandId)
                        .orderByDesc(CommandAuditEvent::getOccurredAt)
                        .orderByDesc(CommandAuditEvent::getId))
                .stream().map(this::toResponse).toList();
    }

    public PageResponse<CommandAuditEventResponse> page(long current, long size) {
        Page<CommandAuditEvent> page = auditMapper.selectPage(
                Page.of(current, size),
                new LambdaQueryWrapper<CommandAuditEvent>()
                        .orderByDesc(CommandAuditEvent::getOccurredAt)
                        .orderByDesc(CommandAuditEvent::getId));
        return PageResponse.of(page, page.getRecords().stream().map(this::toResponse).toList());
    }

    private CommandAuditEventResponse toResponse(CommandAuditEvent event) {
        return new CommandAuditEventResponse(
                event.getId(), event.getCommandId(), event.getAction(), event.getActorUserId(),
                event.getActorUsername(), event.getActorDisplayName(), event.getOccurredAt(),
                event.getChangeReason(), readFields(event.getChangedFields()),
                readSnapshot(event.getBeforeSnapshot()), readSnapshot(event.getAfterSnapshot()),
                event.getSource());
    }

    private void addIfChanged(List<String> fields, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            fields.add(field);
        }
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? "未填写" : reason.trim();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法生成命令审计快照", exception);
        }
    }

    private List<String> readFields(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取命令审计字段", exception);
        }
    }

    private CommandAuditSnapshot readSnapshot(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, CommandAuditSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取命令审计快照", exception);
        }
    }

    private OptionItem toOption(Scene scene) {
        return new OptionItem(scene.getId(), scene.getName());
    }

    private OptionItem toOption(ViewDefinition view) {
        return new OptionItem(view.getId(), view.getName());
    }
}
