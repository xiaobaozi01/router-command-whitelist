package com.example.whitelist.dto;

import java.time.LocalDateTime;

public record RegexFragmentResponse(
        Long id,
        String name,
        String description,
        String pattern,
        boolean common,
        long referenceCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
