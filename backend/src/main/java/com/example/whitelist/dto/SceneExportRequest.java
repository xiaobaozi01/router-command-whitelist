package com.example.whitelist.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SceneExportRequest(
        @NotEmpty(message = "至少选择一个要导出的场景")
        List<@NotNull Long> sceneIds
) {
}
