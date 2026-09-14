package org.turnright.mysqlmcpserver.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.*;
import org.turnright.mysqlmcpserver.tool.McpToolHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core MCP request processor - shared by both stdio and HTTP/SSE transports
 */
@Slf4j
@Component
public class McpServerCore {

    private final Map<String, McpToolHandler> toolHandlers;

    private static final String SERVER_NAME = "turnright-database-mcp-server";
    private static final String SERVER_VERSION = "0.0.1";

    /**
     * 服务端支持的 MCP 协议版本（Streamable HTTP 需 2025-03-26+；旧 SSE 客户端仍可用 2024-11-05）
     */
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = new LinkedHashSet<>(Arrays.asList(
        "2025-03-26",
        "2024-11-05"
    ));

    public McpServerCore(List<McpToolHandler> handlers) {
        this.toolHandlers = new ConcurrentHashMap<>();
        for (McpToolHandler handler : handlers) {
            if (handler != null) {
                this.toolHandlers.put(handler.getName(), handler);
            }
        }
        log.info("MCP Server Core initialized with {} tools: {}", toolHandlers.size(), toolHandlers.keySet());
    }

    /**
     * Process a JSON-RPC request and return the response
     */
    public JsonRpcResponse processRequest(JsonRpcRequest request) {
        String method;
        Object id;
        try {
            // JSON-RPC 2.0 协议级校验（协议错误返回 -32600 Invalid Request）
            if (request == null) {
                throw new IllegalArgumentException("Request is null");
            }
            if (!"2.0".equals(request.getJsonrpc())) {
                throw new IllegalArgumentException("Unsupported JSON-RPC version: " + request.getJsonrpc());
            }
            method = request.getMethod();
            id = request.getId();
            if (method == null || method.isEmpty()) {
                throw new IllegalArgumentException("Missing 'method'");
            }
            if (!isValidRequestId(id)) {
                throw new IllegalArgumentException("Invalid 'id': must be a string, number, or null");
            }
        } catch (IllegalArgumentException e) {
            return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(null)
                .error(JsonRpcError.invalidRequest(e.getMessage()))
                .build();
        }

        try {
            Object result;
            switch (method) {
                case "initialize":
                    result = handleInitialize(request);
                    break;
                case "notifications/initialized":
                    result = handleInitializedNotification(request);
                    // For notifications, we still return a response but with empty result
                    // Client may not expect a response for notifications
                    break;
                case "tools/list":
                    result = handleToolsList();
                    break;
                case "tools/call":
                    result = handleToolsCall(request);
                    break;
                case "ping":
                    result = handlePing();
                    break;
                default:
                    throw new UnsupportedOperationException("Method not found: " + method);
            }

            // For notifications, return minimal response
            if ("notifications/initialized".equals(method)) {
                return null;  // Notifications don't require responses
            }

            return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .result(result)
                .build();

        } catch (UnsupportedOperationException e) {
            return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .error(JsonRpcError.methodNotFound(method))
                .build();
        } catch (IllegalArgumentException e) {
            return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .error(JsonRpcError.invalidParams(e.getMessage()))
                .build();
        } catch (Exception e) {
            log.error("Error processing method {}: {}", method, e.getMessage(), e);
            return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .error(JsonRpcError.internalError(e.getMessage()))
                .build();
        }
    }

    /**
     * JSON-RPC 2.0 规定 id 只能是 string、number 或 null（可忽略）
     */
    private boolean isValidRequestId(Object id) {
        return id == null || id instanceof String || id instanceof Number;
    }

    private Object handleInitialize(JsonRpcRequest request) {
        log.info("MCP Initialize request received");
        Object params = request.getParams();
        String requestedVersion = null;
        if (params instanceof Map) {
            Object pv = ((Map<?, ?>) params).get("protocolVersion");
            if (pv instanceof String) {
                requestedVersion = (String) pv;
            }
        }
        return Map.of(
            "protocolVersion", negotiateProtocolVersion(requestedVersion),
            "capabilities", Map.of(
                "tools", Map.of()
            ),
            "serverInfo", Map.of(
                "name", SERVER_NAME,
                "version", SERVER_VERSION
            )
        );
    }

    /**
     * 协议版本协商：请求版本在支持集内则回显；否则回退到服务端支持的最新版本。
     * 旧 SSE 客户端请求 2024-11-05 时保持兼容，新版客户端用 2025-03-26。
     */
    private String negotiateProtocolVersion(String requested) {
        if (requested != null && SUPPORTED_PROTOCOL_VERSIONS.contains(requested)) {
            return requested;
        }
        if (requested == null) {
            return "2024-11-05";
        }
        return "2025-03-26";
    }

    private Object handleInitializedNotification(JsonRpcRequest request) {
        // This is a notification, not a request - no response needed
        // But since we're using JSON-RPC response pattern, we return empty success
        log.info("Client initialized notification received");
        return null;  // Notification - no response content
    }

    private Object handleToolsList() {
        log.debug("Tools list request received");
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpToolHandler handler : toolHandlers.values()) {
            tools.add(Map.of(
                "name", handler.getName(),
                "description", handler.getDescription(),
                "inputSchema", handler.getInputSchema()
            ));
        }
        return Map.of("tools", tools);
    }

    private Object handleToolsCall(JsonRpcRequest request) {
        log.debug("Tools call request received");
        try {
            Map<String, Object> params = (Map<String, Object>) request.getParams();
            if (params == null) {
                throw new IllegalArgumentException("Missing params for tools/call");
            }

            String toolName = (String) params.get("name");
            if (toolName == null) {
                throw new IllegalArgumentException("Missing tool name");
            }

            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
            if (arguments == null) {
                arguments = new HashMap<>();
            }

            McpToolHandler handler = toolHandlers.get(toolName);
            if (handler == null) {
                throw new IllegalArgumentException("Unknown tool: " + toolName);
            }

            McpToolResult result = handler.execute(arguments);

            return Map.of(
                "content", result.getContent(),
                "isError", result.getIsError() != null ? result.getIsError() : false
            );

        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Invalid params format: " + e.getMessage());
        }
    }

    private Object handlePing() {
        log.debug("Ping request received");
        return Map.of();
    }

    /**
     * Get the list of registered tool names
     */
    public Set<String> getToolNames() {
        return toolHandlers.keySet();
    }

    /**
     * Get server info for health endpoints
     */
    public Map<String, Object> getServerInfo() {
        return Map.of(
            "name", SERVER_NAME,
            "version", SERVER_VERSION,
            "toolCount", toolHandlers.size()
        );
    }
}