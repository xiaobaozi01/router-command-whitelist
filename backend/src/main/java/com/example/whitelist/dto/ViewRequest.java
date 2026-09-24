package com.example.whitelist.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ViewRequest(
        @NotBlank(message = "视图名称不能为空")
        @Size(max = 100, message = "视图名称不能超过100个字符")
        String name,
        @Min(value = 0, message = "展示顺序不能小于0")
        Integer displayOrder
) {
    public ViewRequest {
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }
}
