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
class ListTablesToolTest {

    @Mock
    private DatabaseService databaseService;

    private ListTablesTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListTablesTool(databaseService, new ObjectMapper());
        lenient().when(databaseService.getDefaultDatasourceName()).thenReturn("default");
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mysql_list_tables", tool.getName());
    }

    @Test
    void getInputSchema_shouldHaveDatasourceProperty() {
        Map<String, Object> schema = tool.getInputSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("datasource"));
    }

    @Test
    void execute_withoutDatasource_shouldUseDefault() throws Exception {
        List<String> mockTables = List.of("users", "orders", "products");
        when(databaseService.listTables(null)).thenReturn(mockTables);

        McpToolResult result = tool.execute(Map.of());

        assertFalse(result.getIsError());
        verify(databaseService).listTables(null);
    }

    @Test
    void execute_withDatasource_shouldUseSpecifiedDatasource() throws Exception {
        List<String> mockTables = List.of("reports", "metrics");
        when(databaseService.listTables("analytics")).thenReturn(mockTables);

        McpToolResult result = tool.execute(Map.of("datasource", "analytics"));

        assertFalse(result.getIsError());
        verify(databaseService).listTables("analytics");
    }

    @Test
    void execute_withServiceException_shouldReturnError() throws Exception {
        when(databaseService.listTables(null))
            .thenThrow(new RuntimeException("Connection failed"));

        McpToolResult result = tool.execute(Map.of());

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("Connection failed"));
    }
}