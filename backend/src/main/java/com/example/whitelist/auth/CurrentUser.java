package com.example.whitelist.auth;

public record CurrentUser(
        Long id,
        String username,
        String displayName,
        AuthRole role,
        boolean passwordChangeable
) {
}
