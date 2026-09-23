package com.example.whitelist.dto;

public record GitSyncResult(
        boolean changed,
        String commitId,
        int changedFiles,
        String message,
        DataMigrationSummary data
) {
}
