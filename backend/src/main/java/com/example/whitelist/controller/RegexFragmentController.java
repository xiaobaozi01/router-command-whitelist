package com.example.whitelist.controller;

import com.example.whitelist.common.ApiResponse;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.RegexFragmentRequest;
import com.example.whitelist.dto.RegexFragmentResponse;
import com.example.whitelist.service.RegexFragmentService;
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
@RequestMapping("/api/fragments")
public class RegexFragmentController {
    private final RegexFragmentService fragmentService;

    public RegexFragmentController(RegexFragmentService fragmentService) {
        this.fragmentService = fragmentService;
    }

    @GetMapping
    public ApiResponse<PageResponse<RegexFragmentResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(fragmentService.page(current, size, keyword));
    }

    @GetMapping("/options")
    public ApiResponse<List<RegexFragmentResponse>> options() {
        return ApiResponse.success(fragmentService.options());
    }

    @PostMapping
    public ApiResponse<RegexFragmentResponse> create(@Valid @RequestBody RegexFragmentRequest request) {
        return ApiResponse.success(fragmentService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RegexFragmentResponse> update(@PathVariable Long id, @Valid @RequestBody RegexFragmentRequest request) {
        return ApiResponse.success(fragmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        fragmentService.delete(id);
        return ApiResponse.success();
    }
}
