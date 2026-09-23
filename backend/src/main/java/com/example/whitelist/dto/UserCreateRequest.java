package com.example.whitelist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]*", message = "用户名须以字母开头，只能包含字母、数字、点、横线和下划线")
        @Size(max = 64, message = "用户名不能超过64个字符") String username,
        @NotBlank(message = "请输入姓名")
        @Size(max = 100, message = "姓名不能超过100个字符") String displayName,
        @NotBlank(message = "请选择角色") String role,
        @NotBlank(message = "请输入初始密码")
        @Size(min = 6, max = 100, message = "密码长度应为6至100个字符") String password
) {
}
