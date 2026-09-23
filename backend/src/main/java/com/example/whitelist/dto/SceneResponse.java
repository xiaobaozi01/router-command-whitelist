package com.example.whitelist.dto;

import java.time.LocalDateTime;

public record SceneResponse(
        Long id,
        String name,
        long commandCount,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
