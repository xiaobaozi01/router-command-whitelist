package com.example.whitelist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "请输入原密码") String currentPassword,
        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 100, message = "新密码长度应为6至100个字符") String newPassword
) {
}
