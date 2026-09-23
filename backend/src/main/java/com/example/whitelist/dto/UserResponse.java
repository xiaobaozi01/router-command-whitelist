package com.example.whitelist.dto;

import com.example.whitelist.auth.AuthRole;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        AuthRole role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
