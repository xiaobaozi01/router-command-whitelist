package com.example.whitelist.dto;

import java.time.LocalDateTime;

public record SceneResponse(Long id, String name, long commandCount, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
