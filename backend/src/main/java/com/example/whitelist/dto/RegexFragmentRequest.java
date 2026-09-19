package com.example.whitelist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegexFragmentRequest(
        @NotBlank(message = "片段名称不能为空")
        @Size(max = 64, message = "片段名称不能超过64个字符")
        @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "片段名称只能包含大写字母、数字和下划线，且必须以字母开头")
        String name,
        @NotBlank(message = "片段描述不能为空")
        @Size(max = 500, message = "片段描述不能超过500个字符")
        String description,
        @NotBlank(message = "正则内容不能为空")
        String pattern,
        boolean common
) {
}
