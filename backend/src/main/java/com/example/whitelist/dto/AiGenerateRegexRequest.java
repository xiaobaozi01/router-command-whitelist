package com.example.whitelist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiGenerateRegexRequest(
        @NotBlank(message = "命令行表达式不能为空")
        @Size(max = 10000, message = "命令行表达式内容过长")
        String expressionHtml,
        @Size(max = 1000, message = "命令行描述不能超过1000个字符")
        String description,
        @Size(max = 10000, message = "现有正则内容过长")
        String currentRegexTemplate,
        Boolean matchStart,
        Boolean matchEnd,
        @Size(max = 50, message = "命令所在视图不能超过50个")
        List<@NotNull @Positive Long> currentViewIds,
        @Positive Long targetViewId
) {
}
