package com.honsin.aiword.settings;


import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class WordMemorizerSettingsConfigurable implements Configurable {

    private WordMemorizerSettingsForm settingsForm; // Our UI form instance
    // 默认下载地址
    private static final String DEFAULT_WORDBOOK_URL = "https://gitee.com/handcontent/english-vocabulary/raw/master/json/3-CET4-顺序.json";
    private static final String NOTIFICATION_GROUP_ID = "WordMemorizerNotifications";
    // Setting storage key
    public static final String WORDBOOK_DIRECTORY_KEY = "wordmemorizer.wordbook.directory";

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "AiWord Memorizer Settings"; // Name displayed in Settings/Preferences
    }

    @Override
    public @Nullable JComponent createComponent() {
        settingsForm = new WordMemorizerSettingsForm();


        // Now, configure the browse action for the combined component:
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setTitle("选择单词本存放目录 (Select Wordbook Directory)");
        settingsForm.getWordbookDirectoryTextField().addBrowseFolderListener(
                new TextBrowseFolderListener(descriptor)
        );
        // --- End of Browse button configuration ---
        settingsForm.getWordbookDirectoryTextField().addBrowseFolderListener("选择单词本目录 (Select Wordbook Directory)",
                "请选择用于存放单词本文件的目录。(Please select the directory to store wordbook files.)",
                null, // project context, can be null for application settings
                FileChooserDescriptorFactory.createSingleFolderDescriptor()); // 只允许选择目录

        // --- 添加下载按钮的 Action Listener ---
        settingsForm.getDownloadButton().addActionListener(this::performDownloadAction);

        return settingsForm.getRootPanel();
    }

    // --- 下载按钮的事件处理方法 ---
    private void performDownloadAction(ActionEvent e) {
        String targetDirectoryPath = settingsForm.getWordbookDirectoryTextField().getText();

        // 1. 验证目录路径
        if (StringUtil.isEmptyOrSpaces(targetDirectoryPath)) {
            Messages.showErrorDialog("请先指定一个有效的单词目录。(Please specify a valid wordbook directory first.)",
                    "下载错误 (Download Error)");
            return;
        }

        Path targetDirectory = Paths.get(targetDirectoryPath);
        if (!Files.isDirectory(targetDirectory)) {
            // 尝试创建目录
            try {
                Files.createDirectories(targetDirectory);
                System.out.println("Created directory: " + targetDirectory);
            } catch (IOException ioException) {
                Messages.showErrorDialog("无法创建指定的目录: " + targetDirectoryPath + "\n错误: " + ioException.getMessage(),
                        "目录错误 (Directory Error)");
                return;
            }
            // 如果创建失败，Files.isDirectory 还会是 false，再次检查或依赖后续写入错误
            // if (!Files.isDirectory(targetDirectory)) { ... } // 可选的再次检查
        }


        // 2. 从 URL 提取文件名
        String fileName = extractFileNameFromUrl(DEFAULT_WORDBOOK_URL);
        if (fileName == null) {
            Messages.showErrorDialog("无法从 URL 中提取有效的文件名: " + DEFAULT_WORDBOOK_URL,
                    "URL 错误 (URL Error)");
            return;
        }
        Path targetFilePath = targetDirectory.resolve(fileName);

        // 3. 确认是否覆盖 (可选)
        // if (Files.exists(targetFilePath)) {
        //     int result = Messages.showYesNoDialog(
        //             "文件 '" + fileName + "' 已存在于目标目录中。是否覆盖？",
        //             "确认覆盖 (Confirm Overwrite)", Messages.getWarningIcon());
        //     if (result != Messages.YES) {
        //         return; // 用户取消
        //     }
        // }

        // 4. 执行后台下载任务
        // 需要一个 Project 上下文来显示后台任务进度条，尝试获取当前打开的项目
        Project currentProject = ProjectManager.getInstance().getOpenProjects().length > 0 ?
                ProjectManager.getInstance().getOpenProjects()[0] : null;

        new DownloadTask(currentProject, DEFAULT_WORDBOOK_URL, targetFilePath).queue();
    }

    // --- 辅助方法：从 URL 提取文件名 ---
    private String extractFileNameFromUrl(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            String path = url.getPath();
            // 获取最后一个 '/' 之后的部分
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean isModified() {
        // Check if the value in the text field is different from the saved value
        String savedPath = WordMemorizerSettingsState.getInstance().getWordbookDirectory();
        String currentPath = settingsForm.getWordbookDirectoryTextField().getText();
        return !StringUtil.equals(savedPath, currentPath);
    }

    @Override
    public void apply() throws ConfigurationException {
        // Save the setting when user clicks Apply or OK
        String currentPath = settingsForm.getWordbookDirectoryTextField().getText();
        if (StringUtil.isEmpty(currentPath)) {
            WordMemorizerSettingsState.getInstance().setWordbookDirectory("");
        } else {
            // Basic validation: check if path is somewhat valid (optional but good)
            try {
                Paths.get(currentPath); // Doesn't guarantee it's a usable directory yet
                WordMemorizerSettingsState.getInstance().setWordbookDirectory(currentPath);
            } catch (Exception e) {
                throw new ConfigurationException("无效的目录路径 (Invalid directory path): " + currentPath);
            }
        }
    }

    @Override
    public void reset() {
        // Reset the text field to the currently saved value
        settingsForm.getWordbookDirectoryTextField().setText(WordMemorizerSettingsState.getInstance().getWordbookDirectory());
    }

    @Override
    public void disposeUIResources() {
        // Called when the settings dialog is closed
        settingsForm = null;
    }

    // Helper method to get the default directory (e.g., user home)
    public static String getDefaultDirectory() {
        return System.getProperty("user.home") + "/.wordmemorizer_books";
    }

    // --- 内部类：后台下载任务 ---
    private static class DownloadTask extends Task.Backgroundable {
        private final String downloadUrl;
        private final Path targetFile;

        // 定义常量提高可读性和可维护性
        private static final int CONNECT_TIMEOUT_MS = 30000; // 增加到 30 秒
        private static final int READ_TIMEOUT_MS = 60000;    // 增加到 60 秒
        private static final int BUFFER_SIZE = 8192;         // 8 KB buffer
        private static final String USER_AGENT = "IntelliJ Plugin (AiWord Memorizer)"; // 设置 User-Agent

        public DownloadTask(@Nullable Project project, String downloadUrl, Path targetFile) {
            super(project, "下载单词本 (Downloading Wordbook)", true); // 可取消
            this.downloadUrl = downloadUrl;
            this.targetFile = targetFile;
        }

        @Override
        public void run(ProgressIndicator indicator) {
            indicator.setIndeterminate(false); // 尝试显示精确进度
            indicator.setText("正在准备下载...(Preparing download...)");
            indicator.setFraction(0.0);

            HttpURLConnection connection = null;

            // 使用 try-with-resources 确保流总是被关闭
            try {
                URL url = new URL(downloadUrl);
                connection = (HttpURLConnection) url.openConnection();

                // --- 设置请求属性 ---
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                // connection.setInstanceFollowRedirects(true); // 默认为 true，通常不需要设置

                indicator.setText("正在连接服务器...(Connecting to server...)");
                indicator.checkCanceled(); // 允许在连接前取消

                connection.connect(); // 连接
                int responseCode = connection.getResponseCode();

                // 检查是否成功 (2xx 状态码)
                if (responseCode >= 200 && responseCode < 300) {
                    long fileSize = connection.getContentLengthLong(); // 获取文件大小，可能为 -1
                    String fileName = targetFile.getFileName().toString();
                    indicator.setText("正在下载: " + fileName + " (Downloading...)");
                    if (fileSize <= 0) {
                        indicator.setIndeterminate(true); // 大小未知，进度条不确定
                        System.out.println("服务器未提供 Content-Length，进度条将不确定。");
                    }

                    // 使用 try-with-resources 同时管理输入和输出流
                    try (InputStream inputStream = connection.getInputStream();
                         BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream, BUFFER_SIZE);
                         OutputStream outputStream = Files.newOutputStream(targetFile)) { // 直接写入目标文件

                        byte[] buffer = new byte[BUFFER_SIZE];
                        long bytesCopied = 0;
                        int bytesRead;

                        while ((bytesRead = bufferedInputStream.read(buffer, 0, BUFFER_SIZE)) != -1) {
                            indicator.checkCanceled(); // 在循环中检查取消状态

                            outputStream.write(buffer, 0, bytesRead);
                            bytesCopied += bytesRead;

                            if (fileSize > 0) {
                                indicator.setFraction((double) bytesCopied / fileSize); // 更新进度
                            }
                            // 可选：即使进度不确定，也可以更新文本显示已下载字节数
                            // else { indicator.setText("Downloading: " + fileName + " (" + bytesCopied / 1024 + " KB)"); }
                        }
                    } // 输入输出流在此自动关闭

                    indicator.setFraction(1.0); // 确保结束时进度为 100%
                    System.out.println("文件下载成功: " + targetFile);

                } else {
                    // 处理非成功响应码
                    String errorDetails = "服务器响应错误 (Server returned error)。状态码 (Status Code): " + responseCode;
                    try (InputStream errorStream = connection.getErrorStream()) {
                        if (errorStream != null) {
                            // 尝试读取错误流内容获取更多信息
                            // 注意：这里简化处理，实际可能需要读取并解码
                            errorDetails += "\n服务器错误信息可能存在。";
                        }
                    } catch (IOException readError) {
                        errorDetails += "\n读取服务器错误流失败: " + readError.getMessage();
                    }
                    throw new IOException(errorDetails);
                }

            } catch (SocketTimeoutException e) {
                // 明确捕获超时异常
                System.err.println("下载超时: " + e.getMessage());
                throw new RuntimeException("下载超时，请检查网络连接或增加超时设置。(Download timed out. Check network or increase timeout settings.)", e);
            } catch (IOException e) {
                // 捕获其他 IO 异常
                System.err.println("下载过程中发生 IO 错误: " + e.getMessage());
                throw new RuntimeException("下载失败，发生 IO 错误。(Download failed due to IO error.) " + e.getMessage(), e);
            } catch (Exception e) {
                // 捕获其他意外错误
                System.err.println("下载过程中发生未知错误: " + e.getMessage());
                throw new RuntimeException("下载失败，发生未知错误。(Download failed due to unknown error.) " + e.getMessage(), e);
            } finally {
                if (connection != null) {
                    connection.disconnect(); // 最后断开连接
                }
            }
        }

        @Override
        public void onSuccess() {
            // （保持不变）
            String message = "默认词库 '" + targetFile.getFileName() + "' 下载成功！\n已保存至: " + targetFile.getParent();
            showNotification(message, NotificationType.INFORMATION);
        }

        @Override
        public void onThrowable(@NotNull Throwable error) {
            // （稍微改进错误消息）
            String errorMessage = "下载默认词库失败。\nURL: " + downloadUrl + "\n错误详情: " + error.getMessage();
            // 将堆栈跟踪打印到日志，方便调试
            error.printStackTrace();
            showNotification(errorMessage, NotificationType.ERROR);
        }

        @Override
        public void onCancel() {
            // （保持不变）
            showNotification("下载已取消。(Download Canceled)", NotificationType.WARNING);
            System.out.println("Download canceled by user for: " + downloadUrl);
        }

        // 辅助方法显示通知 (确保在 EDT 执行)
        private void showNotification(String content, NotificationType type) {
            ApplicationManager.getApplication().invokeLater(() -> {
                String title = type == NotificationType.INFORMATION ? "下载成功" : (type == NotificationType.WARNING ? "下载取消" : "下载失败");
                Notification notification = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
                        .createNotification(title, content, type);
                Notifications.Bus.notify(notification, getProject());
            });
        }
    }
}
