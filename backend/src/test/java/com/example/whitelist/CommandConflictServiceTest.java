package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.example.whitelist.service.CommandApprovalService;
import com.example.whitelist.service.CommandConflictService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:commandconflicts;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.ai.enabled=true",
        "app.ai.protocol=mock"
})
@Transactional
class CommandConflictServiceTest {
    @Autowired
    private CommandConflictService conflictService;
    @Autowired
    private CommandRuleMapper commandMapper;
    @Autowired
    private CommandCurrentViewMapper currentViewMapper;
    @Autowired
    private CommandSceneMapper commandSceneMapper;
    @Autowired
    private CommandApprovalRequestMapper approvalMapper;
    @Autowired
    private ViewDefinitionMapper viewMapper;
    @Autowired
    private SceneMapper sceneMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void reportsExactRegexWithDifferentTargetAsHighRiskConflict() {
        ViewDefinition current = insertView("系统视图");
        ViewDefinition existingTarget = insertView("接口视图");
        ViewDefinition newTarget = insertView("VLAN 视图");
        Scene scene = insertScene("接口管理");
        insertCommand("interface INTERFACE", "interface\\s+\\S+", current, existingTarget, scene);

        CommandConflictCheckResponse result = conflictService.check(request(
                "interface NAME", "interface\\s+\\S+", current.getId(), scene.getId(), newTarget.getId()));

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.results()).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo("EFFECTIVE");
            assertThat(item.relation()).isEqualTo("EXACT");
            assertThat(item.riskType()).isEqualTo("TARGET_VIEW_CONFLICT");
            assertThat(item.riskLevel()).isEqualTo("HIGH");
            assertThat(item.confidence()).isEqualTo("CONFIRMED");
        });
    }

    @Test
    void verifiesAiRecalledOverlapWithTheRealJavaRegexEngine() {
        ViewDefinition current = insertView("用户视图");
        Scene scene = insertScene("巡检");
        insertCommand("display version", "display\\s+version", current, null, scene);

        CommandConflictCheckResponse result = conflictService.check(request(
                "display VALUE", "display\\s+.*", current.getId(), scene.getId(), null));

        assertThat(result.aiUsed()).isTrue();
        assertThat(result.results()).singleElement().satisfies(item -> {
            assertThat(item.relation()).isEqualTo("OVERLAP");
            assertThat(item.riskType()).isEqualTo("MATCH_RANGE_OVERLAP");
            assertThat(item.evidence()).containsExactly("display version");
        });
    }

    @Test
    void includesPendingCreateRequestsFromTheSameCurrentView() throws Exception {
        ViewDefinition current = insertView("路由视图");
        Scene scene = insertScene("路由管理");
        OptionItem currentOption = new OptionItem(current.getId(), current.getName());
        OptionItem sceneOption = new OptionItem(scene.getId(), scene.getName());
        CommandApprovalSnapshot snapshot = new CommandApprovalSnapshot(
                "<p>display clock</p>", "display clock", "查看时钟",
                "display\\s+clock", true, true, "^(?:display\\s+clock)$",
                List.of(currentOption), null, List.of(sceneOption), null);
        CommandApprovalRequest approval = new CommandApprovalRequest();
        approval.setRequestType(CommandApprovalService.TYPE_CREATE);
        approval.setStatus(CommandApprovalService.STATUS_PENDING);
        approval.setProposedSnapshot(objectMapper.writeValueAsString(snapshot));
        approval.setChangeReason("新增命令");
        approval.setSubmitterUsername("developer");
        approval.setSubmitterDisplayName("开发人员");
        approval.setSubmittedAt(LocalDateTime.now());
        approval.setVersion(0L);
        approvalMapper.insert(approval);

        CommandConflictCheckResponse result = conflictService.check(request(
                "display clock", "display\\s+clock", current.getId(), scene.getId(), null));

        assertThat(result.results()).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo("PENDING");
            assertThat(item.sourceId()).isEqualTo(approval.getId());
            assertThat(item.relation()).isEqualTo("EXACT");
        });
    }

    @Test
    void excludesTheEditedCommandAndItsOwnApprovalButKeepsOtherPendingVersions() throws Exception {
        ViewDefinition current = insertView("业务视图");
        Scene scene = insertScene("业务场景");
        CommandRule active = insertCommand("display active", "display\\s+active", current, null, scene);
        OptionItem currentOption = new OptionItem(current.getId(), current.getName());
        OptionItem sceneOption = new OptionItem(scene.getId(), scene.getName());
        CommandApprovalSnapshot snapshot = new CommandApprovalSnapshot(
                "<p>display pending</p>", "display pending", "待审批修改",
                "display\\s+pending", true, true, "^(?:display\\s+pending)$",
                List.of(currentOption), null, List.of(sceneOption), active.getVersion());
        CommandApprovalRequest approval = insertApproval(
                CommandApprovalService.TYPE_UPDATE, active.getId(), snapshot);

        CommandConflictCheckRequest directEdit = new CommandConflictCheckRequest(
                active.getId(), null, "<p>display pending</p>", "", "display\\s+pending",
                true, true, List.of(current.getId()), List.of(scene.getId()), null);
        CommandConflictCheckResponse directResult = conflictService.check(directEdit);
        assertThat(directResult.results()).singleElement()
                .extracting(CommandConflictCheckResponse.Item::sourceType)
                .isEqualTo("PENDING");

        CommandConflictCheckRequest approvalEdit = new CommandConflictCheckRequest(
                active.getId(), approval.getId(), "<p>display pending</p>", "", "display\\s+pending",
                true, true, List.of(current.getId()), List.of(scene.getId()), null);
        CommandConflictCheckResponse approvalResult = conflictService.check(approvalEdit);
        assertThat(approvalResult.candidateCount()).isZero();
        assertThat(approvalResult.results()).isEmpty();
    }

    @Test
    void excludesCommandsFromAnotherSceneEvenWhenTheCurrentViewAndRegexMatch() {
        ViewDefinition current = insertView("共用视图");
        Scene existingScene = insertScene("场景 A");
        Scene newScene = insertScene("场景 B");
        insertCommand("display version", "display\\s+version", current, null, existingScene);

        CommandConflictCheckResponse result = conflictService.check(request(
                "display version", "display\\s+version", current.getId(), newScene.getId(), null));

        assertThat(result.candidateCount()).isZero();
        assertThat(result.results()).isEmpty();
    }

    private CommandConflictCheckRequest request(
            String expression,
            String regex,
            Long currentViewId,
            Long sceneId,
            Long targetViewId
    ) {
        return new CommandConflictCheckRequest(
                null, null, "<p>" + expression + "</p>", "", regex,
                true, true, List.of(currentViewId), List.of(sceneId), targetViewId);
    }

    private ViewDefinition insertView(String name) {
        ViewDefinition view = new ViewDefinition();
        view.setName(name);
        view.setDisplayOrder(0);
        view.setCreatedBy("system");
        view.setUpdatedBy("system");
        view.setCreatedAt(LocalDateTime.now());
        view.setUpdatedAt(LocalDateTime.now());
        viewMapper.insert(view);
        return view;
    }

    private CommandRule insertCommand(
            String expression,
            String regex,
            ViewDefinition currentView,
            ViewDefinition targetView,
            Scene scene
    ) {
        CommandRule command = new CommandRule();
        command.setExpressionHtml("<p>" + expression + "</p>");
        command.setExpressionText(expression);
        command.setDescription("");
        command.setRegexTemplate(regex);
        command.setMatchStart(true);
        command.setMatchEnd(true);
        command.setTargetViewId(targetView == null ? null : targetView.getId());
        command.setCreatedBy("system");
        command.setUpdatedBy("system");
        command.setCreatedAt(LocalDateTime.now());
        command.setUpdatedAt(LocalDateTime.now());
        command.setVersion(0L);
        commandMapper.insert(command);
        currentViewMapper.insert(new CommandCurrentView(command.getId(), currentView.getId()));
        commandSceneMapper.insert(new CommandScene(command.getId(), scene.getId()));
        return command;
    }

    private Scene insertScene(String name) {
        Scene scene = new Scene();
        scene.setName(name);
        scene.setCreatedBy("system");
        scene.setUpdatedBy("system");
        scene.setCreatedAt(LocalDateTime.now());
        scene.setUpdatedAt(LocalDateTime.now());
        sceneMapper.insert(scene);
        return scene;
    }

    private CommandApprovalRequest insertApproval(
            String type,
            Long targetCommandId,
            CommandApprovalSnapshot snapshot
    ) throws Exception {
        CommandApprovalRequest approval = new CommandApprovalRequest();
        approval.setRequestType(type);
        approval.setStatus(CommandApprovalService.STATUS_PENDING);
        approval.setTargetCommandId(targetCommandId);
        approval.setTargetCommandVersion(snapshot.version());
        approval.setProposedSnapshot(objectMapper.writeValueAsString(snapshot));
        approval.setChangeReason("测试申请");
        approval.setSubmitterUsername("developer");
        approval.setSubmitterDisplayName("开发人员");
        approval.setSubmittedAt(LocalDateTime.now());
        approval.setVersion(0L);
        approvalMapper.insert(approval);
        return approval;
    }
}
