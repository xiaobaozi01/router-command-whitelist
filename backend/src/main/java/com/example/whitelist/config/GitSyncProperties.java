package com.example.whitelist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.github-sync")
public record GitSyncProperties(
        boolean enabled,
        String repositoryUrl,
        String branch,
        String workDirectory,
        String dataDirectory,
        String authorName,
        String authorEmail
) {
    public GitSyncProperties {
        branch = defaultValue(branch, "main");
        workDirectory = defaultValue(workDirectory, "./data/github-sync");
        dataDirectory = defaultValue(dataDirectory, "asset-data");
        authorName = defaultValue(authorName, "资产管理系统");
        authorEmail = defaultValue(authorEmail, "asset-system@localhost");
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
