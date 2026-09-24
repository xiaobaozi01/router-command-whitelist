package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.whitelist.dto.DataMigrationSummary;
import com.example.whitelist.service.DataMigrationService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:datamigration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class DataMigrationServiceTest {
    @Autowired
    private DataMigrationService dataMigrationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void exportsGitFriendlyShardsAndRestoresAllBusinessDataWithoutChangingUsers() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 9, 24, 1, 30);
        Timestamp timestamp = Timestamp.valueOf(time);
        jdbcTemplate.update("""
                INSERT INTO app_user
                (id, username, display_name, password_hash, role_name, created_at, updated_at)
                VALUES (7, 'local-user', '本地用户', 'hash', 'USER', ?, ?)
                """, timestamp, timestamp);
        jdbcTemplate.update("""
                INSERT INTO regex_fragment
                (id, name, description, pattern_text, is_common, created_by, updated_by, created_at, updated_at)
                VALUES (11, 'INTEGER', '整数', '[0-9]+', TRUE, 'admin', 'admin', ?, ?)
                """, timestamp, timestamp);
        jdbcTemplate.update("""
                INSERT INTO scene (id, name, created_by, updated_by, created_at, updated_at)
                VALUES (21, '日常巡检', 'admin', 'admin', ?, ?)
                """, timestamp, timestamp);
        jdbcTemplate.update("""
                INSERT INTO view_definition (id, name, display_order, created_by, updated_by, created_at, updated_at)
                VALUES (31, '用户视图', 80, 'admin', 'admin', ?, ?)
                """, timestamp, timestamp);
        jdbcTemplate.update("""
                INSERT INTO command_rule
                (id, expression_html, expression_text, description, regex_template, match_start, match_end,
                 target_view_id, created_by, updated_by, created_at, updated_at)
                VALUES (1001, '<p>display 1</p>', 'display 1', '查看数据', 'display ${INTEGER}', TRUE, TRUE,
                        31, 'admin', 'admin', ?, ?)
                """, timestamp, timestamp);
        jdbcTemplate.update("INSERT INTO command_scene (command_id, scene_id) VALUES (1001, 21)");
        jdbcTemplate.update("INSERT INTO command_current_view (command_id, view_id) VALUES (1001, 31)");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        dataMigrationService.exportTo(output);
        byte[] archive = output.toByteArray();
        Map<String, String> files = unzip(archive);

        assertThat(files).containsKeys(
                "asset-data/manifest.json",
                "asset-data/regex-fragments.jsonl",
                "asset-data/scenes.jsonl",
                "asset-data/views.jsonl",
                "asset-data/commands/index.json",
                "asset-data/commands/001000-001999.jsonl");
        assertThat(files.get("asset-data/commands/index.json")).contains("001000-001999.jsonl");
        assertThat(files.get("asset-data/commands/001000-001999.jsonl"))
                .contains("\"id\":1001")
                .contains("\"sceneIds\":[21]")
                .contains("\"currentViewIds\":[31]");
        assertThat(files.get("asset-data/views.jsonl")).contains("\"displayOrder\":80");
        assertThat(files.values()).noneMatch(content -> content.contains("local-user"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "asset-data.zip", "application/zip", archive);
        DataMigrationSummary validated = dataMigrationService.validate(file);
        assertThat(validated).isEqualTo(new DataMigrationSummary(1, 1, 1, 1, 1, 1));

        jdbcTemplate.update("UPDATE scene SET name = '被修改的场景' WHERE id = 21");
        DataMigrationSummary imported = dataMigrationService.importData(file);

        assertThat(imported).isEqualTo(validated);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM scene WHERE id = 21", String.class))
                .isEqualTo("日常巡检");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT display_order FROM view_definition WHERE id = 31", Integer.class)).isEqualTo(80);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM command_rule", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM command_scene", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM command_current_view", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT username FROM app_user WHERE id = 7", String.class))
                .isEqualTo("local-user");

        jdbcTemplate.update("DELETE FROM command_current_view");
        jdbcTemplate.update("DELETE FROM command_scene");
        jdbcTemplate.update("DELETE FROM command_rule");
        jdbcTemplate.update("DELETE FROM regex_fragment");
        jdbcTemplate.update("DELETE FROM scene");
        jdbcTemplate.update("DELETE FROM view_definition");
        ByteArrayOutputStream emptyOutput = new ByteArrayOutputStream();
        dataMigrationService.exportTo(emptyOutput);
        Map<String, String> emptyFiles = unzip(emptyOutput.toByteArray());
        assertThat(emptyFiles).containsKey("asset-data/commands/index.json");
        assertThat(emptyFiles.get("asset-data/commands/index.json")).contains("\"shards\" : [ ]");
        assertThat(emptyFiles.keySet()).noneMatch(name -> name.matches("asset-data/commands/[0-9].*[.]jsonl"));
    }

    private Map<String, String> unzip(byte[] archive) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return files;
    }
}
