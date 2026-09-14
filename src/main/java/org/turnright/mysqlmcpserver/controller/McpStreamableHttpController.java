package org.turnright.mysqlmcpserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.turnright.mysqlmcpserver.mcp.McpServerCore;
import org.turnright.mysqlmcpserver.model.JsonRpcError;
import org.turnright.mysqlmcpserver.model.JsonRpcRequest;
import org.turnright.mysqlmcpserver.model.JsonRpcResponse;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Streamable HTTP transport（2025-03-26 起官方推荐的传输方式）。
 *
 * 单端点 POST /mcp：
 * - 请求体为 JSON-RPC 2.0 消息
 * - 客户端 Accept 含 text/event-stream 时以 SSE 事件返回，否则返回普通 JSON
 * - 会话通过 Mcp-Session-Id 响应头维持（服务端无状态，仅记录会话存在性）
 *
 * GET /mcp 用于能力探测。
 * 旧版 HTTP+SSE transport（/sse + /message）仍由 McpSseController 提供，两者并存。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mcp.transport", havingValue = "http")
public class McpStreamableHttpController {

    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String EVENT_STREAM = "text/event-stream";
    private static final String SERVER_NAME = "turnright-database-mcp-server";
    private static final String SERVER_VERSION = "0.0.1";

    private final ObjectMapper objectMapper;
    private final McpServerCore mcpServerCore;

    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    /**
     * Streamable HTTP 能力探测（可选的 GET 握手）
     */
    @GetMapping("/mcp")
    public ResponseEntity<String> discover() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "name", SERVER_NAME,
            "version", SERVER_VERSION,
            "capabilities", Map.of("tools", Map.of())
        ));
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
    }

    /**
     * Streamable HTTP 消息端点：接收 JSON-RPC 请求并同步返回响应。
     */
    @PostMapping("/mcp")
    public ResponseEntity<String> postMessage(
        @RequestBody(required = false) String rawBody,
        @RequestHeader(value = "Accept", required = false) String accept,
        @RequestHeader(value = SESSION_HEADER, required = false) String sessionId) {

        if (rawBody == null || rawBody.isBlank()) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"Empty request body\"}");
        }

        String activeSession = resolveSession(sessionId);
        boolean wantsSse = accept != null && accept.toLowerCase().contains(EVENT_STREAM);

        try {
            JsonRpcRequest request = objectMapper.readValue(rawBody, JsonRpcRequest.class);

            // 通知类消息（如 notifications/initialized）无需响应 → 202 Accepted
            JsonRpcResponse response = mcpServerCore.processRequest(request);
            if (response == null) {
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .header(SESSION_HEADER, activeSession)
                    .build();
            }

            String json = objectMapper.writeValueAsString(response);
            if (wantsSse) {
                // SSE 单事件即表示一次完整响应（响应结束即连接结束）
                String sse = "event: message\ndata: " + json + "\n\n";
                return ResponseEntity.ok()
                    .header(SESSION_HEADER, activeSession)
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(sse);
            }
            return ResponseEntity.ok()
                .header(SESSION_HEADER, activeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Invalid JSON-RPC payload: {}", e.getMessage());
            return buildError(activeSession, JsonRpcError.builder()
                .code(-32700)
                .message("Parse error")
                .build(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error processing streamable message: {}", e.getMessage(), e);
            return buildError(activeSession, JsonRpcError.internalError(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 会话解析：请求带已有会话头则复用；否则创建新会话。
     * 服务端为无状态，仅登记会话标识以防止无限增长。
     */
    private String resolveSession(String sessionId) {
        if (sessionId != null && sessions.contains(sessionId)) {
            return sessionId;
        }
        if (sessions.size() >= 100) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Maximum sessions reached");
        }
        String newId = UUID.randomUUID().toString();
        sessions.add(newId);
        log.debug("Created new streamable session: {}", newId);
        return newId;
    }

    private ResponseEntity<String> buildError(String sessionId, JsonRpcError error, HttpStatus status) {
        try {
            JsonRpcResponse resp = JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(null)
                .error(error)
                .build();
            return ResponseEntity.status(status)
                .header(SESSION_HEADER, sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(resp));
        } catch (Exception e) {
            log.error("Failed to serialize error response", e);
            return ResponseEntity.status(status).build();
        }
    }
}
