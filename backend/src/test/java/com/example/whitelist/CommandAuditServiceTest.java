package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.whitelist.auth.AuthContext;
import com.example.whitelist.auth.AuthRole;
import com.example.whitelist.auth.CurrentUser;
import com.example.whitelist.common.BusinessException;
import com.example.whitelist.dto.CommandAuditEventResponse;
import com.example.whitelist.dto.CommandRequest;
import com.example.whitelist.dto.CommandResponse;
import com.example.whitelist.dto.RegexFragmentRequest;
import com.example.whitelist.entity.RegexFragment;
import com.example.whitelist.entity.Scene;
import com.example.whitelist.entity.ViewDefinition;
import com.example.whitelist.mapper.RegexFragmentMapper;
import com.example.whitelist.mapper.SceneMapper;
import com.example.whitelist.mapper.ViewDefinitionMapper;
import com.example.whitelist.service.CommandAuditService;
import com.example.whitelist.service.CommandService;
import com.example.whitelist.service.RegexFragmentService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:commandaudit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@Transactional
class CommandAuditServiceTest {
    @Autowired
    private CommandService commandService;
    @Autowired
    private CommandAuditService auditService;
    @Autowired
    private RegexFragmentService fragmentService;
    @Autowired
    private RegexFragmentMapper fragmentMapper;
    @Autowired
    private SceneMapper sceneMapper;
    @Autowired
    private ViewDefinitionMapper viewMapper;

    private Long sceneId;
    private Long viewId;

    @BeforeEach
    void setUp() {
        AuthContext.set(new CurrentUser(7L, "auditor", "审计员", AuthRole.DEVELOPER, true));
        Scene scene = new Scene();
        scene.setName("巡检场景");
        sceneMapper.insert(scene);
        sceneId = scene.getId();
        ViewDefinition view = new ViewDefinition();
        view.setName("系统视图");
        viewMapper.insert(view);
        viewId = view.getId();
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @Test
    void recordsCriticalChangesButIgnoresDescriptionOnlyChangesAndRejectsStaleVersion() {
        CommandResponse created = commandService.create(request(
                "display version", "原描述", "display version", null, null));
        List<CommandAuditEventResponse> createdEvents = auditService.list(created.id());
        assertThat(createdEvents).hasSize(1);
        assertThat(createdEvents.getFirst().action()).isEqualTo("CREATE");
        assertThat(createdEvents.getFirst().actorUsername()).isEqualTo("auditor");

        assertThatThrownBy(() -> commandService.update(created.id(), request(
                "display device", "原描述", "display device", created.version(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须填写修改原因");
        assertThat(commandService.get(created.id()).expressionText()).isEqualTo("display version");
        assertThat(commandService.get(created.id()).version()).isEqualTo(created.version());
        assertThat(auditService.list(created.id())).hasSize(1);

        CommandResponse updated = commandService.update(created.id(), request(
                "display device", "原描述", "display device", created.version(), "适配新设备"));
        List<CommandAuditEventResponse> updatedEvents = auditService.list(created.id());
        assertThat(updatedEvents).hasSize(2);
        assertThat(updatedEvents.getFirst().action()).isEqualTo("UPDATE");
        assertThat(updatedEvents.getFirst().changedFields())
                .contains("expression", "regexTemplate", "expandedRegex")
                .doesNotContain("description");
        assertThat(updatedEvents.getFirst().changeReason()).isEqualTo("适配新设备");
        assertThat(updated.version()).isEqualTo(created.version() + 1);

        CommandResponse descriptionOnly = commandService.update(created.id(), request(
                "display device", "只修改描述", "display device", updated.version(), null));
        assertThat(auditService.list(created.id())).hasSize(2);

        assertThatThrownBy(() -> commandService.update(created.id(), request(
                "display stale", "描述", "display stale", updated.version(), "过期修改")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他人修改");

        commandService.delete(created.id(), descriptionOnly.version(), "命令下线");
        List<CommandAuditEventResponse> deletedEvents = auditService.list(created.id());
        assertThat(deletedEvents).hasSize(3);
        assertThat(deletedEvents.getFirst().action()).isEqualTo("DELETE");
        assertThat(deletedEvents.getFirst().afterSnapshot()).isNull();
    }

    @Test
    void recordsEffectiveRegexChangesCausedByFragmentUpdates() {
        RegexFragment fragment = new RegexFragment();
        fragment.setName("NUMBER");
        fragment.setDescription("数字");
        fragment.setPattern("[0-9]+");
        fragment.setIsCommon(true);
        fragmentMapper.insert(fragment);

        CommandResponse command = commandService.create(request(
                "display 1", "描述", "display ${NUMBER}", null, null));
        fragmentService.update(fragment.getId(), new RegexFragmentRequest(
                "NUMBER", "数字", "[0-9]{1,3}", true));

        CommandAuditEventResponse impact = auditService.list(command.id()).getFirst();
        assertThat(impact.action()).isEqualTo("FRAGMENT_IMPACT");
        assertThat(impact.changedFields()).containsExactly("expandedRegex");
        assertThat(impact.beforeSnapshot().expandedRegex()).contains("[0-9]+");
        assertThat(impact.afterSnapshot().expandedRegex()).contains("[0-9]{1,3}");
    }

    private CommandRequest request(
            String expression,
            String description,
            String regex,
            Long version,
            String reason
    ) {
        return new CommandRequest(
                "<p>" + expression + "</p>", description, regex, true, true,
                List.of(viewId), viewId, List.of(sceneId), version, reason);
    }
}
