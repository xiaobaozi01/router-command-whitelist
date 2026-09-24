package com.example.whitelist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.whitelist.common.BusinessException;
import com.example.whitelist.dto.DataMigrationData.CommandData;
import com.example.whitelist.dto.DataMigrationData.CommandIndex;
import com.example.whitelist.dto.DataMigrationData.Manifest;
import com.example.whitelist.dto.DataMigrationData.RegexFragmentData;
import com.example.whitelist.dto.DataMigrationData.SceneData;
import com.example.whitelist.dto.DataMigrationData.ViewData;
import com.example.whitelist.dto.DataMigrationSummary;
import com.example.whitelist.entity.CommandCurrentView;
import com.example.whitelist.entity.CommandRule;
import com.example.whitelist.entity.CommandScene;
import com.example.whitelist.entity.RegexFragment;
import com.example.whitelist.entity.Scene;
import com.example.whitelist.entity.ViewDefinition;
import com.example.whitelist.mapper.CommandCurrentViewMapper;
import com.example.whitelist.mapper.CommandRuleMapper;
import com.example.whitelist.mapper.CommandSceneMapper;
import com.example.whitelist.mapper.RegexFragmentMapper;
import com.example.whitelist.mapper.SceneMapper;
import com.example.whitelist.mapper.ViewDefinitionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DataMigrationService {
    private static final String PACKAGE_TYPE = "router-command-whitelist-assets";
    private static final String ROOT = "asset-data/";
    private static final String MANIFEST_FILE = ROOT + "manifest.json";
    private static final String FRAGMENT_FILE = ROOT + "regex-fragments.jsonl";
    private static final String SCENE_FILE = ROOT + "scenes.jsonl";
    private static final String VIEW_FILE = ROOT + "views.jsonl";
    private static final String COMMAND_FOLDER = ROOT + "commands/";
    private static final String COMMAND_INDEX_FILE = COMMAND_FOLDER + "index.json";
    private static final int FORMAT_VERSION = 1;
    private static final int COMMAND_SHARD_SIZE = 1000;
    private static final int BATCH_SIZE = 500;
    private static final Pattern COMMAND_FILE_PATTERN = Pattern.compile(
            "asset-data/commands/([0-9]{6,})-([0-9]{6,})[.]jsonl");
    private static final Pattern FRAGMENT_NAME_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern REFERENCE_PATTERN = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");

    private final ObjectMapper objectMapper;
    private final RegexFragmentMapper fragmentMapper;
    private final SceneMapper sceneMapper;
    private final ViewDefinitionMapper viewMapper;
    private final CommandRuleMapper commandMapper;
    private final CommandSceneMapper commandSceneMapper;
    private final CommandCurrentViewMapper commandCurrentViewMapper;
    private final JdbcTemplate jdbcTemplate;

    public DataMigrationService(
            ObjectMapper objectMapper,
            RegexFragmentMapper fragmentMapper,
            SceneMapper sceneMapper,
            ViewDefinitionMapper viewMapper,
            CommandRuleMapper commandMapper,
            CommandSceneMapper commandSceneMapper,
            CommandCurrentViewMapper commandCurrentViewMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.objectMapper = objectMapper;
        this.fragmentMapper = fragmentMapper;
        this.sceneMapper = sceneMapper;
        this.viewMapper = viewMapper;
        this.commandMapper = commandMapper;
        this.commandSceneMapper = commandSceneMapper;
        this.commandCurrentViewMapper = commandCurrentViewMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public void exportTo(OutputStream output) throws IOException {
        PackageData data = loadCurrentData();
        Manifest manifest = manifest(data);
        TreeMap<Long, List<CommandData>> commandShards = shardCommands(data.commands);

        ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
        writeJson(zip, MANIFEST_FILE, manifest);
        writeJsonLines(zip, FRAGMENT_FILE, data.fragments);
        writeJsonLines(zip, SCENE_FILE, data.scenes);
        writeJsonLines(zip, VIEW_FILE, data.views);
        writeJson(zip, COMMAND_INDEX_FILE, commandIndex(commandShards));
        for (Map.Entry<Long, List<CommandData>> shard : commandShards.entrySet()) {
            long start = shard.getKey();
            long end = start + COMMAND_SHARD_SIZE - 1;
            writeJsonLines(zip, COMMAND_FOLDER + "%06d-%06d.jsonl".formatted(start, end), shard.getValue());
        }
        zip.finish();
        zip.flush();
    }

    @Transactional(readOnly = true)
    public DataMigrationSummary writeSnapshot(Path root) throws IOException {
        PackageData data = loadCurrentData();
        TreeMap<Long, List<CommandData>> commandShards = shardCommands(data.commands);
        Path commandsDirectory = root.resolve("commands");
        Files.createDirectories(commandsDirectory);
        writeJson(root.resolve("manifest.json"), manifest(data));
        writeJsonLines(root.resolve("regex-fragments.jsonl"), data.fragments);
        writeJsonLines(root.resolve("scenes.jsonl"), data.scenes);
        writeJsonLines(root.resolve("views.jsonl"), data.views);
        writeJson(commandsDirectory.resolve("index.json"), commandIndex(commandShards));
        for (Map.Entry<Long, List<CommandData>> shard : commandShards.entrySet()) {
            long start = shard.getKey();
            long end = start + COMMAND_SHARD_SIZE - 1;
            writeJsonLines(
                    commandsDirectory.resolve("%06d-%06d.jsonl".formatted(start, end)),
                    shard.getValue());
        }
        return summary(data);
    }

    private PackageData loadCurrentData() {
        List<RegexFragmentData> fragments = fragmentMapper.selectList(
                        new LambdaQueryWrapper<RegexFragment>().orderByAsc(RegexFragment::getId))
                .stream().map(this::toData).toList();
        List<SceneData> scenes = sceneMapper.selectList(
                        new LambdaQueryWrapper<Scene>().orderByAsc(Scene::getId))
                .stream().map(this::toData).toList();
        List<ViewData> views = viewMapper.selectList(
                        new LambdaQueryWrapper<ViewDefinition>().orderByAsc(ViewDefinition::getId))
                .stream().map(this::toData).toList();

        Map<Long, List<Long>> sceneIds = relationMap(
                commandSceneMapper.selectList(new QueryWrapper<CommandScene>()
                        .orderByAsc("command_id", "scene_id")),
                CommandScene::getCommandId,
                CommandScene::getSceneId);
        Map<Long, List<Long>> viewIds = relationMap(
                commandCurrentViewMapper.selectList(new QueryWrapper<CommandCurrentView>()
                        .orderByAsc("command_id", "view_id")),
                CommandCurrentView::getCommandId,
                CommandCurrentView::getViewId);
        List<CommandData> commands = commandMapper.selectList(
                        new LambdaQueryWrapper<CommandRule>().orderByAsc(CommandRule::getId))
                .stream()
                .map(command -> toData(
                        command,
                        viewIds.getOrDefault(command.getId(), List.of()),
                        sceneIds.getOrDefault(command.getId(), List.of())))
                .toList();

        PackageData data = new PackageData();
        data.fragments.addAll(fragments);
        data.scenes.addAll(scenes);
        data.views.addAll(views);
        data.commands.addAll(commands);
        return data;
    }

    public DataMigrationSummary validate(MultipartFile file) {
        return summary(readPackage(file));
    }

    @Transactional
    public DataMigrationSummary importData(MultipartFile file) {
        PackageData data = readPackage(file);
        try {
            replaceData(data);
        } catch (DataAccessException exception) {
            throw new BusinessException(400, "数据导入失败，数据包内容与当前数据库结构不兼容");
        }
        return summary(data);
    }

    private PackageData readPackage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要导入的 ZIP 数据包");
        }
        try (InputStream input = file.getInputStream()) {
            return readPackage(input);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(400, "无法读取上传的数据包");
        }
    }

    private PackageData readPackage(InputStream input) {
        PackageData data = new PackageData();
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                validateEntryName(name);
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                if (!entries.add(name)) {
                    throw invalid("数据包中存在重复文件：" + name);
                }
                if (MANIFEST_FILE.equals(name)) {
                    data.manifest = readManifest(zip);
                } else if (FRAGMENT_FILE.equals(name)) {
                    readJsonLines(zip, name, RegexFragmentData.class, data.fragments::add);
                } else if (SCENE_FILE.equals(name)) {
                    readJsonLines(zip, name, SceneData.class, data.scenes::add);
                } else if (VIEW_FILE.equals(name)) {
                    readJsonLines(zip, name, ViewData.class, data.views::add);
                } else if (COMMAND_INDEX_FILE.equals(name)) {
                    data.commandIndex = readCommandIndex(zip);
                } else {
                    Matcher matcher = COMMAND_FILE_PATTERN.matcher(name);
                    if (!matcher.matches()) {
                        throw invalid("数据包中包含不支持的文件：" + name);
                    }
                    long start = Long.parseLong(matcher.group(1));
                    long end = Long.parseLong(matcher.group(2));
                    if (start % COMMAND_SHARD_SIZE != 0 || end != start + COMMAND_SHARD_SIZE - 1) {
                        throw invalid("命令分片文件名不正确：" + name);
                    }
                    data.commandShardFiles.add(name);
                    readJsonLines(zip, name, CommandData.class, command -> {
                        if (command.id() == null || command.id() < start || command.id() > end) {
                            throw invalid("命令编号不属于对应分片：" + name);
                        }
                        data.commands.add(command);
                    });
                }
                zip.closeEntry();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | NumberFormatException exception) {
            throw invalid("ZIP 数据包格式不正确或已经损坏");
        }

        require(entries.contains(MANIFEST_FILE), "数据包缺少 manifest.json");
        require(entries.contains(FRAGMENT_FILE), "数据包缺少 regex-fragments.jsonl");
        require(entries.contains(SCENE_FILE), "数据包缺少 scenes.jsonl");
        require(entries.contains(VIEW_FILE), "数据包缺少 views.jsonl");
        require(entries.contains(COMMAND_INDEX_FILE), "数据包缺少 commands/index.json");
        validatePackage(data);
        return data;
    }

    private void validatePackage(PackageData data) {
        Manifest manifest = data.manifest;
        require(manifest != null, "数据包清单无效");
        require(PACKAGE_TYPE.equals(manifest.type()), "数据包类型不受支持");
        require(Integer.valueOf(FORMAT_VERSION).equals(manifest.formatVersion()), "数据包版本不受支持");
        require(Integer.valueOf(COMMAND_SHARD_SIZE).equals(manifest.commandShardSize()), "命令分片大小不受支持");
        require(manifest.counts() != null, "数据包清单缺少数据数量");
        validateCommandIndex(data);

        Set<Long> fragmentIds = uniqueIds(data.fragments, RegexFragmentData::id, "正则片段");
        Set<Long> sceneIds = uniqueIds(data.scenes, SceneData::id, "场景");
        Set<Long> viewIds = uniqueIds(data.views, ViewData::id, "视图");
        uniqueIds(data.commands, CommandData::id, "命令");
        Set<String> fragmentNames = new HashSet<>();
        Set<String> sceneNames = new HashSet<>();
        Set<String> viewNames = new HashSet<>();

        for (RegexFragmentData fragment : data.fragments) {
            require(fragmentIds.contains(fragment.id()), "正则片段编号无效");
            requireText(fragment.name(), "正则片段名称", 64, false);
            require(FRAGMENT_NAME_PATTERN.matcher(fragment.name()).matches(),
                    "正则片段名称格式不正确：" + fragment.name());
            require(fragmentNames.add(fragment.name()), "正则片段名称重复：" + fragment.name());
            requireText(fragment.description(), "正则片段描述", 500, false);
            requireText(fragment.pattern(), "正则片段内容", Integer.MAX_VALUE, false);
            require(fragment.common() != null, "正则片段是否常用不能为空");
            requireAudit(fragment.createdBy(), fragment.updatedBy(), fragment.createdAt(), fragment.updatedAt());
            compile(fragment.pattern(), "正则片段语法错误：" + fragment.name());
            require(!fragment.pattern().contains("${"), "正则片段不能引用其他片段：" + fragment.name());
        }
        for (SceneData scene : data.scenes) {
            require(sceneIds.contains(scene.id()), "场景编号无效");
            requireText(scene.name(), "场景名称", 100, false);
            require(sceneNames.add(scene.name()), "场景名称重复：" + scene.name());
            requireAudit(scene.createdBy(), scene.updatedBy(), scene.createdAt(), scene.updatedAt());
        }
        for (ViewData view : data.views) {
            require(viewIds.contains(view.id()), "视图编号无效");
            requireText(view.name(), "视图名称", 100, false);
            require(view.displayOrder() == null || view.displayOrder() >= 0, "视图展示顺序不能小于0");
            require(viewNames.add(view.name()), "视图名称重复：" + view.name());
            requireAudit(view.createdBy(), view.updatedBy(), view.createdAt(), view.updatedAt());
        }

        Map<String, String> patterns = new HashMap<>();
        for (RegexFragmentData fragment : data.fragments) {
            patterns.put(fragment.name(), fragment.pattern());
        }
        for (CommandData command : data.commands) {
            requireText(command.expressionHtml(), "命令行表达式", Integer.MAX_VALUE, false);
            requireText(command.expressionText(), "命令行纯文本", 1000, false);
            requireText(command.description(), "命令行描述", 1000, true);
            requireText(command.regexTemplate(), "命令行正则", Integer.MAX_VALUE, false);
            require(command.matchStart() != null && command.matchEnd() != null, "命令匹配边界不能为空");
            require(command.targetViewId() == null || viewIds.contains(command.targetViewId()),
                    "命令引用了不存在的进入视图：" + command.id());
            validateRelationIds(command.currentViewIds(), viewIds, "命令所在视图", command.id(), false);
            validateRelationIds(command.sceneIds(), sceneIds, "命令所属场景", command.id(), false);
            requireAudit(command.createdBy(), command.updatedBy(), command.createdAt(), command.updatedAt());
            validateCommandRegex(command, patterns);
        }

        DataMigrationSummary actual = summary(data);
        require(manifest.counts().equals(actual), "数据包清单中的数量与实际数据不一致");
    }

    private void validateCommandRegex(CommandData command, Map<String, String> patterns) {
        Matcher matcher = REFERENCE_PATTERN.matcher(command.regexTemplate());
        StringBuffer expanded = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String pattern = patterns.get(name);
            require(pattern != null, "命令 " + command.id() + " 引用了不存在的正则片段：${" + name + "}");
            matcher.appendReplacement(expanded, Matcher.quoteReplacement("(?:" + pattern + ")"));
        }
        matcher.appendTail(expanded);
        require(expanded.indexOf("${") < 0, "命令 " + command.id() + " 的正则片段引用格式错误");
        String finalRegex = (command.matchStart() ? "^" : "")
                + "(?:" + expanded + ")"
                + (command.matchEnd() ? "$" : "");
        compile(finalRegex, "命令正则语法错误：" + command.id());
    }

    private void replaceData(PackageData data) {
        jdbcTemplate.update("DELETE FROM command_current_view");
        jdbcTemplate.update("DELETE FROM command_scene");
        jdbcTemplate.update("DELETE FROM command_rule");
        jdbcTemplate.update("DELETE FROM regex_fragment");
        jdbcTemplate.update("DELETE FROM scene");
        jdbcTemplate.update("DELETE FROM view_definition");

        jdbcTemplate.batchUpdate(
                "INSERT INTO regex_fragment "
                        + "(id, name, description, pattern_text, is_common, created_by, updated_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                data.fragments, BATCH_SIZE, (statement, item) -> {
                    statement.setLong(1, item.id());
                    statement.setString(2, item.name());
                    statement.setString(3, item.description());
                    statement.setString(4, item.pattern());
                    statement.setBoolean(5, item.common());
                    statement.setString(6, item.createdBy());
                    statement.setString(7, item.updatedBy());
                    statement.setTimestamp(8, timestamp(item.createdAt()));
                    statement.setTimestamp(9, timestamp(item.updatedAt()));
                });
        jdbcTemplate.batchUpdate(
                "INSERT INTO scene (id, name, created_by, updated_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                data.scenes, BATCH_SIZE, (statement, item) -> {
                    statement.setLong(1, item.id());
                    statement.setString(2, item.name());
                    statement.setString(3, item.createdBy());
                    statement.setString(4, item.updatedBy());
                    statement.setTimestamp(5, timestamp(item.createdAt()));
                    statement.setTimestamp(6, timestamp(item.updatedAt()));
                });
        jdbcTemplate.batchUpdate(
                "INSERT INTO view_definition "
                        + "(id, name, display_order, created_by, updated_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                data.views, BATCH_SIZE, (statement, item) -> {
                    statement.setLong(1, item.id());
                    statement.setString(2, item.name());
                    statement.setInt(3, item.displayOrder() == null ? 0 : item.displayOrder());
                    statement.setString(4, item.createdBy());
                    statement.setString(5, item.updatedBy());
                    statement.setTimestamp(6, timestamp(item.createdAt()));
                    statement.setTimestamp(7, timestamp(item.updatedAt()));
                });
        jdbcTemplate.batchUpdate(
                "INSERT INTO command_rule "
                        + "(id, expression_html, expression_text, description, regex_template, match_start, match_end, "
                        + "target_view_id, created_by, updated_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                data.commands, BATCH_SIZE, (statement, item) -> {
                    statement.setLong(1, item.id());
                    statement.setString(2, item.expressionHtml());
                    statement.setString(3, item.expressionText());
                    statement.setString(4, item.description());
                    statement.setString(5, item.regexTemplate());
                    statement.setBoolean(6, item.matchStart());
                    statement.setBoolean(7, item.matchEnd());
                    if (item.targetViewId() == null) {
                        statement.setNull(8, Types.BIGINT);
                    } else {
                        statement.setLong(8, item.targetViewId());
                    }
                    statement.setString(9, item.createdBy());
                    statement.setString(10, item.updatedBy());
                    statement.setTimestamp(11, timestamp(item.createdAt()));
                    statement.setTimestamp(12, timestamp(item.updatedAt()));
                });

        List<CommandScene> commandScenes = new ArrayList<>();
        List<CommandCurrentView> commandViews = new ArrayList<>();
        for (CommandData command : data.commands) {
            command.sceneIds().forEach(sceneId -> commandScenes.add(new CommandScene(command.id(), sceneId)));
            command.currentViewIds().forEach(viewId -> commandViews.add(new CommandCurrentView(command.id(), viewId)));
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO command_scene (command_id, scene_id) VALUES (?, ?)",
                commandScenes, BATCH_SIZE, (statement, item) -> {
                    statement.setLong(1, item.getCommandId());
                    statement.setLong(2, item.getSceneId());
                });
        jdbcTemplate.batchUpdate(
                "INSERT INTO command_current_view (command_id, view_id) VALUES (?, ?)",
                commandViews, BATCH_SIZE, (statement, item) -> {
                    statement.setLong(1, item.getCommandId());
                    statement.setLong(2, item.getViewId());
                });
    }

    private Manifest readManifest(ZipInputStream zip) throws IOException {
        byte[] content = zip.readAllBytes();
        try {
            return objectMapper.readValue(content, Manifest.class);
        } catch (JsonProcessingException exception) {
            throw invalid("manifest.json 格式不正确");
        }
    }

    private CommandIndex readCommandIndex(ZipInputStream zip) throws IOException {
        byte[] content = zip.readAllBytes();
        try {
            return objectMapper.readValue(content, CommandIndex.class);
        } catch (JsonProcessingException exception) {
            throw invalid("commands/index.json 格式不正确");
        }
    }

    private void validateCommandIndex(PackageData data) {
        require(data.commandIndex != null && data.commandIndex.shards() != null, "命令分片索引无效");
        Set<String> expectedFiles = new HashSet<>();
        for (String shard : data.commandIndex.shards()) {
            require(shard != null && !shard.contains("/") && !shard.contains("\\"), "命令分片索引中的文件名无效");
            String fullName = COMMAND_FOLDER + shard;
            require(COMMAND_FILE_PATTERN.matcher(fullName).matches(), "命令分片索引中的文件名无效：" + shard);
            require(expectedFiles.add(fullName), "命令分片索引存在重复文件：" + shard);
        }
        require(expectedFiles.equals(data.commandShardFiles), "命令分片索引与数据包内的文件不一致");
    }

    private <T> void readJsonLines(
            ZipInputStream zip,
            String fileName,
            Class<T> type,
            Consumer<T> consumer
    ) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isBlank()) {
                continue;
            }
            try {
                T value = objectMapper.readValue(line, type);
                if (value == null) {
                    throw invalid(fileName + " 第 " + lineNumber + " 行不能为 null");
                }
                consumer.accept(value);
            } catch (JsonProcessingException exception) {
                throw invalid(fileName + " 第 " + lineNumber + " 行格式不正确");
            }
        }
    }

    private void writeJson(ZipOutputStream zip, String name, Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        zip.write('\n');
        zip.closeEntry();
    }

    private void writeJsonLines(ZipOutputStream zip, String name, List<?> values) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        for (Object value : values) {
            zip.write(objectMapper.writeValueAsBytes(value));
            zip.write('\n');
        }
        zip.closeEntry();
    }

    private void writeJson(Path path, Object value) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) {
            output.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
            output.write('\n');
        }
    }

    private void writeJsonLines(Path path, List<?> values) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) {
            for (Object value : values) {
                output.write(objectMapper.writeValueAsBytes(value));
                output.write('\n');
            }
        }
    }

    private Manifest manifest(PackageData data) {
        return new Manifest(PACKAGE_TYPE, FORMAT_VERSION, COMMAND_SHARD_SIZE, summary(data));
    }

    private CommandIndex commandIndex(TreeMap<Long, List<CommandData>> shards) {
        List<String> shardFiles = shards.keySet().stream()
                .map(start -> "%06d-%06d.jsonl".formatted(start, start + COMMAND_SHARD_SIZE - 1))
                .toList();
        return new CommandIndex(shardFiles);
    }

    private TreeMap<Long, List<CommandData>> shardCommands(List<CommandData> commands) {
        TreeMap<Long, List<CommandData>> shards = new TreeMap<>();
        for (CommandData command : commands) {
            long start = Math.floorDiv(command.id(), COMMAND_SHARD_SIZE) * COMMAND_SHARD_SIZE;
            shards.computeIfAbsent(start, ignored -> new ArrayList<>()).add(command);
        }
        return shards;
    }

    private <T> Map<Long, List<Long>> relationMap(
            List<T> relations,
            Function<T, Long> commandId,
            Function<T, Long> valueId
    ) {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        for (T relation : relations) {
            result.computeIfAbsent(commandId.apply(relation), ignored -> new ArrayList<>())
                    .add(valueId.apply(relation));
        }
        return result;
    }

    private RegexFragmentData toData(RegexFragment fragment) {
        return new RegexFragmentData(
                fragment.getId(), fragment.getName(), fragment.getDescription(), fragment.getPattern(),
                fragment.getIsCommon(), fragment.getCreatedBy(), fragment.getUpdatedBy(),
                fragment.getCreatedAt(), fragment.getUpdatedAt());
    }

    private SceneData toData(Scene scene) {
        return new SceneData(
                scene.getId(), scene.getName(), scene.getCreatedBy(), scene.getUpdatedBy(),
                scene.getCreatedAt(), scene.getUpdatedAt());
    }

    private ViewData toData(ViewDefinition view) {
        return new ViewData(
                view.getId(), view.getName(), view.getDisplayOrder(), view.getCreatedBy(), view.getUpdatedBy(),
                view.getCreatedAt(), view.getUpdatedAt());
    }

    private CommandData toData(CommandRule command, List<Long> viewIds, List<Long> sceneIds) {
        return new CommandData(
                command.getId(), command.getExpressionHtml(), command.getExpressionText(), command.getDescription(),
                command.getRegexTemplate(), command.getMatchStart(), command.getMatchEnd(), command.getTargetViewId(),
                List.copyOf(viewIds), List.copyOf(sceneIds), command.getCreatedBy(), command.getUpdatedBy(),
                command.getCreatedAt(), command.getUpdatedAt());
    }

    private DataMigrationSummary summary(PackageData data) {
        long sceneRelations = data.commands.stream()
                .map(CommandData::sceneIds).filter(value -> value != null).mapToLong(List::size).sum();
        long viewRelations = data.commands.stream()
                .map(CommandData::currentViewIds).filter(value -> value != null).mapToLong(List::size).sum();
        return new DataMigrationSummary(
                data.fragments.size(), data.scenes.size(), data.views.size(), data.commands.size(),
                sceneRelations, viewRelations);
    }

    private <T> Set<Long> uniqueIds(List<T> values, Function<T, Long> idExtractor, String label) {
        Set<Long> ids = new HashSet<>();
        for (T value : values) {
            Long id = idExtractor.apply(value);
            require(id != null && id > 0, label + "编号必须是正整数");
            require(ids.add(id), label + "编号重复：" + id);
        }
        return ids;
    }

    private void validateRelationIds(
            List<Long> values,
            Set<Long> available,
            String label,
            Long commandId,
            boolean allowEmpty
    ) {
        require(values != null && (allowEmpty || !values.isEmpty()), label + "不能为空：命令 " + commandId);
        Set<Long> unique = new HashSet<>();
        for (Long value : values) {
            require(value != null && available.contains(value), label + "引用不存在：命令 " + commandId);
            require(unique.add(value), label + "存在重复值：命令 " + commandId);
        }
    }

    private void requireAudit(
            String createdBy,
            String updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        requireText(createdBy, "创建人", 64, false);
        requireText(updatedBy, "修改人", 64, false);
        require(createdAt != null && updatedAt != null, "创建时间和修改时间不能为空");
    }

    private void requireText(String value, String label, int maxLength, boolean allowEmpty) {
        require(value != null, label + "不能为空");
        require(allowEmpty || !value.isBlank(), label + "不能为空");
        require(value.length() <= maxLength, label + "长度超过限制");
    }

    private void compile(String expression, String label) {
        try {
            Pattern.compile(expression);
        } catch (PatternSyntaxException exception) {
            throw invalid(label + "：" + exception.getDescription());
        }
    }

    private void validateEntryName(String name) {
        require(name != null && name.startsWith(ROOT) && !name.contains("..") && !name.contains("\\"),
                "数据包目录结构不正确");
    }

    private Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(400, message);
    }

    private static final class PackageData {
        private Manifest manifest;
        private CommandIndex commandIndex;
        private final Set<String> commandShardFiles = new HashSet<>();
        private final List<RegexFragmentData> fragments = new ArrayList<>();
        private final List<SceneData> scenes = new ArrayList<>();
        private final List<ViewData> views = new ArrayList<>();
        private final List<CommandData> commands = new ArrayList<>();
    }
}
