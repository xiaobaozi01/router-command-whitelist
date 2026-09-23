package com.example.whitelist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.whitelist.common.BusinessException;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.NameRequest;
import com.example.whitelist.dto.OptionItem;
import com.example.whitelist.dto.ViewResponse;
import com.example.whitelist.entity.CommandCurrentView;
import com.example.whitelist.entity.CommandRule;
import com.example.whitelist.entity.ViewDefinition;
import com.example.whitelist.mapper.CommandCurrentViewMapper;
import com.example.whitelist.mapper.CommandRuleMapper;
import com.example.whitelist.mapper.ViewDefinitionMapper;
import com.example.whitelist.util.AuditUtils;
import com.example.whitelist.util.TimeSort;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ViewDefinitionService {
    private final ViewDefinitionMapper viewMapper;
    private final CommandCurrentViewMapper currentViewMapper;
    private final CommandRuleMapper commandMapper;

    public ViewDefinitionService(
            ViewDefinitionMapper viewMapper,
            CommandCurrentViewMapper currentViewMapper,
            CommandRuleMapper commandMapper
    ) {
        this.viewMapper = viewMapper;
        this.currentViewMapper = currentViewMapper;
        this.commandMapper = commandMapper;
    }

    public PageResponse<ViewResponse> page(long current, long size, String keyword, String sortField, String sortOrder) {
        TimeSort timeSort = TimeSort.parse(sortField, sortOrder);
        LambdaQueryWrapper<ViewDefinition> query = new LambdaQueryWrapper<ViewDefinition>()
                .like(keyword != null && !keyword.isBlank(), ViewDefinition::getName, keyword);
        if (timeSort.field() == TimeSort.Field.CREATED_AT) {
            query.orderBy(true, timeSort.ascending(), ViewDefinition::getCreatedAt);
        } else {
            query.orderBy(true, timeSort.ascending(), ViewDefinition::getUpdatedAt);
        }
        query.orderBy(true, timeSort.ascending(), ViewDefinition::getId);
        Page<ViewDefinition> page = viewMapper.selectPage(Page.of(current, size),
                query);
        Set<Long> ids = page.getRecords().stream().map(ViewDefinition::getId).collect(Collectors.toSet());
        Map<Long, Set<Long>> references = new HashMap<>();
        if (!ids.isEmpty()) {
            currentViewMapper.selectList(new LambdaQueryWrapper<CommandCurrentView>()
                            .in(CommandCurrentView::getViewId, ids))
                    .forEach(item -> references.computeIfAbsent(item.getViewId(), ignored -> new HashSet<>())
                            .add(item.getCommandId()));
            commandMapper.selectList(new LambdaQueryWrapper<CommandRule>()
                            .in(CommandRule::getTargetViewId, ids))
                    .forEach(item -> references.computeIfAbsent(item.getTargetViewId(), ignored -> new HashSet<>())
                            .add(item.getId()));
        }
        List<ViewResponse> records = page.getRecords().stream()
                .map(view -> toResponse(view, references.getOrDefault(view.getId(), Set.of()).size()))
                .toList();
        return PageResponse.of(page, records);
    }

    public List<OptionItem> options() {
        return viewMapper.selectList(new LambdaQueryWrapper<ViewDefinition>().orderByAsc(ViewDefinition::getName)).stream()
                .map(item -> new OptionItem(item.getId(), item.getName())).toList();
    }

    public ViewResponse create(NameRequest request) {
        ViewDefinition view = new ViewDefinition();
        view.setName(request.name().trim());
        view.setCreatedBy(AuditUtils.currentUsername());
        view.setUpdatedBy(view.getCreatedBy());
        view.setCreatedAt(LocalDateTime.now());
        view.setUpdatedAt(view.getCreatedAt());
        viewMapper.insert(view);
        return toResponse(view, 0);
    }

    public ViewResponse update(Long id, NameRequest request) {
        ViewDefinition view = requireView(id);
        view.setName(request.name().trim());
        view.setUpdatedBy(AuditUtils.currentUsername());
        view.setUpdatedAt(LocalDateTime.now());
        viewMapper.updateById(view);
        return toResponse(view, countReferences(id));
    }

    public void delete(Long id) {
        requireView(id);
        if (countReferences(id) > 0) {
            throw new BusinessException(409, "该视图已被命令引用，不能删除");
        }
        viewMapper.deleteById(id);
    }

    public ViewDefinition requireView(Long id) {
        ViewDefinition view = viewMapper.selectById(id);
        if (view == null) {
            throw new BusinessException(404, "视图不存在");
        }
        return view;
    }

    private long countReferences(Long id) {
        Set<Long> commandIds = currentViewMapper.selectList(
                        new LambdaQueryWrapper<CommandCurrentView>().eq(CommandCurrentView::getViewId, id))
                .stream().map(CommandCurrentView::getCommandId).collect(Collectors.toSet());
        commandIds.addAll(commandMapper.selectList(
                        new LambdaQueryWrapper<CommandRule>().eq(CommandRule::getTargetViewId, id))
                .stream().map(CommandRule::getId).toList());
        return commandIds.size();
    }

    private ViewResponse toResponse(ViewDefinition view, long count) {
        return new ViewResponse(
                view.getId(), view.getName(), count, view.getCreatedBy(), view.getUpdatedBy(),
                view.getCreatedAt(), view.getUpdatedAt());
    }
}
