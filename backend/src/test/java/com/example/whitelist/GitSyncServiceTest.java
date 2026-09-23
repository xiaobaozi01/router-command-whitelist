package com.example.whitelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.whitelist.config.GitSyncProperties;
import com.example.whitelist.dto.DataMigrationSummary;
import com.example.whitelist.dto.GitSyncResult;
import com.example.whitelist.service.DataMigrationService;
import com.example.whitelist.service.GitSyncService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitSyncServiceTest {
    @TempDir
    private Path tempDirectory;

    @Test
    void pushesOnlyWhenTheGeneratedSnapshotChanges() throws Exception {
        Path remote = tempDirectory.resolve("remote.git");
        Path seed = tempDirectory.resolve("seed");
        Path work = tempDirectory.resolve("managed-work");
        run(tempDirectory, "git", "init", "--bare", remote.toString());
        Files.createDirectories(seed);
        run(seed, "git", "init");
        run(seed, "git", "config", "user.name", "test");
        run(seed, "git", "config", "user.email", "test@example.com");
        Files.writeString(seed.resolve("README.md"), "asset data\n");
        run(seed, "git", "add", "README.md");
        run(seed, "git", "commit", "-m", "init");
        run(seed, "git", "branch", "-M", "main");
        run(seed, "git", "remote", "add", "origin", remote.toString());
        run(seed, "git", "push", "-u", "origin", "main");

        AtomicReference<String> content = new AtomicReference<>("first\n");
        DataMigrationSummary summary = new DataMigrationSummary(0, 0, 0, 0, 0, 0);
        DataMigrationService migrationService = mock(DataMigrationService.class);
        when(migrationService.writeSnapshot(any())).thenAnswer(invocation -> {
            Path directory = invocation.getArgument(0);
            Files.createDirectories(directory.resolve("commands"));
            Files.writeString(directory.resolve("manifest.json"), content.get());
            Files.writeString(directory.resolve("commands/index.json"), "{\"shards\":[]}\n");
            return summary;
        });
        GitSyncProperties properties = new GitSyncProperties(
                true,
                remote.toString(),
                "main",
                work.toString(),
                "asset-data",
                "资产管理系统",
                "asset-system@example.com");
        GitSyncService service = new GitSyncService(properties, migrationService);

        GitSyncResult first = service.sync();
        assertThat(first.changed()).isTrue();
        assertThat(first.changedFiles()).isEqualTo(2);
        assertThat(first.commitId()).hasSize(40);
        assertThat(Files.readString(work.resolve("asset-data/manifest.json"))).isEqualTo("first\n");

        GitSyncResult unchanged = service.sync();
        assertThat(unchanged.changed()).isFalse();
        assertThat(unchanged.commitId()).isEqualTo(first.commitId());

        content.set("second\n");
        GitSyncResult second = service.sync();
        assertThat(second.changed()).isTrue();
        assertThat(second.commitId()).isNotEqualTo(first.commitId());
        assertThat(run(remote, "git", "show", "main:asset-data/manifest.json")).isEqualTo("second");
        assertThat(run(remote, "git", "log", "--format=%s", "main"))
                .startsWith("data: sync assets by 系统");
    }

    private String run(Path directory, String... command) throws Exception {
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();
        return output.trim();
    }
}
