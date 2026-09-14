package org.turnright.mysqlmcpserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.turnright.mysqlmcpserver.model.JsonRpcError;
import org.turnright.mysqlmcpserver.model.JsonRpcRequest;
import org.turnright.mysqlmcpserver.model.JsonRpcResponse;
import org.turnright.mysqlmcpserver.mcp.McpServerCore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mcp.transport", havingValue = "http")
public class McpSseController {

    private final ObjectMapper objectMapper;
    private final McpServerCore mcpServerCore;

    // Store SSE emitters for each session
    private final Map<String, Sinks.Many<ServerSentEvent<String>>> sessionEmitters = new ConcurrentHashMap<>();

    // Maximum concurrent sessions to prevent exhaustion
    private static final int MAX_SESSIONS = 100;

    // SSE buffer size limit to prevent memory leak
    private static final int SSE_BUFFER_SIZE = 1000;

    /**
     * SSE endpoint for MCP connections
     * Clients connect here to receive events
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> connectSse(@RequestParam(required = false) String sessionId) {
        // Enforce session limit
        if (sessionEmitters.size() >= MAX_SESSIONS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Maximum sessions reached");
        }

        final String finalSessionId = sessionId != null ? sessionId : java.util.UUID.randomUUID().toString();

        log.info("New SSE connection: sessionId={}", finalSessionId);

        // replay sink：初始 endpoint/server_info 事件在客户端订阅前 emit 也会被缓存，
        // 避免热源 multicast 在无订阅者时丢弃握手事件；慢消费者也不再触发背压 ERROR 终止会话。
        Sinks.Many<ServerSentEvent<String>> emitter = Sinks.many().replay().limit(SSE_BUFFER_SIZE);
        sessionEmitters.put(finalSessionId, emitter);

        // Send endpoint event to tell client where to send messages
        emitter.tryEmitNext(ServerSentEvent.<String>builder()
            .event("endpoint")
            .data("/mcp/message?sessionId=" + finalSessionId)
            .build());

        // Send server info
        try {
            String serverInfo = objectMapper.writeValueAsString(Map.of(
                "name", "turnright-database-mcp-server",
                "version", "0.0.1",
                "capabilities", Map.of("tools", Map.of())
            ));
            emitter.tryEmitNext(ServerSentEvent.<String>builder()
                .event("server_info")
                .data(serverInfo)
                .build());
        } catch (Exception e) {
            log.error("Error sending server info", e);
        }

        // Return the flux, clean up when done
        return emitter.asFlux()
            .doOnCancel(() -> {
                log.info("SSE connection closed: sessionId={}", finalSessionId);
                sessionEmitters.remove(finalSessionId);
            })
            .doOnError(e -> {
                log.warn("SSE connection error for session {}: {}", finalSessionId, e.getMessage());
                sessionEmitters.remove(finalSessionId);
            });
    }

    /**
     * Message endpoint for receiving JSON-RPC requests
     * Clients send messages here
     */
    @PostMapping("/message")
    public void handleMessage(
        @RequestParam String sessionId,
        @RequestBody String message,
        @RequestHeader(value = "Content-Length", required = false) Long contentLength
    ) {
        // 消息大小限制：Content-Length 头可被 chunked 绕过，这里再按实际 body 长度兜底
        if ((contentLength != null && contentLength > 1_000_000) || message.length() > 1_000_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Message too large (max 1MB)");
        }

        log.debug("Received message for session {}: {}", sessionId, message);

        Sinks.Many<ServerSentEvent<String>> emitter = sessionEmitters.get(sessionId);
        if (emitter == null) {
            log.warn("No SSE connection for session: {}", sessionId);
            return;
        }
        // 注意：不能按 currentSubscriberCount()==0 即时清理——客户端 GET 之后、Flux 订阅完成之前
        // POST 到达属合法时序；replay sink 会缓存事件，订阅建立后一并送达。

        try {
            // Parse the request
            JsonRpcRequest request = objectMapper.readValue(message, JsonRpcRequest.class);

            // Process the request
            JsonRpcResponse response = mcpServerCore.processRequest(request);

            if (response == null) {
                // 通知类消息无需响应：不向 SSE 推送字面量 "null"
                log.debug("Notification processed, no SSE event sent for session {}", sessionId);
                return;
            }

            // Send response via SSE
            String responseJson = objectMapper.writeValueAsString(response);
            Sinks.EmitResult result = emitter.tryEmitNext(ServerSentEvent.<String>builder()
                .event("message")
                .data(responseJson)
                .build());

            if (result.isFailure()) {
                log.warn("Failed to emit message for session {}: {}", sessionId, result);
                sessionEmitters.remove(sessionId);
            }

        } catch (Exception e) {
            log.error("Error processing message for session {}: {}", sessionId, e.getMessage(), e);

            try {
                JsonRpcResponse errorResponse = JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(null)
                    .error(JsonRpcError.internalError(e.getMessage()))
                    .build();

                String errorJson = objectMapper.writeValueAsString(errorResponse);
                Sinks.EmitResult result = emitter.tryEmitNext(ServerSentEvent.<String>builder()
                    .event("message")
                    .data(errorJson)
                    .build());

                if (result.isFailure()) {
                    log.warn("Failed to send error response for session {}, removing", sessionId);
                    sessionEmitters.remove(sessionId);
                }
            } catch (Exception ex) {
                log.error("Error sending error response", ex);
                sessionEmitters.remove(sessionId);
            }
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "ok",
            "active_sessions", sessionEmitters.size(),
            "server_name", "turnright-database-mcp-server",
            "version", "0.0.1"
        );
    }
}