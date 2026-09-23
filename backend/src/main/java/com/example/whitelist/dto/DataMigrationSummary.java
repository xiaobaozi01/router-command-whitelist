package com.example.whitelist.dto;

public record DataMigrationSummary(
        long regexFragments,
        long scenes,
        long views,
        long commands,
        long commandSceneRelations,
        long commandViewRelations
) {
}
