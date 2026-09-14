package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.DatabaseService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TableSchemaToolTest {

    @Mock
    private DatabaseService databaseService;

    private TableSchemaTool tool;

    @BeforeEach
    void setUp() {
        tool = new TableSchemaTool(databaseService, new ObjectMapper());
        lenient().when(databaseService.getDefaultDatasourceName()).thenReturn("default");
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mysql_table_schema", tool.getName());
    }

    @Test
    void getInputSchema_shouldHaveTableNameAndDatasource() {
        Map<String, Object> schema = tool.getInputSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("table_name"));
        assertTrue(properties.containsKey("datasource"));
        assertTrue(properties.containsKey("include_indexes"));
        assertTrue(properties.containsKey("include_primary_key"));
    }

    @Test
    void execute_withValidTableName_shouldReturnSchema() throws Exception {
        List<Map<String, Object>> mockSchema = List.of(
            Map.of("column_name", "id", "data_type", "INT"),
            Map.of("column_name", "name", "data_type", "VARCHAR")
        );
        when(databaseService.getTableSchema(null, "users")).thenReturn(mockSchema);

        McpToolResult result = tool.execute(Map.of("table_name", "users"));

        assertFalse(result.getIsError());
        verify(databaseService).getTableSchema(null, "users");
    }

    @Test
    void execute_withDatasource_shouldUseSpecifiedDatasource() throws Exception {
        List<Map<String, Object>> mockSchema = List.of(
            Map.of("column_name", "id", "data_type", "INT")
        );
        when(databaseService.getTableSchema("analytics", "reports")).thenReturn(mockSchema);
        when(databaseService.getTableIndexes("analytics", "reports")).thenReturn(List.of());
        when(databaseService.getTablePrimaryKey("analytics", "reports")).thenReturn(List.of());

        McpToolResult result = tool.execute(Map.of(
            "datasource", "analytics",
            "table_name", "reports",
            "include_indexes", true,
            "include_primary_key", true
        ));

        assertFalse(result.getIsError());
        verify(databaseService).getTableSchema("analytics", "reports");
        verify(databaseService).getTableIndexes("analytics", "reports");
        verify(databaseService).getTablePrimaryKey("analytics", "reports");
    }

    @Test
    void execute_withMissingTableName_shouldReturnError() {
        McpToolResult result = tool.execute(Map.of());

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("Missing"));
    }

    @Test
    void execute_withEmptyTableName_shouldReturnError() {
        McpToolResult result = tool.execute(Map.of("table_name", ""));

        assertTrue(result.getIsError());
    }

    @Test
    void execute_withServiceException_shouldReturnError() throws Exception {
        when(databaseService.getTableSchema(null, "users"))
            .thenThrow(new RuntimeException("Table not found"));

        McpToolResult result = tool.execute(Map.of("table_name", "users"));

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("Table not found"));
    }
}