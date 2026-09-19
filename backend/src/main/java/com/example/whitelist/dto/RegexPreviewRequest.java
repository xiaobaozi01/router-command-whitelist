package com.example.whitelist.dto;

import jakarta.validation.constraints.NotBlank;

public record RegexPreviewRequest(
        @NotBlank(message = "正则表达式不能为空") String regexTemplate,
        Boolean matchStart,
        Boolean matchEnd,
        String testText
) {
}
