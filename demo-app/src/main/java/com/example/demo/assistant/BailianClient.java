package com.example.demo.assistant;

import com.example.demo.assistant.dto.FileUploadResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class BailianClient {
    private static final String CONTINUE_PROMPT = "请从上一处中断位置继续生成，不要重复已输出内容，并保持相同语言与结构化格式。";
    private static final int MAX_AUTO_CONTINUE_ROUNDS = 3;

    private final BailianProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public BailianClient(BailianProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public FileUploadResult uploadFile(MultipartFile file, String purpose) {
        requireApiKey();
        if (file == null || file.isEmpty()) {
            throw new AssistantException("上传文件不能为空。");
        }

        String safePurpose = blankToDefault(purpose, "file-extract");
        String endpoint = normalizeBaseUrl() + "/files";
        String boundary = "----BailianBoundary" + UUID.randomUUID();

        try {
            byte[] requestBody = buildMultipartBody(file, safePurpose, boundary);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AssistantException("百炼文件上传失败，状态码 " + response.statusCode() + "，响应：" + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return new FileUploadResult(
                    root.path("id").asText(),
                    root.path("filename").asText(file.getOriginalFilename()),
                    root.path("bytes").isMissingNode() ? null : root.path("bytes").asLong(),
                    root.path("purpose").asText(safePurpose)
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssistantException("百炼文件上传被中断：" + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new AssistantException("百炼文件上传失败：" + ex.getMessage(), ex);
        }
    }

    public boolean hasApiKey() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    public String chat(String model, String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        return chatRaw(model, messages);
    }

    public String chatWithFiles(String model, String systemPrompt, List<String> fileIds, String userPrompt) {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new AssistantException("文件编号列表不能为空。");
        }
        String joinedFileIds = String.join(",", fileIds.stream().filter(Objects::nonNull).map(String::trim).toList());
        if (joinedFileIds.isBlank()) {
            throw new AssistantException("文件编号列表不能为空。");
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("system", "fileid://" + joinedFileIds));
        messages.add(message("user", userPrompt));
        return chatRaw(model, messages);
    }

    public String chatWithMessages(String model, List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new AssistantException("消息列表不能为空。");
        }
        return chatRaw(model, messages);
    }

    public void chatStream(String model, String systemPrompt, String userPrompt, Consumer<String> onDelta) {
        if (onDelta == null) {
            throw new AssistantException("流式回调函数不能为空。");
        }
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        requireApiKey();
        String effectiveModel = blankToDefault(model, properties.getDefaultModel());
        chatStreamInternal(effectiveModel, messages, onDelta, true, MAX_AUTO_CONTINUE_ROUNDS, true);
    }
    public void chatStreamWithMessages(String model, List<Map<String, String>> messages, Consumer<String> onDelta) {
        if (messages == null || messages.isEmpty()) {
            throw new AssistantException("消息列表不能为空。");
        }
        if (onDelta == null) {
            throw new AssistantException("流式回调函数不能为空。");
        }
        requireApiKey();
        String effectiveModel = blankToDefault(model, properties.getDefaultModel());
        chatStreamInternal(effectiveModel, messages, onDelta, true, MAX_AUTO_CONTINUE_ROUNDS, true);
    }
    public List<Double> embed(String text) {
        requireApiKey();
        if (text == null || text.isBlank()) {
            throw new AssistantException("用于向量化的文本不能为空。");
        }

        String endpoint = normalizeBaseUrl() + "/embeddings";
        String effectiveModel = blankToDefault(properties.getEmbeddingModel(), "text-embedding-v3");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", effectiveModel);
        payload.put("input", text);

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AssistantException("百炼向量化失败，状态码 " + response.statusCode() + "，响应：" + response.body());
            }
            return extractEmbedding(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssistantException("百炼向量化被中断：" + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new AssistantException("百炼向量化失败：" + ex.getMessage(), ex);
        }
    }

    private String chatRaw(String model, List<Map<String, String>> messages) {
        requireApiKey();
        String effectiveModel = blankToDefault(model, properties.getDefaultModel());
        return chatRawInternal(effectiveModel, messages, true, MAX_AUTO_CONTINUE_ROUNDS, true);
    }

    private String chatRawInternal(String model,
                                   List<Map<String, String>> messages,
                                   boolean allowFallback,
                                   int remainingAutoContinueRounds,
                                   boolean allowTimeoutRetry) {
        String endpoint = normalizeBaseUrl() + "/chat/completions";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", 0.2);
        if (properties.getMaxTokens() != null && properties.getMaxTokens() > 0) {
            payload.put("max_tokens", properties.getMaxTokens());
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (isFallbackModel(model) && isModelNotFound(response.body())) {
                    String defaultModel = properties.getDefaultModel();
                    if (isValidFallbackModel(defaultModel, model)) {
                        return chatRawInternal(defaultModel, messages, false, remainingAutoContinueRounds, allowTimeoutRetry);
                    }
                }
                if (allowFallback && shouldFallbackStatus(response.statusCode())) {
                    String fallbackModel = properties.getFallbackModel();
                    if (isValidFallbackModel(fallbackModel, model)) {
                        return chatRawInternal(fallbackModel, messages, false, remainingAutoContinueRounds, allowTimeoutRetry);
                    }
                }
                throw new AssistantException("百炼对话失败，状态码 " + response.statusCode() + "，响应：" + response.body());
            }
            ChatCompletionResult assistantResult = extractAssistantResult(response.body());
            if (remainingAutoContinueRounds > 0 && isTruncatedFinishReason(assistantResult.finishReason())) {
                List<Map<String, String>> continuationMessages = new ArrayList<>(messages);
                continuationMessages.add(message("assistant", assistantResult.content()));
                continuationMessages.add(message("user", CONTINUE_PROMPT));
                String remainder = chatRawInternal(
                        model,
                        continuationMessages,
                        allowFallback,
                        remainingAutoContinueRounds - 1,
                        allowTimeoutRetry
                );
                return mergeAnswer(assistantResult.content(), remainder);
            }
            return assistantResult.content();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssistantException("百炼对话被中断：" + ex.getMessage(), ex);
        } catch (IOException ex) {
            if (allowFallback && isTimeoutException(ex)) {
                String fallbackModel = properties.getFallbackModel();
                if (isValidFallbackModel(fallbackModel, model)) {
                    return chatRawInternal(fallbackModel, messages, false, remainingAutoContinueRounds, allowTimeoutRetry);
                }
            }
            if (allowTimeoutRetry && isTimeoutException(ex)) {
                return chatRawInternal(model, messages, false, remainingAutoContinueRounds, false);
            }
            throw new AssistantException("百炼对话失败：" + ex.getMessage(), ex);
        }
    }

    private void chatStreamInternal(String model,
                                    List<Map<String, String>> messages,
                                    Consumer<String> onDelta,
                                    boolean allowFallback,
                                    int remainingAutoContinueRounds,
                                    boolean allowTimeoutRetry) {
        String endpoint = normalizeBaseUrl() + "/chat/completions";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", 0.2);
        payload.put("stream", true);
        if (properties.getMaxTokens() != null && properties.getMaxTokens() > 0) {
            payload.put("max_tokens", properties.getMaxTokens());
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey().trim())
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = readBodySafely(response.body());
                if (isFallbackModel(model) && isModelNotFound(errorBody)) {
                    String defaultModel = properties.getDefaultModel();
                    if (isValidFallbackModel(defaultModel, model)) {
                        chatStreamInternal(defaultModel, messages, onDelta, false, remainingAutoContinueRounds, allowTimeoutRetry);
                        return;
                    }
                }
                if (allowFallback && shouldFallbackStatus(response.statusCode())) {
                    String fallbackModel = properties.getFallbackModel();
                    if (isValidFallbackModel(fallbackModel, model)) {
                        chatStreamInternal(fallbackModel, messages, onDelta, false, remainingAutoContinueRounds, allowTimeoutRetry);
                        return;
                    }
                }
                throw new AssistantException("百炼流式对话失败，状态码 " + response.statusCode() + "，响应：" + errorBody);
            }

            StreamChatResult streamResult = readStreamAndForward(response.body(), onDelta);
            if (remainingAutoContinueRounds > 0 && isTruncatedFinishReason(streamResult.finishReason())) {
                List<Map<String, String>> continuationMessages = new ArrayList<>(messages);
                continuationMessages.add(message("assistant", streamResult.content()));
                continuationMessages.add(message("user", CONTINUE_PROMPT));
                onDelta.accept(System.lineSeparator());
                chatStreamInternal(
                        model,
                        continuationMessages,
                        onDelta,
                        allowFallback,
                        remainingAutoContinueRounds - 1,
                        allowTimeoutRetry
                );
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssistantException("百炼流式对话被中断：" + ex.getMessage(), ex);
        } catch (IOException ex) {
            if (allowFallback && isTimeoutException(ex)) {
                String fallbackModel = properties.getFallbackModel();
                if (isValidFallbackModel(fallbackModel, model)) {
                    chatStreamInternal(fallbackModel, messages, onDelta, false, remainingAutoContinueRounds, allowTimeoutRetry);
                    return;
                }
            }
            if (allowTimeoutRetry && isTimeoutException(ex)) {
                chatStreamInternal(model, messages, onDelta, false, remainingAutoContinueRounds, false);
                return;
            }
            throw new AssistantException("百炼流式对话失败：" + ex.getMessage(), ex);
        }
    }

    private byte[] buildMultipartBody(MultipartFile file, String purpose, String boundary) throws IOException {
        String filename = blankToDefault(file.getOriginalFilename(), "upload.txt");
        byte[] fileBytes = file.getBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writeLine(out, "--" + boundary);
        writeLine(out, "Content-Disposition: form-data; name=\"purpose\"");
        writeLine(out, "");
        writeLine(out, purpose);

        writeLine(out, "--" + boundary);
        writeLine(out, "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"");
        writeLine(out, "Content-Type: " + blankToDefault(file.getContentType(), MediaType.APPLICATION_OCTET_STREAM_VALUE));
        writeLine(out, "");
        out.write(fileBytes);
        writeLine(out, "");
        writeLine(out, "--" + boundary + "--");

        return out.toByteArray();
    }

    private ChatCompletionResult extractAssistantResult(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choiceNode = root.path("choices").path(0);
        String finishReason = choiceNode.path("finish_reason").asText("");
        JsonNode contentNode = choiceNode.path("message").path("content");

        if (contentNode.isTextual()) {
            return new ChatCompletionResult(contentNode.asText(), finishReason);
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode node : contentNode) {
                if (node.hasNonNull("text")) {
                    if (!builder.isEmpty()) {
                        builder.append(System.lineSeparator());
                    }
                    builder.append(node.path("text").asText());
                }
            }
            if (!builder.isEmpty()) {
                return new ChatCompletionResult(builder.toString(), finishReason);
            }
        }
        return new ChatCompletionResult(responseBody, finishReason);
    }

    private List<Double> extractEmbedding(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode embeddingNode = root.path("data").path(0).path("embedding");
        if (!embeddingNode.isArray()) {
            embeddingNode = root.path("output").path("embeddings").path(0).path("embedding");
        }
        if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
            throw new AssistantException("无法从百炼响应中解析向量结果。");
        }

        List<Double> vector = new ArrayList<>(embeddingNode.size());
        for (JsonNode node : embeddingNode) {
            vector.add(node.asDouble());
        }
        return Collections.unmodifiableList(vector);
    }

    private void requireApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AssistantException("百炼接口密钥缺失，请先完成系统配置。");
        }
    }

    private String normalizeBaseUrl() {
        String url = properties.getBaseUrl();
        if (url == null || url.isBlank()) {
            url = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static Map<String, String> message(String role, String content) {
        return Map.of(
                "role", role,
                "content", content
        );
    }

    private static void writeLine(ByteArrayOutputStream out, String line) throws IOException {
        out.write(line.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static boolean shouldFallbackStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private static boolean isTimeoutException(IOException ex) {
        if (ex instanceof HttpTimeoutException) {
            return true;
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String lowered = message.toLowerCase();
        return lowered.contains("timed out")
                || lowered.contains("connection reset")
                || lowered.contains("header parser received no bytes")
                || lowered.contains("connection closed");
    }

    private static boolean isValidFallbackModel(String fallbackModel, String currentModel) {
        return fallbackModel != null
                && !fallbackModel.isBlank()
                && !fallbackModel.equalsIgnoreCase(currentModel);
    }

    private boolean isFallbackModel(String model) {
        return model != null
                && properties.getFallbackModel() != null
                && model.equalsIgnoreCase(properties.getFallbackModel());
    }

    private static boolean isModelNotFound(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String text = body.toLowerCase();
        return text.contains("model_not_found")
                || text.contains("does not exist or you do not have access");
    }

    private static boolean isTruncatedFinishReason(String finishReason) {
        return "length".equalsIgnoreCase(finishReason)
                || "max_tokens".equalsIgnoreCase(finishReason);
    }

    private static String mergeAnswer(String content, String remainder) {
        if (content == null || content.isBlank()) {
            return blankToDefault(remainder, "");
        }
        if (remainder == null || remainder.isBlank()) {
            return content;
        }
        return content + System.lineSeparator() + remainder;
    }

    private StreamChatResult readStreamAndForward(InputStream responseStream, Consumer<String> onDelta) throws IOException {
        StringBuilder allContent = new StringBuilder();
        String finishReason = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                    continue;
                }

                String data = trimmed.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }

                JsonNode root = objectMapper.readTree(data);
                JsonNode choiceNode = root.path("choices").path(0);
                String currentFinishReason = choiceNode.path("finish_reason").asText("");
                if (!currentFinishReason.isBlank()) {
                    finishReason = currentFinishReason;
                }

                String deltaText = extractDeltaContent(choiceNode.path("delta").path("content"));
                if (!deltaText.isBlank()) {
                    allContent.append(deltaText);
                    onDelta.accept(deltaText);
                }
            }
        }
        return new StreamChatResult(allContent.toString(), finishReason);
    }

    private static String extractDeltaContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode node : contentNode) {
                if (node.hasNonNull("text")) {
                    builder.append(node.path("text").asText());
                }
            }
            return builder.toString();
        }
        return "";
    }

    private static String readBodySafely(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "<无法读取错误响应体>";
        }
    }

    private record ChatCompletionResult(String content, String finishReason) {
    }

    private record StreamChatResult(String content, String finishReason) {
    }
}


