package com.example.whitelist.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.whitelist.auth.AuthContext;
import com.example.whitelist.auth.AuthRole;
import com.example.whitelist.auth.CurrentUser;
import com.example.whitelist.common.BusinessException;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.ApprovalDecisionRequest;
import com.example.whitelist.dto.CommandApprovalResponse;
import com.example.whitelist.dto.CommandApprovalSnapshot;
import com.example.whitelist.dto.CommandRequest;
import com.example.whitelist.dto.CommandResponse;
import com.example.whitelist.dto.OptionItem;
import com.example.whitelist.entity.CommandApprovalRequest;
import com.example.whitelist.entity.Scene;
import com.example.whitelist.entity.ViewDefinition;
import com.example.whitelist.mapper.CommandApprovalRequestMapper;
import com.example.whitelist.mapper.SceneMapper;
import com.example.whitelist.mapper.ViewDefinitionMapper;
import com.example.whitelist.util.RichTextUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommandApprovalService {
    public static final String TYPE_CREATE = "CREATE";
    public static final String TYPE_UPDATE = "UPDATE";
    public static final String TYPE_DELETE = "DELETE";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private static final Set<String> TYPES = Set.of(TYPE_CREATE, TYPE_UPDATE, TYPE_DELETE);
    private static final Set<String> STATUSES = Set.of(STATUS_PENDING, STATUS_APPROVED, STATUS_REJECTED);

    private final CommandApprovalRequestMapper approvalMapper;
    private final SceneMapper sceneMapper;
    private final ViewDefinitionMapper viewMapper;
    private final RegexEngineService regexEngineService;
    private final CommandService commandService;
    private final ObjectMapper objectMapper;

    public CommandApprovalService(
            CommandApprovalRequestMapper approvalMapper,
            SceneMapper sceneMapper,
            ViewDefinitionMapper viewMapper,
            RegexEngineService regexEngineService,
            CommandService commandService,
            ObjectMapper objectMapper
    ) {
        this.approvalMapper = approvalMapper;
        this.sceneMapper = sceneMapper;
        this.viewMapper = viewMapper;
        this.regexEngineService = regexEngineService;
        this.commandService = commandService;
        this.objectMapper = objectMapper;
    }

    public PageResponse<CommandApprovalResponse> page(
            long current,
            long size,
            String status,
            String requestType
    ) {
        String normalizedStatus = normalizeFilter(status, STATUSES, "未知的审批状态");
        String normalizedType = normalizeFilter(requestType, TYPES, "未知的申请类型");
        CurrentUser user = requireCurrentUser();
        LambdaQueryWrapper<CommandApprovalRequest> query = new LambdaQueryWrapper<CommandApprovalRequest>()
                .eq(normalizedStatus != null, CommandApprovalRequest::getStatus, normalizedStatus)
                .eq(normalizedType != null, CommandApprovalRequest::getRequestType, normalizedType)
                .eq(user.role() == AuthRole.DEVELOPER,
                        CommandApprovalRequest::getSubmitterUsername, user.username())
                .orderByDesc(CommandApprovalRequest::getSubmittedAt)
                .orderByDesc(CommandApprovalRequest::getId);
        Page<CommandApprovalRequest> page = approvalMapper.selectPage(Page.of(current, size), query);
        return PageResponse.of(page, page.getRecords().stream().map(this::toResponse).toList());
    }

    public CommandApprovalResponse getPendingForAiAnalysis(Long requestId) {
        requireAdmin();
        CommandApprovalRequest approval = approvalMapper.selectById(requestId);
        if (approval == null) {
            throw new BusinessException(404, "审批申请不存在");
        }
        if (!STATUS_PENDING.equals(approval.getStatus())) {
            throw new BusinessException(409, "该申请已处理，无需再次分析");
        }
        return toResponse(approval);
    }

    @Transactional
    public CommandApprovalResponse submitCreate(CommandRequest request) {
        CurrentUser submitter = requireDeveloper();
        CommandApprovalSnapshot proposed = validateAndSnapshot(request, null);
        return createRequest(TYPE_CREATE, null, null, null, proposed, "新增命令", submitter);
    }

    @Transactional
    public CommandApprovalResponse submitUpdate(Long commandId, CommandRequest request) {
        CurrentUser submitter = requireDeveloper();
        CommandResponse current = commandService.get(commandId);
        requireCurrentVersion(request.version(), current.version());
        requireNoPendingRequest(commandId);
        String reason = requireReason(request.changeReason(), "修改命令时必须填写修改原因");
        CommandApprovalSnapshot before = snapshot(current);
        CommandApprovalSnapshot proposed = validateAndSnapshot(request, current.version());
        if (before.equals(proposed)) {
            throw new BusinessException(400, "命令内容没有变化");
        }
        return createRequest(
                TYPE_UPDATE, commandId, current.version(), before, proposed, reason, submitter);
    }

    @Transactional
    public CommandApprovalResponse submitDelete(Long commandId, Long version, String reason) {
        CurrentUser submitter = requireDeveloper();
        CommandResponse current = commandService.get(commandId);
        requireCurrentVersion(version, current.version());
        requireNoPendingRequest(commandId);
        String normalizedReason = requireReason(reason, "删除命令时必须填写删除原因");
        CommandApprovalSnapshot before = snapshot(current);
        return createRequest(
                TYPE_DELETE, commandId, current.version(), before, before, normalizedReason, submitter);
    }

    @Transactional
    public CommandApprovalResponse approve(Long requestId, ApprovalDecisionRequest decision) {
        CurrentUser reviewer = requireAdmin();
        CommandApprovalRequest approval = requirePendingForUpdate(requestId);
        CommandApprovalSnapshot proposed = readSnapshot(approval.getProposedSnapshot());
        if (!TYPE_DELETE.equals(approval.getRequestType())) {
            CommandApprovalSnapshot revalidated = validateAndSnapshot(
                    toRequest(proposed, proposed.version(), approval.getChangeReason()), proposed.version());
            if (!proposed.equals(revalidated)) {
                throw new BusinessException(
                        409, "正则片段、场景或视图已发生变化，请驳回后由开发人员重新提交");
            }
        }
        String auditReason = approvalAuditReason(approval, decision.comment());
        if (TYPE_CREATE.equals(approval.getRequestType())) {
            CommandResponse created = commandService.createFromApproval(
                    toRequest(proposed, null, approval.getChangeReason()),
                    approval.getSubmitterUsername(), auditReason);
            approval.setGeneratedCommandId(created.id());
        } else if (TYPE_UPDATE.equals(approval.getRequestType())) {
            commandService.updateFromApproval(
                    approval.getTargetCommandId(),
                    toRequest(proposed, approval.getTargetCommandVersion(), approval.getChangeReason()),
                    approval.getSubmitterUsername(), auditReason);
            approval.setGeneratedCommandId(approval.getTargetCommandId());
        } else if (TYPE_DELETE.equals(approval.getRequestType())) {
            commandService.deleteFromApproval(
                    approval.getTargetCommandId(), approval.getTargetCommandVersion(), auditReason);
        } else {
            throw new BusinessException(400, "未知的申请类型");
        }
        completeReview(approval, STATUS_APPROVED, decision.comment(), reviewer);
        approvalMapper.updateById(approval);
        return toResponse(approval);
    }

    @Transactional
    public CommandApprovalResponse reject(Long requestId, ApprovalDecisionRequest decision) {
        CurrentUser reviewer = requireAdmin();
        String comment = requireReason(decision.comment(), "驳回申请时必须填写原因");
        CommandApprovalRequest approval = requirePendingForUpdate(requestId);
        completeReview(approval, STATUS_REJECTED, comment, reviewer);
        approvalMapper.updateById(approval);
        return toResponse(approval);
    }

    private CommandApprovalResponse createRequest(
            String type,
            Long targetCommandId,
            Long targetVersion,
            CommandApprovalSnapshot before,
            CommandApprovalSnapshot proposed,
            String reason,
            CurrentUser submitter
    ) {
        CommandApprovalRequest approval = new CommandApprovalRequest();
        approval.setRequestType(type);
        approval.setStatus(STATUS_PENDING);
        approval.setTargetCommandId(targetCommandId);
        approval.setTargetCommandVersion(targetVersion);
        approval.setBeforeSnapshot(before == null ? null : writeSnapshot(before));
        approval.setProposedSnapshot(writeSnapshot(proposed));
        approval.setChangeReason(reason);
        approval.setSubmitterUserId(submitter.id());
        approval.setSubmitterUsername(submitter.username());
        approval.setSubmitterDisplayName(submitter.displayName());
        approval.setSubmittedAt(LocalDateTime.now());
        approval.setVersion(0L);
        approvalMapper.insert(approval);
        return toResponse(approval);
    }

    private CommandApprovalSnapshot validateAndSnapshot(CommandRequest request, Long version) {
        String expressionText = RichTextUtils.toPlainText(request.expressionHtml());
        if (expressionText.isBlank()) {
            throw new BusinessException(400, "命令行表达式不能为空");
        }
        if (expressionText.length() > 1000) {
            throw new BusinessException(400, "命令行表达式不能超过1000个字符");
        }
        boolean matchStart = request.matchStart() == null || request.matchStart();
        boolean matchEnd = request.matchEnd() == null || request.matchEnd();
        String expandedRegex = regexEngineService.expandAndValidate(
                request.regexTemplate(), matchStart, matchEnd);
        LinkedHashSet<Long> sceneIds = new LinkedHashSet<>(request.sceneIds());
        LinkedHashSet<Long> currentViewIds = new LinkedHashSet<>(request.currentViewIds());
        List<Scene> scenes = sceneMapper.selectByIds(sceneIds);
        List<ViewDefinition> currentViews = viewMapper.selectByIds(currentViewIds);
        if (scenes.size() != sceneIds.size()) {
            throw new BusinessException(400, "选择的场景不存在或已被删除");
        }
        if (currentViews.size() != currentViewIds.size()) {
            throw new BusinessException(400, "选择的所在视图不存在或已被删除");
        }
        ViewDefinition target = request.targetViewId() == null
                ? null : viewMapper.selectById(request.targetViewId());
        if (request.targetViewId() != null && target == null) {
            throw new BusinessException(400, "选择的目标视图不存在或已被删除");
        }
        return new CommandApprovalSnapshot(
                request.expressionHtml(), expressionText,
                request.description() == null ? "" : request.description().trim(),
                request.regexTemplate(), matchStart, matchEnd, expandedRegex,
                currentViewIds.stream().map(id -> toOption(requireView(currentViews, id))).toList(),
                target == null ? null : toOption(target),
                sceneIds.stream().map(id -> toOption(requireScene(scenes, id))).toList(),
                version);
    }

    private CommandApprovalSnapshot snapshot(CommandResponse command) {
        return new CommandApprovalSnapshot(
                command.expressionHtml(), command.expressionText(), command.description(),
                command.regexTemplate(), command.matchStart(), command.matchEnd(), command.expandedRegex(),
                command.currentViews(), command.targetView(), command.scenes(), command.version());
    }

    private CommandRequest toRequest(CommandApprovalSnapshot snapshot, Long version, String reason) {
        return new CommandRequest(
                snapshot.expressionHtml(), snapshot.description(), snapshot.regexTemplate(),
                snapshot.matchStart(), snapshot.matchEnd(),
                snapshot.currentViews().stream().map(OptionItem::id).toList(),
                snapshot.targetView() == null ? null : snapshot.targetView().id(),
                snapshot.scenes().stream().map(OptionItem::id).toList(),
                version, reason);
    }

    private CommandApprovalRequest requirePendingForUpdate(Long id) {
        CommandApprovalRequest approval = approvalMapper.selectOne(
                new LambdaQueryWrapper<CommandApprovalRequest>()
                        .eq(CommandApprovalRequest::getId, id)
                        .last("FOR UPDATE"));
        if (approval == null) {
            throw new BusinessException(404, "审批申请不存在");
        }
        if (!STATUS_PENDING.equals(approval.getStatus())) {
            throw new BusinessException(409, "该申请已处理，请刷新后查看");
        }
        return approval;
    }

    private void requireNoPendingRequest(Long commandId) {
        Long count = approvalMapper.selectCount(new LambdaQueryWrapper<CommandApprovalRequest>()
                .eq(CommandApprovalRequest::getTargetCommandId, commandId)
                .eq(CommandApprovalRequest::getStatus, STATUS_PENDING));
        if (count > 0) {
            throw new BusinessException(409, "该命令已有待审批申请，请先等待管理员处理");
        }
    }

    private void requireCurrentVersion(Long requested, Long current) {
        if (requested == null || !requested.equals(current)) {
            throw new BusinessException(409, "该命令已被其他人修改，请刷新后重新确认");
        }
    }

    private void completeReview(
            CommandApprovalRequest approval,
            String status,
            String comment,
            CurrentUser reviewer
    ) {
        approval.setStatus(status);
        approval.setReviewerUsername(reviewer.username());
        approval.setReviewerDisplayName(reviewer.displayName());
        approval.setReviewedAt(LocalDateTime.now());
        approval.setReviewComment(comment == null ? "" : comment.trim());
    }

    private String approvalAuditReason(CommandApprovalRequest approval, String comment) {
        String result = "审批通过审批单 #" + approval.getId()
                + "，提交人：" + approval.getSubmitterDisplayName()
                + "，申请原因：" + approval.getChangeReason();
        if (comment != null && !comment.isBlank()) {
            result += "，审批意见：" + comment.trim();
        }
        return result.length() <= 500 ? result : result.substring(0, 500);
    }

    private CommandApprovalResponse toResponse(CommandApprovalRequest approval) {
        return new CommandApprovalResponse(
                approval.getId(), approval.getRequestType(), approval.getStatus(),
                approval.getTargetCommandId(), approval.getTargetCommandVersion(),
                readSnapshot(approval.getBeforeSnapshot()), readSnapshot(approval.getProposedSnapshot()),
                approval.getChangeReason(), approval.getSubmitterUserId(),
                approval.getSubmitterUsername(), approval.getSubmitterDisplayName(), approval.getSubmittedAt(),
                approval.getReviewerUsername(), approval.getReviewerDisplayName(), approval.getReviewedAt(),
                approval.getReviewComment(), approval.getGeneratedCommandId(), approval.getVersion());
    }

    private String normalizeFilter(String value, Set<String> allowed, String errorMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new BusinessException(400, errorMessage);
        }
        return normalized;
    }

    private String requireReason(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, message);
        }
        return value.trim();
    }

    private CurrentUser requireCurrentUser() {
        CurrentUser user = AuthContext.get();
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        return user;
    }

    private CurrentUser requireDeveloper() {
        CurrentUser user = requireCurrentUser();
        if (user.role() != AuthRole.DEVELOPER) {
            throw new BusinessException(403, "只有开发人员可以提交命令申请");
        }
        return user;
    }

    private CurrentUser requireAdmin() {
        CurrentUser user = requireCurrentUser();
        if (user.role() != AuthRole.ADMIN) {
            throw new BusinessException(403, "只有管理员可以审批命令申请");
        }
        return user;
    }

    private String writeSnapshot(CommandApprovalSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存命令审批快照", exception);
        }
    }

    private CommandApprovalSnapshot readSnapshot(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, CommandApprovalSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取命令审批快照", exception);
        }
    }

    private Scene requireScene(List<Scene> scenes, Long id) {
        return scenes.stream().filter(scene -> scene.getId().equals(id)).findFirst().orElseThrow();
    }

    private ViewDefinition requireView(List<ViewDefinition> views, Long id) {
        return views.stream().filter(view -> view.getId().equals(id)).findFirst().orElseThrow();
    }

    private OptionItem toOption(Scene scene) {
        return new OptionItem(scene.getId(), scene.getName());
    }

    private OptionItem toOption(ViewDefinition view) {
        return new OptionItem(view.getId(), view.getName());
    }
}
