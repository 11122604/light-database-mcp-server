package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.DatabaseService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseInfoToolTest {

    @Mock
    private DatabaseService databaseService;

    private DatabaseInfoTool tool;

    @BeforeEach
    void setUp() {
        tool = new DatabaseInfoTool(databaseService, new ObjectMapper());
        lenient().when(databaseService.getDefaultDatasourceName()).thenReturn("default");
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mysql_database_info", tool.getName());
    }

    @Test
    void getDescription_shouldContainDatasourceInfo() {
        assertTrue(tool.getDescription().contains("datasource"));
    }

    @Test
    void getInputSchema_shouldHaveDatasourceProperty() {
        Map<String, Object> schema = tool.getInputSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("datasource"));
    }

    @Test
    void execute_withoutDatasource_shouldUseDefault() throws Exception {
        Map<String, Object> mockInfo = Map.of(
            "database_name", "mydb",
            "database_product_name", "MySQL",
            "datasource", "default"
        );
        when(databaseService.getDatabaseInfo(null)).thenReturn(mockInfo);

        McpToolResult result = tool.execute(Map.of());

        assertFalse(result.getIsError());
        verify(databaseService).getDatabaseInfo(null);
    }

    @Test
    void execute_withDatasource_shouldUseSpecifiedDatasource() throws Exception {
        Map<String, Object> mockInfo = Map.of(
            "database_name", "analytics_db",
            "database_product_name", "MySQL",
            "datasource", "analytics"
        );
        when(databaseService.getDatabaseInfo("analytics")).thenReturn(mockInfo);

        McpToolResult result = tool.execute(Map.of("datasource", "analytics"));

        assertFalse(result.getIsError());
        verify(databaseService).getDatabaseInfo("analytics");
    }

    @Test
    void execute_withServiceException_shouldReturnError() throws Exception {
        when(databaseService.getDatabaseInfo(null))
            .thenThrow(new RuntimeException("Connection failed"));

        McpToolResult result = tool.execute(Map.of());

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("Connection failed"));
    }
}