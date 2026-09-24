package com.example.whitelist.dto;

import java.time.LocalDateTime;

public record CommandApprovalResponse(
        Long id,
        String requestType,
        String status,
        Long targetCommandId,
        Long targetCommandVersion,
        CommandApprovalSnapshot beforeSnapshot,
        CommandApprovalSnapshot proposedSnapshot,
        String changeReason,
        Long submitterUserId,
        String submitterUsername,
        String submitterDisplayName,
        LocalDateTime submittedAt,
        String reviewerUsername,
        String reviewerDisplayName,
        LocalDateTime reviewedAt,
        String reviewComment,
        Long generatedCommandId,
        Long version
) {
}
