package com.example.whitelist.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CommandAuditEventResponse(
        Long id,
        Long commandId,
        String action,
        Long actorUserId,
        String actorUsername,
        String actorDisplayName,
        LocalDateTime occurredAt,
        String changeReason,
        List<String> changedFields,
        CommandAuditSnapshot beforeSnapshot,
        CommandAuditSnapshot afterSnapshot,
        String source
) {
}
