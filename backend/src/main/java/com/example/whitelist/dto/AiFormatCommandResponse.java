package com.example.whitelist.dto;

import java.util.List;

public record AiFormatCommandResponse(
        String formattedHtml,
        String explanation,
        List<String> warnings
) {
}
