package com.agentplatform.server.service;

import com.agentplatform.server.model.ChatRequest;
import com.agentplatform.server.model.ChatResponse;
import com.agentplatform.server.model.ChatStreamChunk;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Bridges the dashboard chat request format to LangChain4j chat models.
 * When no LLM API key is configured it falls back to a local response so the
 * frontend can still be exercised during integration testing.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_HISTORY_MESSAGES = 20;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<StreamingChatModel> streamingChatModelProvider;
    private final String projectGuidePrompt;

    public ChatService(
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<StreamingChatModel> streamingChatModelProvider) {
        this.chatModelProvider = chatModelProvider;
        this.streamingChatModelProvider = streamingChatModelProvider;
        this.projectGuidePrompt = loadPrompt("/prompts/project-guide.txt");
    }

    public ChatResponse chat(ChatRequest request) {
        ChatModel model = chatModelProvider.getIfAvailable();
        if (model == null) {
            return ChatResponse.ok(fallbackText(request));
        }
        try {
            dev.langchain4j.model.chat.response.ChatResponse response =
                    model.chat(buildLlmRequest(request));
            String answer = response.aiMessage() == null ? "" : response.aiMessage().text();
            return ChatResponse.ok(answer == null ? "" : answer);
        } catch (Exception e) {
            log.warn("Chat model request failed: {}", e.getMessage());
            return ChatResponse.error("AI 服务暂时不可用", e.getMessage());
        }
    }

    public void stream(ChatRequest request, SseEmitter emitter) {
        StreamingChatModel streamingModel = streamingChatModelProvider.getIfAvailable();
        if (streamingModel != null) {
            try {
                streamingModel.chat(buildLlmRequest(request), streamingHandler(emitter));
            } catch (Exception e) {
                sendErrorChunk(emitter, e);
            }
            return;
        }

        ChatModel model = chatModelProvider.getIfAvailable();
        if (model != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    dev.langchain4j.model.chat.response.ChatResponse response =
                            model.chat(buildLlmRequest(request));
                    String answer = response.aiMessage() == null ? "" : response.aiMessage().text();
                    sendChunks(emitter, answer == null ? "" : answer);
                } catch (Exception e) {
                    sendErrorChunk(emitter, e);
                }
            });
            return;
        }

        CompletableFuture.runAsync(() -> sendChunks(emitter, fallbackText(request)));
    }

    private StreamingChatResponseHandler streamingHandler(SseEmitter emitter) {
        return new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                if (token != null && !token.isBlank()) {
                    sendChunk(emitter, token, false);
                }
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                sendChunk(emitter, "", true);
                complete(emitter);
            }

            @Override
            public void onError(Throwable error) {
                sendErrorChunk(emitter, error);
            }
        };
    }

    private dev.langchain4j.model.chat.request.ChatRequest buildLlmRequest(ChatRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt(request)));

        if (Boolean.TRUE.equals(request.hasContext()) && request.contextData() != null) {
            Object rawPath = request.contextData().get("path");
            Object rawText = request.contextData().get("text");
            if (rawText != null && !rawText.toString().isBlank()) {
                String path = rawPath == null ? "selected-file" : rawPath.toString();
                String text = rawText.toString();
                if (text.length() > 20000) {
                    text = text.substring(0, 20000) + "\n...[truncated]";
                }
                messages.add(new SystemMessage("用户当前选中的文件上下文：" + path + "\n```\n" + text + "\n```"));
            }
        }

        List<ChatRequest.ChatHistoryMessage> history = request.history();
        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < history.size(); i++) {
            ChatRequest.ChatHistoryMessage item = history.get(i);
            if (item == null || item.content() == null || item.content().isBlank()) {
                continue;
            }
            if ("user".equalsIgnoreCase(item.role())) {
                messages.add(new UserMessage(item.content()));
            } else if ("assistant".equalsIgnoreCase(item.role())) {
                messages.add(new AiMessage(item.content()));
            }
        }

        messages.add(buildUserMessage(request));
        return dev.langchain4j.model.chat.request.ChatRequest.builder()
                .messages(messages)
                .build();
    }

    private String systemPrompt(ChatRequest request) {
        StringBuilder prompt = new StringBuilder(projectGuidePrompt);
        if (Boolean.TRUE.equals(request.isThinkMode())) {
            prompt.append("\n用户开启了深度思考模式，请先分析再给出结论。");
        }
        int imageCount = imageCount(request);
        if (imageCount > 0) {
            prompt.append("\n用户上传了 ").append(imageCount)
                    .append(" 张图片，请结合图片内容回答。");
        }
        return prompt.toString();
    }

    private UserMessage buildUserMessage(ChatRequest request) {
        List<Content> contents = new ArrayList<>();
        contents.add(TextContent.from(request.query()));
        addImageContent(contents, request.imgBase64(), null);
        List<ChatRequest.ChatImageFile> files = request.imgFiles();
        if (files != null) {
            for (ChatRequest.ChatImageFile file : files) {
                if (file != null) {
                    addImageContent(contents, file.base64(), file.type());
                }
            }
        }
        if (contents.size() == 1) {
            return new UserMessage(request.query());
        }
        return new UserMessage(contents);
    }

    private void addImageContent(List<Content> contents, String rawBase64, String declaredType) {
        if (rawBase64 == null || rawBase64.isBlank()) {
            return;
        }
        String mimeType = declaredType == null || declaredType.isBlank() ? "image/png" : declaredType;
        String data = rawBase64;
        if (rawBase64.startsWith("data:")) {
            int comma = rawBase64.indexOf(',');
            if (comma > 0) {
                String header = rawBase64.substring(5, comma);
                String[] headerParts = header.split(";");
                if (headerParts.length > 0 && headerParts[0].contains("/")) {
                    mimeType = headerParts[0];
                }
                data = rawBase64.substring(comma + 1);
            }
        }
        contents.add(ImageContent.from(data, mimeType));
    }

    private String loadPrompt(String path) {
        try (InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream(path),
                "Missing prompt resource: " + path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load prompt resource: " + path, e);
        }
    }

    private int imageCount(ChatRequest request) {
        int count = request.imgBase64() == null || request.imgBase64().isBlank() ? 0 : 1;
        List<ChatRequest.ChatImageFile> files = request.imgFiles();
        count += files == null ? 0 : files.size();
        return count;
    }

    private void sendChunks(SseEmitter emitter, String text) {
        int chunkSize = 60;
        for (int i = 0; i < text.length(); i += chunkSize) {
            sendChunk(emitter, text.substring(i, Math.min(text.length(), i + chunkSize)), false);
        }
        sendChunk(emitter, "", true);
        complete(emitter);
    }

    private void sendErrorChunk(SseEmitter emitter, Throwable error) {
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        sendChunks(emitter, "AI 服务暂时不可用：" + detail);
    }

    private void sendChunk(SseEmitter emitter, String result, boolean isEnd) {
        try {
            emitter.send(SseEmitter.event().data(new ChatStreamChunk(0, result, isEnd)));
        } catch (Exception e) {
            log.debug("Failed to send chat stream chunk: {}", e.getMessage());
            complete(emitter);
        }
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // emitter is already closed
        }
    }

    private String fallbackText(ChatRequest request) {
        return """
                当前没有配置大模型，后端已进入本地联调模式，暂时无法调用大模型。

                配置方式：编辑项目根目录的 .env 文件，填写 LLM_API_KEY、LLM_BASE_URL、LLM_MODEL_NAME，然后重启应用。

                你发送的问题是：%s
                """.formatted(request.query());
    }
}
