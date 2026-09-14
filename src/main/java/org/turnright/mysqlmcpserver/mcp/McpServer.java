package org.turnright.mysqlmcpserver.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.turnright.mysqlmcpserver.model.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * MCP Server implementation using stdio transport
 * This class wraps McpServerCore for stdin/stdout communication
 */
@Slf4j
public class McpServer implements Runnable {

    private final ObjectMapper objectMapper;
    private final McpServerCore mcpServerCore;
    private volatile boolean running = true;

    public McpServer(ObjectMapper objectMapper, McpServerCore mcpServerCore) {
        this.objectMapper = objectMapper;
        this.mcpServerCore = mcpServerCore;
        log.info("MCP Stdio Server initialized with {} tools", mcpServerCore.getToolNames().size());
    }

    @Override
    public void run() {
        log.info("MCP Server started (stdio mode), waiting for requests on stdin...");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    handleRequest(line);
                } catch (Exception e) {
                    log.error("Error handling request: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("MCP Server error: {}", e.getMessage(), e);
        }
        log.info("MCP Server stopped");
    }

    public void stop() {
        running = false;
    }

    private void handleRequest(String jsonLine) {
        log.debug("Received request: {}", jsonLine);
        try {
            JsonRpcRequest request = objectMapper.readValue(jsonLine, JsonRpcRequest.class);

            JsonRpcResponse response = mcpServerCore.processRequest(request);

            if (response == null) {
                // 通知类消息（notifications/initialized）不需要响应：不要向 stdout 打印 "null"
                log.debug("Notification processed, no response sent");
                return;
            }

            String responseJson = objectMapper.writeValueAsString(response);
            log.debug("Sending response: {}", responseJson);
            System.out.println(responseJson);
            System.out.flush();

        } catch (Exception e) {
            log.error("Error parsing request: {}", e.getMessage());
            JsonRpcResponse errorResponse = JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(null)
                .error(JsonRpcError.invalidRequest(e.getMessage()))
                .build();
            sendResponse(errorResponse);
        }
    }

    private void sendResponse(JsonRpcResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            System.out.println(json);
            System.out.flush();
        } catch (Exception e) {
            log.error("Error sending response: {}", e.getMessage());
        }
    }
}