package com.example.whitelist.dto;

import java.time.LocalDateTime;

public record ViewResponse(
        Long id,
        String name,
        int displayOrder,
        long commandCount,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
