package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.DatabaseService;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryToolTest {

    @Mock
    private DatabaseService databaseService;

    private QueryTool queryTool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        queryTool = new QueryTool(databaseService, objectMapper);
        lenient().when(databaseService.getDefaultDatasourceName()).thenReturn("default");
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mysql_query", queryTool.getName());
    }

    @Test
    void getDescription_shouldReturnNonEmptyDescription() {
        assertFalse(queryTool.getDescription().isEmpty());
        assertTrue(queryTool.getDescription().contains("SELECT"));
        assertTrue(queryTool.getDescription().contains("datasource"));
    }

    @Test
    void getInputSchema_shouldHaveRequiredFields() {
        Map<String, Object> schema = queryTool.getInputSchema();

        assertEquals("object", schema.get("type"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("sql"));
        assertTrue(properties.containsKey("datasource"));

        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("sql"));
    }

    @Test
    void execute_withValidSql_shouldReturnResults() throws SQLException {
        List<Map<String, Object>> mockResults = Arrays.asList(
            Map.of("id", 1, "name", "Alice"),
            Map.of("id", 2, "name", "Bob")
        );
        when(databaseService.executeQuery(eq(null), anyString())).thenReturn(mockResults);

        McpToolResult result = queryTool.execute(Map.of("sql", "SELECT * FROM users"));

        assertFalse(result.getIsError());
        assertNotNull(result.getContent());

        verify(databaseService).executeQuery(null, "SELECT * FROM users");
    }

    @Test
    void execute_withDatasource_shouldUseSpecifiedDatasource() throws SQLException {
        List<Map<String, Object>> mockResults = List.of(
            Map.of("id", 1, "log", "test")
        );
        when(databaseService.executeQuery(eq("analytics"), anyString())).thenReturn(mockResults);

        McpToolResult result = queryTool.execute(Map.of(
            "datasource", "analytics",
            "sql", "SELECT * FROM logs"
        ));

        assertFalse(result.getIsError());
        verify(databaseService).executeQuery("analytics", "SELECT * FROM logs");
    }

    @Test
    void execute_withMissingSql_shouldReturnError() {
        McpToolResult result = queryTool.execute(Map.of());

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("Missing"));
    }

    @Test
    void execute_withNullSql_shouldReturnError() {
        // Map.of 不允许 null 值，改用 HashMap
        Map<String, Object> args = new HashMap<>();
        args.put("sql", null);
        McpToolResult result = queryTool.execute(args);

        assertTrue(result.getIsError());
    }

    @Test
    void execute_withEmptySql_shouldReturnError() {
        McpToolResult result = queryTool.execute(Map.of("sql", ""));

        assertTrue(result.getIsError());
    }

    @Test
    void execute_withServiceException_shouldReturnError() throws SQLException {
        when(databaseService.executeQuery(eq(null), anyString()))
            .thenThrow(new SQLException("Connection failed"));

        McpToolResult result = queryTool.execute(Map.of("sql", "SELECT * FROM users"));

        assertTrue(result.getIsError());
        // QueryTool 刻意脱敏，不向客户端回传底层异常消息
        assertTrue(result.getContent().get(0).getText().contains("Query execution failed"));
    }

    @Test
    void execute_withShowStatement_shouldExecute() throws SQLException {
        when(databaseService.executeQuery(null, "SHOW TABLES")).thenReturn(List.of(Map.of("Tables_in_test", "users")));

        McpToolResult result = queryTool.execute(Map.of("sql", "SHOW TABLES"));

        assertFalse(result.getIsError());
        verify(databaseService).executeQuery(null, "SHOW TABLES");
    }

    @Test
    void execute_emptyResults_shouldReturnEmptyList() throws SQLException {
        when(databaseService.executeQuery(eq(null), anyString())).thenReturn(List.of());

        McpToolResult result = queryTool.execute(Map.of("sql", "SELECT * FROM empty_table"));

        assertFalse(result.getIsError());
    }
}