package com.honsin.aiword.action;


import com.honsin.aiword.settings.WordMemorizerSettingsState;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public class DownloadWordbookAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(DownloadWordbookAction.class);
    private static final String DEFAULT_WORDBOOK_URL = "https://raw.githubusercontent.com/KyleBing/english-vocabulary/blob/master/json/3-CET4-顺序.json"; // <--- !!! NEEDS A REAL URL !!!
    private static final String DEFAULT_FILENAME = "cet4_default.txt";
    private static final String NOTIFICATION_GROUP_ID = "WordMemorizerNotifications";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject(); // Can be null
        String targetDirectory = WordMemorizerSettingsState.getInstance().getWordbookDirectory();

        if (StringUtil.isEmpty(targetDirectory)) {
            Messages.showWarningDialog(project,
                    "请先在 设置 -> 工具 -> AiWord Memorizer Settings 中配置单词本目录。\n(Please configure the wordbook directory in Settings -> Tools -> AiWord Memorizer Settings first.)",
                    "目录未配置 (Directory Not Configured)");
            return;
        }

        Path targetDir = Paths.get(targetDirectory);
        if (!Files.exists(targetDir)) {
            try {
                Files.createDirectories(targetDir);
                LOG.info("Created wordbook directory: " + targetDir);
            } catch (IOException ioException) {
                LOG.error("Failed to create wordbook directory: " + targetDir, ioException);
                NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
                        .createNotification("错误 (Error)", "无法创建目录 (Could not create directory): " + targetDir, NotificationType.ERROR)
                        .notify(project);
                return;
            }
        } else if (!Files.isDirectory(targetDir)) {
            Messages.showErrorDialog(project, "配置的路径不是一个目录 (Configured path is not a directory): " + targetDirectory, "配置错误 (Configuration Error)");
            return;
        }


        Path targetFilePath = targetDir.resolve(DEFAULT_FILENAME);

        // Use ProgressManager to run the download in the background
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "下载默认单词本 (Downloading Default Wordbook)", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("正在连接并下载... (Connecting and downloading...)");

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10)) // Add timeout
                        .followRedirects(HttpClient.Redirect.NORMAL) // Handle redirects
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(DEFAULT_WORDBOOK_URL))
                        .timeout(Duration.ofSeconds(30)) // Timeout for the request itself
                        .GET()
                        .build();

                try {
                    LOG.info("Downloading from " + DEFAULT_WORDBOOK_URL + " to " + targetFilePath);
                    // Download directly to file
                    HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(targetFilePath));

                    if (response.statusCode() == 200) {
                        LOG.info("Download successful. Status code: " + response.statusCode());
                        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
                                .createNotification("下载成功 (Download Successful)",
                                        "默认单词本已保存到 (Default wordbook saved to): " + targetFilePath,
                                        NotificationType.INFORMATION)
                                .notify(project);
                    } else {
                        LOG.error("Download failed. Status code: " + response.statusCode() + ", Body (might be error message): " + response.body()); // Log body for debugging
                        // Try to delete the potentially incomplete file
                        try {
                            Files.deleteIfExists(targetFilePath);
                        } catch (IOException ignored) {
                        }
                        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
                                .createNotification("下载失败 (Download Failed)",
                                        "无法下载文件 (Could not download file). 服务器状态码 (Server status code): " + response.statusCode(),
                                        NotificationType.ERROR)
                                .notify(project);
                    }

                } catch (IOException | InterruptedException | IllegalArgumentException ex) {
                    LOG.error("Error during wordbook download", ex);
                    // Try to delete the potentially incomplete file
                    try {
                        Files.deleteIfExists(targetFilePath);
                    } catch (IOException ignored) {
                    }
                    NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
                            .createNotification("下载出错 (Download Error)",
                                    "下载过程中发生错误 (An error occurred during download): " + ex.getMessage(),
                                    NotificationType.ERROR)
                            .notify(project);
                }
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 你的 update 逻辑保持不变
        e.getPresentation().setEnabledAndVisible(isValidUrl(DEFAULT_WORDBOOK_URL));
    }

    /**
     * 指定 update 方法在哪个线程执行。
     * 对于快速的 UI 状态检查，应使用 EDT。
     * 这是为了兼容较新的 IntelliJ Platform 版本。
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // 因为 update() 方法中的 isValidUrl 非常快，所以选择 EDT
        return ActionUpdateThread.EDT;
    }// Basic URL validation
    private boolean isValidUrl(String urlString) {
        if (StringUtil.isEmpty(urlString) || !urlString.startsWith("http")) {
            return false;
        }
        try {
            new URL(urlString).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}