package com.example.whitelist.dto;

import java.util.List;

public record CommandAuditUsersResponse(List<String> creators, List<String> updaters) {
}
