package com.honsin.aiword.service;


import com.google.gson.Gson;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import javazoom.jl.player.Player;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class YoudaoTtsService {

    private static final Logger LOG = Logger.getInstance(YoudaoTtsService.class);
    private static final String TTS_URL = "https://dict.youdao.com/dictvoice?audio=%s&type=1";
    private static final String NOTIFICATION_GROUP_ID = "WordMemorizerNotifications";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Gson gson = new Gson(); // For parsing JSON error responses

    /**
     * Requests pronunciation from Youdao TTS API and plays it.
     * Runs asynchronously in a background task.
     *
     * @param project Current project (can be null)
     * @param word    The word to pronounce.
     */
    public void pronounceWordAsync(Project project, String word) {
        if (word == null || word.trim().isEmpty()) {
            LOG.warn("Attempted to pronounce an empty word.");
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "获取发音 (Fetching Pronunciation)", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("正在请求 " + word + " 的发音 (Requesting pronunciation for " + word + ")...");

                try {
//                    Map<String, String> params = buildRequestParams(word);
                    HttpRequest request = buildHttpRequest(word);

                    LOG.info("Sending TTS request for word: " + word);
                    HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                    handleResponse(project, word, response);

                } catch (Exception e) {
                    LOG.error("Error during Youdao TTS request for word: " + word, e);
                    NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
                            .createNotification("发音请求错误 (Pronunciation Request Error)",
                                    "请求 '" + word + "' 发音时出错 (Error requesting pronunciation for '" + word + "'): " + e.getMessage(),
                                    NotificationType.ERROR)
                            .notify(project);
                }
            }
        });
    }

    private Map<String, String> buildRequestParams(String word) throws NoSuchAlgorithmException {
//        String appKey = YoudaoApiConfig.APP_KEY;
//        String appSecret = YoudaoApiConfig.APP_SECRET;
        String salt = UUID.randomUUID().toString();
        String curtime = String.valueOf(System.currentTimeMillis() / 1000);
        // Truncate input if needed (Youdao might have length limits)
        String input = (word.length() > 20) ? word.substring(0, 20) : word;
//        String signStr = appKey + input + salt + curtime + appSecret;
//        String sign = getDigest(signStr);

        Map<String, String> params = new HashMap<>();
        params.put("q", word); // The text to synthesize
//        params.put("langType", "en"); // Language: English
//        params.put("appKey", appKey);
//        params.put("salt", salt);
//        params.put("sign", sign);
//        params.put("signType", "v3");
//        params.put("curtime", curtime);
//        params.put("format", "mp3"); // Audio format
//        params.put("voice", "0"); // Voice type (0 for female, 1 for male - check Youdao docs)
//        params.put("rate", "0"); // Speed, optional
        // Add other parameters like 'voice', 'rate', 'volume' if needed, check Youdao docs

        return params;
    }

    private HttpRequest buildHttpRequest(String word) {
        String format = String.format(TTS_URL, word);
        System.out.println("---> 请求:" + format);
        return HttpRequest.newBuilder()
                .uri(URI.create(format))
                .GET()
                .timeout(Duration.ofSeconds(10)) // Request timeout
                .build();
    }

    private void handleResponse(Project project, String word, HttpResponse<InputStream> response) throws IOException {
        int statusCode = response.statusCode();
        String contentType = response.headers().firstValue("Content-Type").orElse("");

        LOG.info("TTS response status code: " + statusCode + ", Content-Type: " + contentType);

        if (statusCode == 200 && contentType.contains("audio/mpeg")) {
            // Success - Play audio
            try (InputStream audioStream = response.body()) {
                AudioPlayerUtil.playMp3Stream(audioStream);
                LOG.info("Successfully played pronunciation for: " + word);
            } catch (Exception e) {
                LOG.error("Error playing audio stream for word: " + word, e);
                NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
                        .createNotification("音频播放错误 (Audio Playback Error)",
                                "无法播放 '" + word + "' 的发音 (Could not play pronunciation for '" + word + "'): " + e.getMessage(),
                                NotificationType.ERROR)
                        .notify(project);
            }
        } else {
            // Error - Try to read error message from body
            String errorBody = "";
            try (InputStream errorStream = response.body()) {
                errorBody = new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ioEx) {
                LOG.error("Could not read error response body", ioEx);
            }

            LOG.error("Youdao TTS API error for word '" + word + "'. Status: " + statusCode + ", Content-Type: " + contentType + ", Body: " + errorBody);

            // Try to parse JSON error code if available
            String errorMessage = "服务器返回错误 (Server returned error) " + statusCode + ".";
            if (!errorBody.isEmpty()) {
                try {
                    Map<String, String> errorJson = gson.fromJson(errorBody, Map.class);
                    if (errorJson != null && errorJson.containsKey("errorCode")) {
                        errorMessage += " 有道错误码 (Youdao error code): " + errorJson.get("errorCode");
                        // You can check Youdao documentation for specific error codes
                    } else {
                        errorMessage += " 响应体 (Response Body): " + errorBody.substring(0, Math.min(errorBody.length(), 100)); // Show part of body
                    }
                } catch (Exception jsonEx) {
                    errorMessage += " 无法解析错误响应 (Could not parse error response).";
                }
            }

            NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
                    .createNotification("发音API错误 (Pronunciation API Error)", errorMessage, NotificationType.ERROR)
                    .notify(project);
        }
    }


    /**
     * Generates SHA-256 digest.
     */
    private static String getDigest(String string) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(string.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

class AudioPlayerUtil {

    private static final Logger LOG = Logger.getInstance(AudioPlayerUtil.class);

    public static void playMp3Stream(InputStream mp3Stream) throws LineUnavailableException, IOException, UnsupportedAudioFileException {
        if (mp3Stream == null) {
            throw new IOException("Input stream is null");
        }

        InputStream bufferedStream = new BufferedInputStream(mp3Stream);
        // 2. 使用 JLayer 播放
        Player player = null;
        // 播放成功 (如果需要可以在这里添加日志或通知)

        try {

            player = new Player(bufferedStream);
            player.play(); // 阻塞播放

        } catch (Exception e) {
            // Conversion might not be supported, try playing original format directly
            LOG.warn("PCM conversion not directly supported, trying original format. Error was: " + e.getMessage());
            // Reset the original stream (requires bufferedStream)
            bufferedStream.reset(); // Go back to the start of the buffered stream
        }
    }

}