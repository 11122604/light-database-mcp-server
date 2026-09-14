package org.turnright.mysqlmcpserver.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcModelsTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jsonRpcRequest_shouldSerializeCorrectly() throws Exception {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(1)
            .method("test")
            .params(Map.of("key", "value"))
            .build();

        String json = objectMapper.writeValueAsString(request);

        assertTrue(json.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("\"method\":\"test\""));
    }

    @Test
    void jsonRpcRequest_shouldDeserializeCorrectly() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"test\",\"params\":{\"key\":\"value\"}}";

        JsonRpcRequest request = objectMapper.readValue(json, JsonRpcRequest.class);

        assertEquals("2.0", request.getJsonrpc());
        assertEquals(1, request.getId());
        assertEquals("test", request.getMethod());
        assertNotNull(request.getParams());
    }

    @Test
    void jsonRpcRequest_withNullId_shouldDeserialize() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"notification\"}";

        JsonRpcRequest request = objectMapper.readValue(json, JsonRpcRequest.class);

        assertNull(request.getId());
    }

    @Test
    void jsonRpcRequest_withStringId_shouldDeserialize() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":\"abc-123\",\"method\":\"test\"}";

        JsonRpcRequest request = objectMapper.readValue(json, JsonRpcRequest.class);

        assertEquals("abc-123", request.getId());
    }

    @Test
    void jsonRpcResponse_withResult_shouldSerializeCorrectly() throws Exception {
        JsonRpcResponse response = JsonRpcResponse.builder()
            .jsonrpc("2.0")
            .id(1)
            .result(Map.of("status", "ok"))
            .build();

        String json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("\"result\":"));
        assertFalse(json.contains("\"error\":"));
    }

    @Test
    void jsonRpcResponse_withError_shouldSerializeCorrectly() throws Exception {
        JsonRpcError error = JsonRpcError.internalError("Something went wrong");
        JsonRpcResponse response = JsonRpcResponse.builder()
            .jsonrpc("2.0")
            .id(1)
            .error(error)
            .build();

        String json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("\"error\":"));
        assertTrue(json.contains("\"code\":-32603"));
        assertFalse(json.contains("\"result\":"));
    }

    @Test
    void jsonRpcError_invalidRequest_shouldHaveCorrectCode() {
        JsonRpcError error = JsonRpcError.invalidRequest("Bad format");

        assertEquals(-32600, error.getCode());
        assertTrue(error.getMessage().contains("Invalid Request"));
    }

    @Test
    void jsonRpcError_methodNotFound_shouldHaveCorrectCode() {
        JsonRpcError error = JsonRpcError.methodNotFound("unknown_method");

        assertEquals(-32601, error.getCode());
        assertTrue(error.getMessage().contains("Method not found"));
    }

    @Test
    void jsonRpcError_invalidParams_shouldHaveCorrectCode() {
        JsonRpcError error = JsonRpcError.invalidParams("Missing parameter");

        assertEquals(-32602, error.getCode());
        assertTrue(error.getMessage().contains("Invalid params"));
    }

    @Test
    void jsonRpcError_internalError_shouldHaveCorrectCode() {
        JsonRpcError error = JsonRpcError.internalError("Server crashed");

        assertEquals(-32603, error.getCode());
        assertTrue(error.getMessage().contains("Internal error"));
    }

    @Test
    void jsonRpcError_toolError_shouldHaveCorrectCode() {
        JsonRpcError error = JsonRpcError.toolError("Tool failed");

        assertEquals(-32000, error.getCode());
        assertTrue(error.getMessage().contains("Tool execution error"));
    }

    @Test
    void mcpToolResult_success_shouldNotBeError() {
        McpToolResult result = McpToolResult.success("Operation completed");

        assertFalse(result.getIsError());
        assertNotNull(result.getContent());
    }

    @Test
    void mcpToolResult_error_shouldBeError() {
        McpToolResult result = McpToolResult.error("Operation failed");

        assertTrue(result.getIsError());
        assertNotNull(result.getContent());
    }

    @Test
    void mcpToolResult_withData_shouldContainData() {
        McpToolResult result = McpToolResult.success("10 rows affected");

        assertNotNull(result.getContent());
        assertEquals(1, result.getContent().size());
        assertEquals("10 rows affected", result.getContent().get(0).getText());
    }

    @Test
    void mcpContent_text_shouldHaveCorrectType() {
        McpContent content = McpContent.text("Hello world");

        assertEquals("text", content.getType());
        assertEquals("Hello world", content.getText());
    }
}