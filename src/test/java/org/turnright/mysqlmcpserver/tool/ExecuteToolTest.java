package org.turnright.mysqlmcpserver.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.DatabaseService;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecuteToolTest {

    @Mock
    private DatabaseService databaseService;

    private ExecuteTool executeTool;

    @BeforeEach
    void setUp() {
        executeTool = new ExecuteTool(databaseService);
        // lenient：getDefaultDatasourceName 仅在使用默认数据源的用例中被调用
        lenient().when(databaseService.getDefaultDatasourceName()).thenReturn("default");
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mysql_execute", executeTool.getName());
    }

    @Test
    void getDescription_shouldContainInsertUpdateDelete() {
        String description = executeTool.getDescription();
        assertTrue(description.contains("INSERT") || description.contains("UPDATE") || description.contains("DELETE"));
        assertTrue(description.contains("datasource"));
    }

    @Test
    void getInputSchema_shouldHaveSqlAndDatasourceProperties() {
        Map<String, Object> schema = executeTool.getInputSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("sql"));
        assertTrue(properties.containsKey("datasource"));
    }

    @Test
    void execute_withValidInsert_shouldReturnAffectedRows() throws SQLException {
        when(databaseService.executeUpdate(eq(null), anyString())).thenReturn(1);

        McpToolResult result = executeTool.execute(Map.of(
            "sql", "INSERT INTO users (name) VALUES ('Test')"
        ));

        assertFalse(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("1"));

        verify(databaseService).executeUpdate(null, "INSERT INTO users (name) VALUES ('Test')");
    }

    @Test
    void execute_withDatasource_shouldUseSpecifiedDatasource() throws SQLException {
        when(databaseService.executeUpdate(eq("analytics"), anyString())).thenReturn(5);

        McpToolResult result = executeTool.execute(Map.of(
            "datasource", "analytics",
            "sql", "INSERT INTO logs (message) VALUES ('test')"
        ));

        assertFalse(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("analytics"));

        verify(databaseService).executeUpdate("analytics", "INSERT INTO logs (message) VALUES ('test')");
    }

    @Test
    void execute_withValidUpdate_shouldReturnAffectedRows() throws SQLException {
        when(databaseService.executeUpdate(eq(null), anyString())).thenReturn(5);

        McpToolResult result = executeTool.execute(Map.of(
            "sql", "UPDATE users SET active = true WHERE id > 10"
        ));

        assertFalse(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("5"));
    }

    @Test
    void execute_withValidDelete_shouldReturnAffectedRows() throws SQLException {
        when(databaseService.executeUpdate(eq(null), anyString())).thenReturn(3);

        McpToolResult result = executeTool.execute(Map.of(
            "sql", "DELETE FROM users WHERE id < 5"
        ));

        assertFalse(result.getIsError());
    }

    @Test
    void execute_withSelectQuery_shouldReturnError() throws SQLException {
        McpToolResult result = executeTool.execute(Map.of(
            "sql", "SELECT * FROM users"
        ));

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("SELECT"));
    }

    @Test
    void execute_withMissingSql_shouldReturnError() {
        McpToolResult result = executeTool.execute(Map.of());

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("Missing"));
    }

    @Test
    void execute_withEmptySql_shouldReturnError() {
        McpToolResult result = executeTool.execute(Map.of("sql", ""));

        assertTrue(result.getIsError());
    }

    @Test
    void execute_withServiceException_shouldReturnError() throws SQLException {
        when(databaseService.executeUpdate(eq(null), anyString()))
            .thenThrow(new SQLException("Constraint violation"));

        McpToolResult result = executeTool.execute(Map.of("sql", "INSERT INTO users VALUES (1)"));

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("Constraint violation"));
    }
}