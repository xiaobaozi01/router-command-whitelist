package com.example.whitelist.dto;

public record GitSyncStatus(
        boolean enabled,
        String repositoryUrl,
        String branch
) {
}
