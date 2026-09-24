package com.example.whitelist.dto;

public record AiStatusResponse(
        boolean enabled,
        boolean available,
        String protocol,
        String model,
        String message
) {
}
