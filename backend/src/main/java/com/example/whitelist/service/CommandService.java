package com.example.whitelist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.whitelist.common.BusinessException;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.CommandRequest;
import com.example.whitelist.dto.CommandResponse;
import com.example.whitelist.dto.OptionItem;
import com.example.whitelist.entity.CommandCurrentView;
import com.example.whitelist.entity.CommandRule;
import com.example.whitelist.entity.CommandScene;
import com.example.whitelist.entity.Scene;
import com.example.whitelist.entity.ViewDefinition;
import com.example.whitelist.mapper.CommandCurrentViewMapper;
import com.example.whitelist.mapper.CommandRuleMapper;
import com.example.whitelist.mapper.CommandSceneMapper;
import com.example.whitelist.mapper.SceneMapper;
import com.example.whitelist.mapper.ViewDefinitionMapper;
import com.example.whitelist.util.RichTextUtils;
import com.example.whitelist.util.AuditUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandService {
    private final CommandRuleMapper commandMapper;
    private final CommandSceneMapper commandSceneMapper;
    private final CommandCurrentViewMapper currentViewMapper;
    private final SceneMapper sceneMapper;
    private final ViewDefinitionMapper viewMapper;
    private final RegexEngineService regexEngineService;

    public CommandService(
            CommandRuleMapper commandMapper,
            CommandSceneMapper commandSceneMapper,
            CommandCurrentViewMapper currentViewMapper,
            SceneMapper sceneMapper,
            ViewDefinitionMapper viewMapper,
            RegexEngineService regexEngineService
    ) {
        this.commandMapper = commandMapper;
        this.commandSceneMapper = commandSceneMapper;
        this.currentViewMapper = currentViewMapper;
        this.sceneMapper = sceneMapper;
        this.viewMapper = viewMapper;
        this.regexEngineService = regexEngineService;
    }

    public PageResponse<CommandResponse> page(
            long current,
            long size,
            String keyword,
            String regexKeyword,
            Long currentViewId,
            Long targetViewId,
            Long sceneId
    ) {
        LambdaQueryWrapper<CommandRule> query = new LambdaQueryWrapper<CommandRule>()
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(CommandRule::getExpressionText, keyword)
                        .or().like(CommandRule::getDescription, keyword))
                .eq(targetViewId != null, CommandRule::getTargetViewId, targetViewId)
                .apply(currentViewId != null,
                        "EXISTS (SELECT 1 FROM command_current_view ccv WHERE ccv.command_id = command_rule.id AND ccv.view_id = {0})",
                        currentViewId)
                .apply(sceneId != null,
                        "EXISTS (SELECT 1 FROM command_scene cs WHERE cs.command_id = command_rule.id AND cs.scene_id = {0})",
                        sceneId)
                .orderByDesc(CommandRule::getUpdatedAt);
        if (regexKeyword != null && !regexKeyword.isBlank()) {
            return pageByExpandedRegex(current, size, regexKeyword, query);
        }
        Page<CommandRule> page = commandMapper.selectPage(Page.of(current, size), query);
        return PageResponse.of(page, assemble(page.getRecords()));
    }

    private PageResponse<CommandResponse> pageByExpandedRegex(
            long current,
            long size,
            String regexKeyword,
            LambdaQueryWrapper<CommandRule> query
    ) {
        List<CommandRule> candidates = commandMapper.selectList(query);
        Map<Long, String> expandedRegexes = expandRegexes(candidates);
        String normalizedKeyword = regexKeyword.toLowerCase(Locale.ROOT);
        List<CommandRule> matches = candidates.stream()
                .filter(command -> expandedRegexes.get(command.getId())
                        .toLowerCase(Locale.ROOT)
                        .contains(normalizedKeyword))
                .toList();

        long total = matches.size();
        long offset = current - 1 > Long.MAX_VALUE / size
                ? Long.MAX_VALUE
                : (current - 1) * size;
        int fromIndex = (int) Math.min(offset, total);
        int toIndex = (int) Math.min((long) fromIndex + size, total);
        List<CommandRule> pageRecords = matches.subList(fromIndex, toIndex);
        long pages = total == 0 ? 0 : (total + size - 1) / size;
        return new PageResponse<>(
                assemble(pageRecords, expandedRegexes), total, current, size, pages);
    }

    public CommandResponse get(Long id) {
        return assemble(List.of(requireCommand(id))).getFirst();
    }

    public List<CommandResponse> listByScene(Long sceneId) {
        List<Long> commandIds = commandSceneMapper.selectList(
                        new LambdaQueryWrapper<CommandScene>().eq(CommandScene::getSceneId, sceneId))
                .stream().map(CommandScene::getCommandId).toList();
        if (commandIds.isEmpty()) {
            return List.of();
        }
        List<CommandRule> commands = commandMapper.selectList(new LambdaQueryWrapper<CommandRule>()
                .in(CommandRule::getId, commandIds)
                .orderByAsc(CommandRule::getExpressionText));
        return assemble(commands);
    }

    @Transactional
    public CommandResponse create(CommandRequest request) {
        ValidatedRequest validated = validateRequest(request);
        CommandRule command = new CommandRule();
        apply(command, request, validated);
        command.setCreatedBy(AuditUtils.currentUsername());
        command.setUpdatedBy(command.getCreatedBy());
        command.setCreatedAt(LocalDateTime.now());
        command.setUpdatedAt(command.getCreatedAt());
        commandMapper.insert(command);
        insertRelations(command.getId(), validated.sceneIds(), validated.currentViewIds());
        return get(command.getId());
    }

    @Transactional
    public CommandResponse update(Long id, CommandRequest request) {
        CommandRule command = requireCommand(id);
        ValidatedRequest validated = validateRequest(request);
        apply(command, request, validated);
        command.setUpdatedBy(AuditUtils.currentUsername());
        command.setUpdatedAt(LocalDateTime.now());
        commandMapper.updateById(command);
        commandSceneMapper.delete(new LambdaQueryWrapper<CommandScene>().eq(CommandScene::getCommandId, id));
        currentViewMapper.delete(new LambdaQueryWrapper<CommandCurrentView>().eq(CommandCurrentView::getCommandId, id));
        insertRelations(id, validated.sceneIds(), validated.currentViewIds());
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        requireCommand(id);
        commandSceneMapper.delete(new LambdaQueryWrapper<CommandScene>().eq(CommandScene::getCommandId, id));
        currentViewMapper.delete(new LambdaQueryWrapper<CommandCurrentView>().eq(CommandCurrentView::getCommandId, id));
        commandMapper.deleteById(id);
    }

    private ValidatedRequest validateRequest(CommandRequest request) {
        String expressionText = RichTextUtils.toPlainText(request.expressionHtml());
        if (expressionText.isBlank()) {
            throw new BusinessException(400, "命令行表达式不能为空");
        }
        if (expressionText.length() > 1000) {
            throw new BusinessException(400, "命令行表达式不能超过1000个字符");
        }
        boolean matchStart = request.matchStart() == null || request.matchStart();
        boolean matchEnd = request.matchEnd() == null || request.matchEnd();
        regexEngineService.expandAndValidate(request.regexTemplate(), matchStart, matchEnd);

        Set<Long> sceneIds = new LinkedHashSet<>(request.sceneIds());
        if (sceneMapper.selectByIds(sceneIds).size() != sceneIds.size()) {
            throw new BusinessException(400, "选择的场景不存在或已被删除");
        }
        Set<Long> currentViewIds = new LinkedHashSet<>(request.currentViewIds());
        if (viewMapper.selectByIds(currentViewIds).size() != currentViewIds.size()) {
            throw new BusinessException(400, "选择的所在视图不存在或已被删除");
        }
        if (request.targetViewId() != null && viewMapper.selectById(request.targetViewId()) == null) {
            throw new BusinessException(400, "选择的目标视图不存在或已被删除");
        }
        return new ValidatedRequest(expressionText, sceneIds, currentViewIds, matchStart, matchEnd);
    }

    private void apply(CommandRule command, CommandRequest request, ValidatedRequest validated) {
        command.setExpressionHtml(request.expressionHtml());
        command.setExpressionText(validated.expressionText());
        command.setDescription(request.description() == null ? "" : request.description().trim());
        command.setRegexTemplate(request.regexTemplate());
        command.setMatchStart(validated.matchStart());
        command.setMatchEnd(validated.matchEnd());
        command.setTargetViewId(request.targetViewId());
    }

    private void insertRelations(Long commandId, Set<Long> sceneIds, Set<Long> currentViewIds) {
        sceneIds.forEach(sceneId -> commandSceneMapper.insert(new CommandScene(commandId, sceneId)));
        currentViewIds.forEach(viewId -> currentViewMapper.insert(new CommandCurrentView(commandId, viewId)));
    }

    private CommandRule requireCommand(Long id) {
        CommandRule command = commandMapper.selectById(id);
        if (command == null) {
            throw new BusinessException(404, "命令不存在");
        }
        return command;
    }

    private List<CommandResponse> assemble(List<CommandRule> commands) {
        return assemble(commands, expandRegexes(commands));
    }

    private Map<Long, String> expandRegexes(List<CommandRule> commands) {
        if (commands.isEmpty()) {
            return Map.of();
        }
        RegexEngineService.RegexExpander expander = regexEngineService.createExpander();
        Map<Long, String> expandedRegexes = new HashMap<>();
        for (CommandRule command : commands) {
            boolean matchStart = command.getMatchStart() == null || command.getMatchStart();
            boolean matchEnd = command.getMatchEnd() == null || command.getMatchEnd();
            expandedRegexes.put(command.getId(), expander.expandAndValidate(
                    command.getRegexTemplate(), matchStart, matchEnd));
        }
        return expandedRegexes;
    }

    private List<CommandResponse> assemble(
            List<CommandRule> commands,
            Map<Long, String> expandedRegexes
    ) {
        if (commands.isEmpty()) {
            return List.of();
        }
        Set<Long> commandIds = commands.stream().map(CommandRule::getId).collect(Collectors.toSet());
        List<CommandScene> sceneRelations = commandSceneMapper.selectList(
                new LambdaQueryWrapper<CommandScene>().in(CommandScene::getCommandId, commandIds));
        List<CommandCurrentView> viewRelations = currentViewMapper.selectList(
                new LambdaQueryWrapper<CommandCurrentView>().in(CommandCurrentView::getCommandId, commandIds));

        Set<Long> sceneIds = sceneRelations.stream().map(CommandScene::getSceneId).collect(Collectors.toSet());
        Set<Long> viewIds = viewRelations.stream().map(CommandCurrentView::getViewId).collect(Collectors.toSet());
        commands.stream().map(CommandRule::getTargetViewId).filter(id -> id != null).forEach(viewIds::add);

        Map<Long, Scene> scenes = sceneIds.isEmpty() ? Map.of() : sceneMapper.selectByIds(sceneIds).stream()
                .collect(Collectors.toMap(Scene::getId, Function.identity()));
        Map<Long, ViewDefinition> views = viewIds.isEmpty() ? Map.of() : viewMapper.selectByIds(viewIds).stream()
                .collect(Collectors.toMap(ViewDefinition::getId, Function.identity()));

        Map<Long, List<OptionItem>> commandScenes = sceneRelations.stream().collect(Collectors.groupingBy(
                CommandScene::getCommandId,
                Collectors.mapping(item -> toOption(scenes.get(item.getSceneId())), Collectors.toList())));
        Map<Long, List<OptionItem>> commandViews = viewRelations.stream().collect(Collectors.groupingBy(
                CommandCurrentView::getCommandId,
                Collectors.mapping(item -> toOption(views.get(item.getViewId())), Collectors.toList())));

        List<CommandResponse> result = new ArrayList<>();
        for (CommandRule command : commands) {
            ViewDefinition target = command.getTargetViewId() == null ? null : views.get(command.getTargetViewId());
            boolean matchStart = command.getMatchStart() == null || command.getMatchStart();
            boolean matchEnd = command.getMatchEnd() == null || command.getMatchEnd();
            result.add(new CommandResponse(
                    command.getId(), command.getExpressionHtml(), command.getExpressionText(), command.getDescription(),
                    command.getRegexTemplate(), matchStart, matchEnd,
                    expandedRegexes.get(command.getId()),
                    commandViews.getOrDefault(command.getId(), List.of()),
                    target == null ? null : toOption(target),
                    commandScenes.getOrDefault(command.getId(), List.of()),
                    command.getCreatedBy(), command.getUpdatedBy(),
                    command.getCreatedAt(), command.getUpdatedAt()
            ));
        }
        return result;
    }

    private OptionItem toOption(Scene scene) {
        return new OptionItem(scene.getId(), scene.getName());
    }

    private OptionItem toOption(ViewDefinition view) {
        return new OptionItem(view.getId(), view.getName());
    }

    private record ValidatedRequest(
            String expressionText,
            Set<Long> sceneIds,
            Set<Long> currentViewIds,
            boolean matchStart,
            boolean matchEnd
    ) {
    }
}
