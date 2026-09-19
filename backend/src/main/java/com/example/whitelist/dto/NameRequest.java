package com.example.whitelist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NameRequest(
        @NotBlank(message = "名称不能为空")
        @Size(max = 100, message = "名称不能超过100个字符")
        String name
) {
}
