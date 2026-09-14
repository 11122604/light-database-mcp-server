package org.turnright.mysqlmcpserver.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.turnright.mysqlmcpserver.model.JsonRpcRequest;
import org.turnright.mysqlmcpserver.model.JsonRpcResponse;
import org.turnright.mysqlmcpserver.tool.McpToolHandler;
import org.turnright.mysqlmcpserver.model.McpToolResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpServerCoreTest {

    @Mock
    private McpToolHandler toolHandler1;

    @Mock
    private McpToolHandler toolHandler2;

    private McpServerCore mcpServerCore;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        when(toolHandler1.getName()).thenReturn("test_tool_1");
        // lenient：getDescription/getInputSchema 仅在 tools/list 相关用例中被调用
        lenient().when(toolHandler1.getDescription()).thenReturn("Test tool 1 description");
        lenient().when(toolHandler1.getInputSchema()).thenReturn(Map.of("type", "object"));

        when(toolHandler2.getName()).thenReturn("test_tool_2");
        lenient().when(toolHandler2.getDescription()).thenReturn("Test tool 2 description");
        lenient().when(toolHandler2.getInputSchema()).thenReturn(Map.of("type", "object"));

        List<McpToolHandler> handlers = Arrays.asList(toolHandler1, toolHandler2);
        mcpServerCore = new McpServerCore(handlers);
    }

    @Test
    void processInitializeRequest_shouldReturnValidResponse() {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(1)
            .method("initialize")
            .params(Map.of("protocolVersion", "2024-11-05"))
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNotNull(response);
        assertEquals("2.0", response.getJsonrpc());
        assertEquals(1, response.getId());
        assertNull(response.getError());

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertEquals("2024-11-05", result.get("protocolVersion"));

        Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
        assertNotNull(capabilities);
        assertTrue(capabilities.containsKey("tools"));

        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertEquals("turnright-database-mcp-server", serverInfo.get("name"));
        assertEquals("0.0.1", serverInfo.get("version"));
    }

    @Test
    void processToolsListRequest_shouldReturnAllTools() {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(2)
            .method("tools/list")
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNotNull(response);
        assertNull(response.getError());

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");

        assertEquals(2, tools.size());

        Map<String, Object> tool1 = tools.get(0);
        assertEquals("test_tool_1", tool1.get("name"));
        assertEquals("Test tool 1 description", tool1.get("description"));

        Map<String, Object> tool2 = tools.get(1);
        assertEquals("test_tool_2", tool2.get("name"));
    }

    @Test
    void processToolsCallRequest_shouldExecuteTool() {
        McpToolResult mockResult = McpToolResult.success("Tool executed successfully");
        when(toolHandler1.execute(any())).thenReturn(mockResult);

        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(3)
            .method("tools/call")
            .params(Map.of(
                "name", "test_tool_1",
                "arguments", Map.of("param1", "value1")
            ))
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNotNull(response);
        assertNull(response.getError());

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertNotNull(result.get("content"));
        assertFalse((Boolean) result.get("isError"));

        verify(toolHandler1).execute(Map.of("param1", "value1"));
    }

    @Test
    void processToolsCallRequest_withUnknownTool_shouldReturnError() {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(4)
            .method("tools/call")
            .params(Map.of("name", "unknown_tool"))
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals(-32602, response.getError().getCode());  // Invalid params
    }

    @Test
    void processToolsCallRequest_withMissingToolName_shouldReturnError() {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(5)
            .method("tools/call")
            .params(Map.of("arguments", Map.of()))
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals(-32602, response.getError().getCode());
    }

    @Test
    void processToolsCallRequest_withNullParams_shouldReturnError() {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(6)
            .method("tools/call")
            .params(null)
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNotNull(response);
        assertNotNull(response.getError());
    }

    @Test
    void processPingRequest_shouldReturnEmptyResult() {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(7)
            .method("ping")
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNotNull(response);
        assertNull(response.getError());
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.isEmpty());
    }

    @Test
    void processUnknownMethod_shouldReturnMethodNotFound() {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(8)
            .method("unknown/method")
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals(-32601, response.getError().getCode());  // Method not found
    }

    @Test
    void processNotificationsInitialized_shouldReturnNull() {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(null)  // Notifications have null id
            .method("notifications/initialized")
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNull(response);  // Notifications don't require responses
    }

    @Test
    void processToolsCallRequest_withToolError_shouldReturnIsError() {
        McpToolResult errorResult = McpToolResult.error("Tool execution failed");
        when(toolHandler1.execute(any())).thenReturn(errorResult);

        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(9)
            .method("tools/call")
            .params(Map.of("name", "test_tool_1", "arguments", Map.of()))
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNotNull(response);
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue((Boolean) result.get("isError"));
    }

    @Test
    void getToolNames_shouldReturnAllHandlerNames() {
        var toolNames = mcpServerCore.getToolNames();

        assertEquals(2, toolNames.size());
        assertTrue(toolNames.contains("test_tool_1"));
        assertTrue(toolNames.contains("test_tool_2"));
    }

    @Test
    void getServerInfo_shouldReturnValidInfo() {
        Map<String, Object> serverInfo = mcpServerCore.getServerInfo();

        assertEquals("turnright-database-mcp-server", serverInfo.get("name"));
        assertEquals("0.0.1", serverInfo.get("version"));
        assertEquals(2, serverInfo.get("toolCount"));
    }

    @Test
    void constructor_withNullHandler_shouldSkipNull() {
        List<McpToolHandler> handlersWithNull = Arrays.asList(toolHandler1, null, toolHandler2);
        McpServerCore coreWithNulls = new McpServerCore(handlersWithNull);

        assertEquals(2, coreWithNulls.getToolNames().size());
    }

    @Test
    void processToolsCallRequest_withEmptyArguments_shouldUseEmptyMap() {
        McpToolResult mockResult = McpToolResult.success("Success");
        when(toolHandler1.execute(any())).thenReturn(mockResult);

        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(10)
            .method("tools/call")
            .params(Map.of("name", "test_tool_1"))  // No arguments
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNotNull(response);
        assertNull(response.getError());
        verify(toolHandler1).execute(new HashMap<>());
    }

    @Test
    void processRequest_missingMethod_shouldReturnInvalidRequest() {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(1)
            .build();  // 无 method

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertNotNull(response.getError());
        assertEquals(-32600, response.getError().getCode());  // 应为 Invalid Request 而非 -32603
    }

    @Test
    void processRequest_badJsonrpcVersion_shouldReturnInvalidRequest() {
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("1.0")
            .id(1)
            .method("ping")
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertEquals(-32600, response.getError().getCode());
    }

    @Test
    void processRequest_illegalIdType_shouldReturnInvalidRequest() {
        // JSON-RPC 规定 id 仅可为 string/number/null；Map 型 id 非法
        JsonRpcRequest request = JsonRpcRequest.builder()
            .jsonrpc("2.0")
            .id(Map.of("x", 1))
            .method("ping")
            .build();

        JsonRpcResponse response = mcpServerCore.processRequest(request);

        assertEquals(-32600, response.getError().getCode());
    }
}