package com.example.whitelist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank(message = "请输入姓名")
        @Size(max = 100, message = "姓名不能超过100个字符") String displayName,
        @NotBlank(message = "请选择角色") String role
) {
}
