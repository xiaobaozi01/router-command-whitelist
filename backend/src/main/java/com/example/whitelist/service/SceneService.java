package com.example.whitelist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.whitelist.common.BusinessException;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.NameRequest;
import com.example.whitelist.dto.OptionItem;
import com.example.whitelist.dto.SceneResponse;
import com.example.whitelist.entity.CommandScene;
import com.example.whitelist.entity.Scene;
import com.example.whitelist.mapper.CommandSceneMapper;
import com.example.whitelist.mapper.SceneMapper;
import com.example.whitelist.util.AuditUtils;
import com.example.whitelist.util.TimeSort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SceneService {
    private final SceneMapper sceneMapper;
    private final CommandSceneMapper commandSceneMapper;

    public SceneService(SceneMapper sceneMapper, CommandSceneMapper commandSceneMapper) {
        this.sceneMapper = sceneMapper;
        this.commandSceneMapper = commandSceneMapper;
    }

    public PageResponse<SceneResponse> page(long current, long size, String keyword, String sortField, String sortOrder) {
        TimeSort timeSort = TimeSort.parse(sortField, sortOrder);
        LambdaQueryWrapper<Scene> query = new LambdaQueryWrapper<Scene>()
                .like(keyword != null && !keyword.isBlank(), Scene::getName, keyword);
        if (timeSort.field() == TimeSort.Field.CREATED_AT) {
            query.orderBy(true, timeSort.ascending(), Scene::getCreatedAt);
        } else {
            query.orderBy(true, timeSort.ascending(), Scene::getUpdatedAt);
        }
        query.orderBy(true, timeSort.ascending(), Scene::getId);
        Page<Scene> page = sceneMapper.selectPage(Page.of(current, size), query);
        Set<Long> ids = page.getRecords().stream().map(Scene::getId).collect(Collectors.toSet());
        Map<Long, Long> counts = ids.isEmpty() ? Map.of() : commandSceneMapper.selectList(
                        new LambdaQueryWrapper<CommandScene>().in(CommandScene::getSceneId, ids))
                .stream().collect(Collectors.groupingBy(CommandScene::getSceneId, Collectors.counting()));
        List<SceneResponse> records = page.getRecords().stream()
                .map(scene -> toResponse(scene, counts.getOrDefault(scene.getId(), 0L)))
                .toList();
        return PageResponse.of(page, records);
    }

    public SceneResponse get(Long id) {
        Scene scene = requireScene(id);
        long count = commandSceneMapper.selectCount(
                new LambdaQueryWrapper<CommandScene>().eq(CommandScene::getSceneId, id));
        return toResponse(scene, count);
    }

    public List<OptionItem> options() {
        return sceneMapper.selectList(new LambdaQueryWrapper<Scene>().orderByAsc(Scene::getName)).stream()
                .map(item -> new OptionItem(item.getId(), item.getName())).toList();
    }

    public SceneResponse create(NameRequest request) {
        Scene scene = new Scene();
        scene.setName(request.name().trim());
        scene.setCreatedBy(AuditUtils.currentUsername());
        scene.setUpdatedBy(scene.getCreatedBy());
        scene.setCreatedAt(LocalDateTime.now());
        scene.setUpdatedAt(scene.getCreatedAt());
        sceneMapper.insert(scene);
        return toResponse(scene, 0);
    }

    public SceneResponse update(Long id, NameRequest request) {
        Scene scene = requireScene(id);
        scene.setName(request.name().trim());
        scene.setUpdatedBy(AuditUtils.currentUsername());
        scene.setUpdatedAt(LocalDateTime.now());
        sceneMapper.updateById(scene);
        return get(id);
    }

    public void delete(Long id) {
        requireScene(id);
        if (commandSceneMapper.selectCount(new LambdaQueryWrapper<CommandScene>().eq(CommandScene::getSceneId, id)) > 0) {
            throw new BusinessException(409, "该场景已被命令引用，不能删除");
        }
        sceneMapper.deleteById(id);
    }

    public Scene requireScene(Long id) {
        Scene scene = sceneMapper.selectById(id);
        if (scene == null) {
            throw new BusinessException(404, "场景不存在");
        }
        return scene;
    }

    private SceneResponse toResponse(Scene scene, long count) {
        return new SceneResponse(
                scene.getId(), scene.getName(), count, scene.getCreatedBy(), scene.getUpdatedBy(),
                scene.getCreatedAt(), scene.getUpdatedAt());
    }
}
