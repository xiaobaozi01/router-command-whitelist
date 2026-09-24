package com.example.whitelist.controller;

import com.example.whitelist.auth.AuthRole;
import com.example.whitelist.auth.RequireRole;
import com.example.whitelist.common.ApiResponse;
import com.example.whitelist.dto.AiFormatCommandRequest;
import com.example.whitelist.dto.AiFormatCommandResponse;
import com.example.whitelist.dto.AiGenerateRegexRequest;
import com.example.whitelist.dto.AiGenerateRegexResponse;
import com.example.whitelist.dto.AiStatusResponse;
import com.example.whitelist.service.AiAssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequireRole({AuthRole.ADMIN, AuthRole.DEVELOPER})
public class AiAssistantController {
    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @GetMapping("/status")
    public ApiResponse<AiStatusResponse> status() {
        return ApiResponse.success(aiAssistantService.status());
    }

    @PostMapping("/format-command")
    public ApiResponse<AiFormatCommandResponse> formatCommand(
            @Valid @RequestBody AiFormatCommandRequest request) {
        return ApiResponse.success(aiAssistantService.formatCommand(request));
    }

    @PostMapping("/generate-regex")
    public ApiResponse<AiGenerateRegexResponse> generateRegex(
            @Valid @RequestBody AiGenerateRegexRequest request) {
        return ApiResponse.success(aiAssistantService.generateRegex(request));
    }
}
