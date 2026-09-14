package org.turnright.mysqlmcpserver.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.RedisService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisGetToolTest {

    @Mock
    private RedisService redisService;

    private RedisGetTool tool;

    @BeforeEach
    void setUp() {
        tool = new RedisGetTool(redisService);
    }

    @Test
    void getName_shouldReturnRedisGet() {
        assertEquals("redis_get", tool.getName());
    }

    @Test
    void getDescription_shouldDescribePurpose() {
        assertTrue(tool.getDescription().contains("value"));
    }

    @Test
    void getInputSchema_shouldHaveKeyProperty() {
        Map<String, Object> schema = tool.getInputSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("key"));
    }

    @Test
    void execute_shouldReturnValue() {
        when(redisService.get(null, "mykey")).thenReturn("myvalue");

        McpToolResult result = tool.execute(Map.of("key", "mykey"));

        assertFalse(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("myvalue"));
    }

    @Test
    void execute_nonexistentKey_shouldReturnNull() {
        when(redisService.get(null, "mykey")).thenReturn(null);

        McpToolResult result = tool.execute(Map.of("key", "mykey"));

        assertFalse(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("null"));
    }

    @Test
    void execute_missingKey_shouldReturnError() {
        McpToolResult result = tool.execute(Map.of());

        assertTrue(result.getIsError());
    }
}
