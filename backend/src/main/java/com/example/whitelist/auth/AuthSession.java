package com.example.whitelist.auth;

import java.io.Serializable;

public record AuthSession(Long userId, boolean configuredAdmin) implements Serializable {
}
