package com.example.whitelist.controller;

import com.example.whitelist.common.ApiResponse;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.NameRequest;
import com.example.whitelist.dto.OptionItem;
import com.example.whitelist.dto.ViewResponse;
import com.example.whitelist.service.ViewDefinitionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
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
@RequestMapping("/api/views")
public class ViewDefinitionController {
    private final ViewDefinitionService viewService;

    public ViewDefinitionController(ViewDefinitionService viewService) {
        this.viewService = viewService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ViewResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(viewService.page(current, size, keyword));
    }

    @GetMapping("/options")
    public ApiResponse<List<OptionItem>> options() {
        return ApiResponse.success(viewService.options());
    }

    @PostMapping
    public ApiResponse<ViewResponse> create(@Valid @RequestBody NameRequest request) {
        return ApiResponse.success(viewService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ViewResponse> update(@PathVariable Long id, @Valid @RequestBody NameRequest request) {
        return ApiResponse.success(viewService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        viewService.delete(id);
        return ApiResponse.success();
    }
}
