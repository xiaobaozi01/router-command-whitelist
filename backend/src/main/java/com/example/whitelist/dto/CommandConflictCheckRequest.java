package com.example.whitelist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CommandConflictCheckRequest(
        Long commandId,
        Long approvalRequestId,
        @NotBlank(message = "命令行表达式不能为空")
        String expressionHtml,
        @Size(max = 1000, message = "命令行描述不能超过1000个字符")
        String description,
        @NotBlank(message = "正则表达式不能为空")
        String regexTemplate,
        Boolean matchStart,
        Boolean matchEnd,
        @NotEmpty(message = "至少选择一个命令行所在视图")
        List<@NotNull Long> currentViewIds,
        @NotEmpty(message = "至少选择一个所属场景")
        List<@NotNull Long> sceneIds,
        Long targetViewId
) {
}
