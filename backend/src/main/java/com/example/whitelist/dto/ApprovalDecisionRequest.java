package com.example.whitelist.dto;

import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(
        @Size(max = 500, message = "审批意见不能超过500个字符")
        String comment
) {
}
