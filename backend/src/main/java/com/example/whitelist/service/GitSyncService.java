package com.example.whitelist.service;

import com.example.whitelist.common.BusinessException;
import com.example.whitelist.config.GitSyncProperties;
import com.example.whitelist.dto.DataMigrationSummary;
import com.example.whitelist.dto.GitSyncResult;
import com.example.whitelist.dto.GitSyncStatus;
import com.example.whitelist.util.AuditUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class GitSyncService {
    private static final long COMMAND_TIMEOUT_SECONDS = 120;
    private static final String MANAGED_MARKER = ".asset-sync-managed";

    private final GitSyncProperties properties;
    private final DataMigrationService dataMigrationService;
    private final ReentrantLock syncLock = new ReentrantLock();

    public GitSyncService(GitSyncProperties properties, DataMigrationService dataMigrationService) {
        this.properties = properties;
        this.dataMigrationService = dataMigrationService;
    }

    public GitSyncStatus status() {
        return new GitSyncStatus(properties.enabled(), properties.repositoryUrl(), properties.branch());
    }

    public GitSyncResult sync() {
        validateConfiguration();
        if (!syncLock.tryLock()) {
            throw new BusinessException(409, "已有数据同步任务正在执行");
        }
        try {
            return doSync();
        } finally {
            syncLock.unlock();
        }
    }

    private GitSyncResult doSync() {
        Path repository = Path.of(properties.workDirectory()).toAbsolutePath().normalize();
        Path dataRelative = validateDataDirectory();
        Path dataDirectory = repository.resolve(dataRelative).normalize();
        require(dataDirectory.startsWith(repository) && !dataDirectory.equals(repository),
                "GitHub 数据目录配置不安全");

        prepareRepository(repository);
        resetToRemote(repository);
        deleteDataDirectory(repository, dataDirectory);

        DataMigrationSummary summary;
        try {
            summary = dataMigrationService.writeSnapshot(dataDirectory);
        } catch (IOException exception) {
            throw new BusinessException(500, "生成 GitHub 数据文件失败");
        }

        String gitPath = dataRelative.toString().replace('\\', '/');
        runGit(repository, Set.of(0), "add", "--all", "--", gitPath);
        CommandResult diff = runGit(repository, Set.of(0, 1), "diff", "--cached", "--quiet", "--", gitPath);
        if (diff.exitCode() == 0) {
            String commitId = runGit(repository, Set.of(0), "rev-parse", "HEAD").output().trim();
            return new GitSyncResult(false, commitId, 0, "GitHub 中的数据已是最新", summary);
        }

        CommandResult changed = runGit(
                repository, Set.of(0), "diff", "--cached", "--name-only", "--", gitPath);
        int changedFiles = (int) changed.output().lines().filter(line -> !line.isBlank()).count();
        runGit(repository, Set.of(0), "config", "user.name", properties.authorName());
        runGit(repository, Set.of(0), "config", "user.email", properties.authorEmail());
        String message = "data: sync assets by " + AuditUtils.currentUsername();
        runGit(repository, Set.of(0), "commit", "-m", message, "--", gitPath);
        String commitId = runGit(repository, Set.of(0), "rev-parse", "HEAD").output().trim();
        try {
            runGit(repository, Set.of(0), "push", "origin", "HEAD:refs/heads/" + properties.branch());
        } catch (BusinessException exception) {
            throw new BusinessException(409, "GitHub 推送失败，远端分支可能已发生变化，请重新同步");
        }
        return new GitSyncResult(true, commitId, changedFiles, "数据已提交并推送到 GitHub", summary);
    }

    private void prepareRepository(Path repository) {
        if (Files.isDirectory(repository.resolve(".git"))) {
            require(Files.isRegularFile(repository.resolve(MANAGED_MARKER)),
                    "GitHub 同步工作目录不是由本系统创建的托管仓库");
            runGit(repository, Set.of(0), "remote", "set-url", "origin", properties.repositoryUrl());
            return;
        }
        if (Files.exists(repository) && !isEmptyDirectory(repository)) {
            throw new BusinessException(500, "GitHub 同步工作目录已存在且不是 Git 仓库");
        }
        Path parent = repository.getParent();
        require(parent != null, "GitHub 同步工作目录配置不正确");
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new BusinessException(500, "无法创建 GitHub 同步工作目录");
        }
        run(parent, Set.of(0), List.of(
                "git", "clone", "--no-checkout", properties.repositoryUrl(), repository.toString()));
        try {
            Files.writeString(repository.resolve(MANAGED_MARKER), "managed by asset data sync\n");
        } catch (IOException exception) {
            throw new BusinessException(500, "无法标记 GitHub 同步工作目录");
        }
    }

    private void resetToRemote(Path repository) {
        require(!properties.branch().startsWith("-") && !properties.branch().isBlank(),
                "GitHub 目标分支配置不正确");
        try {
            runGit(repository, Set.of(0), "fetch", "origin", properties.branch());
        } catch (BusinessException exception) {
            throw new BusinessException(409, "无法获取 GitHub 目标分支，请确认分支已存在且 SSH 配置正确");
        }
        runGit(repository, Set.of(0), "checkout", "--force", "-B", properties.branch(), "FETCH_HEAD");
        runGit(repository, Set.of(0), "reset", "--hard", "FETCH_HEAD");
    }

    private void deleteDataDirectory(Path repository, Path dataDirectory) {
        require(dataDirectory.startsWith(repository) && !dataDirectory.equals(repository),
                "GitHub 数据目录配置不安全");
        if (!Files.exists(dataDirectory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dataDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException exception) {
            throw new BusinessException(500, "清理旧的 GitHub 数据文件失败");
        }
    }

    private Path validateDataDirectory() {
        Path path = Path.of(properties.dataDirectory()).normalize();
        require(!path.isAbsolute() && path.getNameCount() > 0 && !path.startsWith("..") && !".".equals(path.toString()),
                "GitHub 数据目录配置不正确");
        return path;
    }

    private void validateConfiguration() {
        require(properties.enabled(), "GitHub 同步功能尚未启用");
        require(properties.repositoryUrl() != null && !properties.repositoryUrl().isBlank(),
                "GitHub 仓库地址尚未配置");
    }

    private boolean isEmptyDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        try (Stream<Path> children = Files.list(path)) {
            return children.findAny().isEmpty();
        } catch (IOException exception) {
            throw new BusinessException(500, "无法读取 GitHub 同步工作目录");
        }
    }

    private CommandResult runGit(Path directory, Set<Integer> allowedExitCodes, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        return run(directory, allowedExitCodes, command);
    }

    private CommandResult run(Path directory, Set<Integer> allowedExitCodes, List<String> command) {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException exception) {
            throw new BusinessException(500, "无法执行 Git，请确认运行环境已安装 Git");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> copyOutput(process.getInputStream(), output));
        try {
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                reader.join();
                throw new BusinessException(504, "Git 操作超时");
            }
            reader.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new BusinessException(500, "Git 操作被中断");
        }

        String text = output.toString(StandardCharsets.UTF_8);
        if (!allowedExitCodes.contains(process.exitValue())) {
            String detail = text.isBlank() ? "未返回错误信息" : text.trim();
            throw new BusinessException(500, "Git 操作失败：" + detail);
        }
        return new CommandResult(process.exitValue(), text);
    }

    private void copyOutput(InputStream input, ByteArrayOutputStream output) {
        try (input; output) {
            input.transferTo(output);
        } catch (IOException ignored) {
            // 进程结束或被强制终止时，输出流可能已关闭。
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(500, message);
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}
