package com.example.whitelist.controller;

import com.example.whitelist.auth.AuthRole;
import com.example.whitelist.auth.RequireRole;
import com.example.whitelist.common.ApiResponse;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.ApprovalDecisionRequest;
import com.example.whitelist.dto.ApprovalReasonRequest;
import com.example.whitelist.dto.AiApprovalAnalysisResponse;
import com.example.whitelist.dto.CommandApprovalResponse;
import com.example.whitelist.dto.CommandRequest;
import com.example.whitelist.service.AiAssistantService;
import com.example.whitelist.service.CommandApprovalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/command-approvals")
@RequireRole({AuthRole.ADMIN, AuthRole.DEVELOPER})
public class CommandApprovalController {
    private final CommandApprovalService approvalService;
    private final AiAssistantService aiAssistantService;

    public CommandApprovalController(
            CommandApprovalService approvalService,
            AiAssistantService aiAssistantService
    ) {
        this.approvalService = approvalService;
        this.aiAssistantService = aiAssistantService;
    }

    @GetMapping
    public ApiResponse<PageResponse<CommandApprovalResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestType
    ) {
        return ApiResponse.success(approvalService.page(current, size, status, requestType));
    }

    @PostMapping("/commands")
    @RequireRole(AuthRole.DEVELOPER)
    public ApiResponse<CommandApprovalResponse> submitCreate(@Valid @RequestBody CommandRequest request) {
        return ApiResponse.success(approvalService.submitCreate(request));
    }

    @PutMapping("/commands/{id}")
    @RequireRole(AuthRole.DEVELOPER)
    public ApiResponse<CommandApprovalResponse> submitUpdate(
            @PathVariable Long id,
            @Valid @RequestBody CommandRequest request
    ) {
        return ApiResponse.success(approvalService.submitUpdate(id, request));
    }

    @DeleteMapping("/commands/{id}")
    @RequireRole(AuthRole.DEVELOPER)
    public ApiResponse<CommandApprovalResponse> submitDelete(
            @PathVariable Long id,
            @RequestParam Long version,
            @RequestParam @Size(max = 500, message = "删除原因不能超过500个字符") String reason
    ) {
        return ApiResponse.success(approvalService.submitDelete(id, version, reason));
    }

    @PutMapping("/{id}")
    @RequireRole(AuthRole.DEVELOPER)
    public ApiResponse<CommandApprovalResponse> updateRequest(
            @PathVariable Long id,
            @Valid @RequestBody CommandRequest request
    ) {
        return ApiResponse.success(approvalService.updateRequest(id, request));
    }

    @PutMapping("/{id}/reason")
    @RequireRole(AuthRole.DEVELOPER)
    public ApiResponse<CommandApprovalResponse> updateReason(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalReasonRequest request
    ) {
        return ApiResponse.success(approvalService.updateReason(id, request.reason()));
    }

    @DeleteMapping("/{id}")
    @RequireRole(AuthRole.DEVELOPER)
    public ApiResponse<CommandApprovalResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(approvalService.cancel(id));
    }

    @PostMapping("/{id}/approve")
    @RequireRole(AuthRole.ADMIN)
    public ApiResponse<CommandApprovalResponse> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalDecisionRequest request
    ) {
        return ApiResponse.success(approvalService.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    @RequireRole(AuthRole.ADMIN)
    public ApiResponse<CommandApprovalResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalDecisionRequest request
    ) {
        return ApiResponse.success(approvalService.reject(id, request));
    }

    @PostMapping("/{id}/ai-analysis")
    @RequireRole(AuthRole.ADMIN)
    public ApiResponse<AiApprovalAnalysisResponse> analyzeWithAi(@PathVariable Long id) {
        CommandApprovalResponse approval = approvalService.getPendingForAiAnalysis(id);
        return ApiResponse.success(aiAssistantService.analyzeApproval(approval));
    }
}
