package com.example.whitelist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApprovalReasonRequest(
        @NotBlank(message = "申请原因不能为空")
        @Size(max = 500, message = "申请原因不能超过500个字符")
        String reason
) {
}
