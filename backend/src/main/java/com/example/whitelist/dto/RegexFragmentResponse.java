package com.example.whitelist.dto;

import java.time.LocalDateTime;

public record RegexFragmentResponse(
        Long id,
        String name,
        String description,
        String pattern,
        boolean common,
        long referenceCount,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
