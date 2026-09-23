package com.example.whitelist.controller;

import com.example.whitelist.auth.AuthRole;
import com.example.whitelist.auth.RequireRole;
import com.example.whitelist.common.ApiResponse;
import com.example.whitelist.common.PageResponse;
import com.example.whitelist.dto.NameRequest;
import com.example.whitelist.dto.OptionItem;
import com.example.whitelist.dto.SceneExportRequest;
import com.example.whitelist.dto.SceneResponse;
import com.example.whitelist.service.SceneExportService;
import com.example.whitelist.service.SceneService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/scenes")
public class SceneController {
    private final SceneService sceneService;
    private final SceneExportService sceneExportService;

    public SceneController(SceneService sceneService, SceneExportService sceneExportService) {
        this.sceneService = sceneService;
        this.sceneExportService = sceneExportService;
    }

    @GetMapping
    public ApiResponse<PageResponse<SceneResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(sceneService.page(current, size, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<SceneResponse> get(@PathVariable Long id) {
        return ApiResponse.success(sceneService.get(id));
    }

    @GetMapping("/options")
    public ApiResponse<List<OptionItem>> options() {
        return ApiResponse.success(sceneService.options());
    }

    @PostMapping
    @RequireRole(AuthRole.ADMIN)
    public ApiResponse<SceneResponse> create(@Valid @RequestBody NameRequest request) {
        return ApiResponse.success(sceneService.create(request));
    }

    @PostMapping("/export")
    @RequireRole(AuthRole.ADMIN)
    public ResponseEntity<byte[]> export(@Valid @RequestBody SceneExportRequest request) {
        SceneExportService.ExportArchive archive = sceneExportService.export(request.sceneIds());
        String disposition = ContentDisposition.attachment()
                .filename(archive.fileName(), StandardCharsets.UTF_8)
                .build().toString();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(archive.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(archive.content());
    }

    @PutMapping("/{id}")
    @RequireRole(AuthRole.ADMIN)
    public ApiResponse<SceneResponse> update(@PathVariable Long id, @Valid @RequestBody NameRequest request) {
        return ApiResponse.success(sceneService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole(AuthRole.ADMIN)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sceneService.delete(id);
        return ApiResponse.success();
    }
}
