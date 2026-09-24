package com.example.whitelist.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class DataMigrationData {
    private DataMigrationData() {
    }

    public record Manifest(
            String type,
            Integer formatVersion,
            Integer commandShardSize,
            DataMigrationSummary counts
    ) {
    }

    public record CommandIndex(List<String> shards) {
    }

    public record RegexFragmentData(
            Long id,
            String name,
            String description,
            String pattern,
            Boolean common,
            String createdBy,
            String updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record SceneData(
            Long id,
            String name,
            String createdBy,
            String updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ViewData(
            Long id,
            String name,
            Integer displayOrder,
            String createdBy,
            String updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record CommandData(
            Long id,
            String expressionHtml,
            String expressionText,
            String description,
            String regexTemplate,
            Boolean matchStart,
            Boolean matchEnd,
            Long targetViewId,
            List<Long> currentViewIds,
            List<Long> sceneIds,
            String createdBy,
            String updatedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
