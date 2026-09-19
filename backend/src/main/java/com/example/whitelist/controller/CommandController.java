package com.example.whitelist.controller;

import com.example.whitelist.common.ApiResponse;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.CommandRequest;
import com.example.whitelist.dto.CommandResponse;
import com.example.whitelist.dto.RegexPreviewRequest;
import com.example.whitelist.dto.RegexPreviewResponse;
import com.example.whitelist.service.CommandService;
import com.example.whitelist.service.RegexEngineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/commands")
public class CommandController {
    private final CommandService commandService;
    private final RegexEngineService regexEngineService;

    public CommandController(CommandService commandService, RegexEngineService regexEngineService) {
        this.commandService = commandService;
        this.regexEngineService = regexEngineService;
    }

    @GetMapping
    public ApiResponse<PageResponse<CommandResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long currentViewId,
            @RequestParam(required = false) Long targetViewId,
            @RequestParam(required = false) Long sceneId
    ) {
        return ApiResponse.success(commandService.page(current, size, keyword, currentViewId, targetViewId, sceneId));
    }

    @GetMapping("/{id}")
    public ApiResponse<CommandResponse> get(@PathVariable Long id) {
        return ApiResponse.success(commandService.get(id));
    }

    @PostMapping
    public ApiResponse<CommandResponse> create(@Valid @RequestBody CommandRequest request) {
        return ApiResponse.success(commandService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CommandResponse> update(@PathVariable Long id, @Valid @RequestBody CommandRequest request) {
        return ApiResponse.success(commandService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        commandService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/regex-preview")
    public ApiResponse<RegexPreviewResponse> preview(@Valid @RequestBody RegexPreviewRequest request) {
        return ApiResponse.success(regexEngineService.preview(
                request.regexTemplate(),
                request.matchStart() == null || request.matchStart(),
                request.matchEnd() == null || request.matchEnd(),
                request.testText()
        ));
    }
}
