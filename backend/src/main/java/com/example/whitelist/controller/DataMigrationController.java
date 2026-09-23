package com.example.whitelist.controller;

import com.example.whitelist.auth.AuthRole;
import com.example.whitelist.auth.RequireRole;
import com.example.whitelist.common.ApiResponse;
import com.example.whitelist.dto.DataMigrationSummary;
import com.example.whitelist.dto.GitSyncResult;
import com.example.whitelist.dto.GitSyncStatus;
import com.example.whitelist.service.DataMigrationService;
import com.example.whitelist.service.GitSyncService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/data-migration")
@RequireRole(AuthRole.ADMIN)
public class DataMigrationController {
    private final DataMigrationService dataMigrationService;
    private final GitSyncService gitSyncService;

    public DataMigrationController(DataMigrationService dataMigrationService, GitSyncService gitSyncService) {
        this.dataMigrationService = dataMigrationService;
        this.gitSyncService = gitSyncService;
    }

    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export() {
        String disposition = ContentDisposition.attachment()
                .filename("asset-data.zip", StandardCharsets.UTF_8)
                .build().toString();
        StreamingResponseBody body = dataMigrationService::exportTo;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(body);
    }

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DataMigrationSummary> validate(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(dataMigrationService.validate(file));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DataMigrationSummary> importData(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(dataMigrationService.importData(file));
    }

    @GetMapping("/git/status")
    public ApiResponse<GitSyncStatus> gitStatus() {
        return ApiResponse.success(gitSyncService.status());
    }

    @PostMapping("/git/sync")
    public ApiResponse<GitSyncResult> syncToGit() {
        return ApiResponse.success(gitSyncService.sync());
    }
}
