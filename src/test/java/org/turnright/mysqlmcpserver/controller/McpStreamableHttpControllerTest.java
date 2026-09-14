package org.turnright.mysqlmcpserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.turnright.mysqlmcpserver.mcp.McpServerCore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McpStreamableHttpControllerTest {

    private McpStreamableHttpController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // 空工具列表的真实 core：验证 initialize/协议协商/通知路径
        controller = new McpStreamableHttpController(objectMapper, new McpServerCore(List.of()));
    }

    @Test
    void discover_shouldReturnServerInfo() throws Exception {
        ResponseEntity<String> resp = controller.discover();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().contains("turnright-database-mcp-server"));
    }

    @Test
    void initialize_latestVersion_shouldNegotiate2025() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
            + "\"params\":{\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},\"clientInfo\":{}}}";

        ResponseEntity<String> resp = controller.postMessage(body, "application/json", null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getHeaders().getFirst("Mcp-Session-Id"));
        assertTrue(resp.getBody().contains("\"protocolVersion\":\"2025-03-26\""));
    }

    @Test
    void initialize_legacyVersion_shouldKeep2024() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
            + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}";

        ResponseEntity<String> resp = controller.postMessage(body, "application/json", null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        // 旧 SSE 客户端请求旧版本时保持兼容
        assertTrue(resp.getBody().contains("\"protocolVersion\":\"2024-11-05\""));
    }

    @Test
    void toolsCall_withSseAccept_shouldReturnEventStream() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}";

        ResponseEntity<String> resp = controller.postMessage(body, "application/json, text/event-stream", null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getHeaders().getContentType().toString().contains("text/event-stream"));
        assertTrue(resp.getBody().startsWith("event: message\ndata: "));
        assertTrue(resp.getBody().contains("\"id\":2"));
    }

    @Test
    void notification_shouldReturn202WithoutBody() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

        ResponseEntity<String> resp = controller.postMessage(body, "application/json", null);

        // 通知无需响应：202 Accepted，无响应体
        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        assertFalse(resp.hasBody());
    }

    @Test
    void emptyBody_shouldReturn400() {
        ResponseEntity<String> resp = controller.postMessage("   ", "application/json", null);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void invalidJson_shouldReturnParseError() {
        ResponseEntity<String> resp = controller.postMessage("{not valid json", "application/json", null);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertTrue(resp.getBody().contains("-32700"));
    }
}
