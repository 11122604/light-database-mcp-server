package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.DatabaseService;
import org.turnright.mysqlmcpserver.service.SqlServerService;
import org.turnright.mysqlmcpserver.service.MongoService;
import org.turnright.mysqlmcpserver.service.ElasticsearchService;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListDataSourcesToolTest {

    @Mock
    private DatabaseService databaseService;

    @Mock
    private SqlServerService sqlServerService;

    @Mock
    private MongoService mongoService;

    @Mock
    private ElasticsearchService esService;

    private ListDataSourcesTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tool = new ListDataSourcesTool(objectMapper);
        // Inject all services via reflection (they are all @Autowired(required = false))
        ReflectionTestUtils.setField(tool, "databaseService", databaseService);
        ReflectionTestUtils.setField(tool, "sqlServerService", sqlServerService);
        ReflectionTestUtils.setField(tool, "mongoService", mongoService);
        ReflectionTestUtils.setField(tool, "esService", esService);
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("list_datasources", tool.getName());
    }

    @Test
    void getDescription_shouldDescribePurpose() {
        assertTrue(tool.getDescription().contains("datasources"));
    }

    @Test
    void getInputSchema_shouldHaveDatabaseTypeProperty() {
        Map<String, Object> schema = tool.getInputSchema();
        assertTrue(schema.containsKey("properties"));
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("database_type"));
    }

    @Test
    void execute_allTypes_shouldReturnAllDataSources() {
        when(databaseService.getAvailableDatasources()).thenReturn(Set.of("primary", "secondary"));
        when(databaseService.getDefaultDatasourceName()).thenReturn("primary");
        when(databaseService.getDatasourceMetadata()).thenReturn(Map.of("primary", "Main DB", "secondary", "Analytics"));
        when(sqlServerService.getAvailableDatasources()).thenReturn(Set.of("sql-primary"));
        when(sqlServerService.getDefaultDatasourceName()).thenReturn("sql-primary");
        when(sqlServerService.getDatasourceMetadata()).thenReturn(Map.of("sql-primary", "SQL Server DB"));
        when(mongoService.getAvailableDatasources()).thenReturn(Set.of("mongo-primary"));
        when(mongoService.getDefaultDatasourceName()).thenReturn("mongo-primary");
        when(mongoService.getDatasourceMetadata()).thenReturn(Map.of("mongo-primary", "Mongo DB"));
        when(esService.getAvailableDatasources()).thenReturn(Set.of("es-primary"));
        when(esService.getDefaultDatasourceName()).thenReturn("es-primary");
        when(esService.getDatasourceMetadata()).thenReturn(Map.of("es-primary", "ES DB"));

        McpToolResult result = tool.execute(Map.of());

        assertFalse(result.getIsError());
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("mysql"));
        assertTrue(text.contains("sqlserver"));
        assertTrue(text.contains("mongodb"));
        assertTrue(text.contains("elasticsearch"));
    }

    @Test
    void execute_mysqlOnly_shouldReturnMySQLDataSources() {
        when(databaseService.getAvailableDatasources()).thenReturn(Set.of("primary"));
        when(databaseService.getDefaultDatasourceName()).thenReturn("primary");
        when(databaseService.getDatasourceMetadata()).thenReturn(Map.of("primary", "Main DB"));

        McpToolResult result = tool.execute(Map.of("database_type", "mysql"));

        assertFalse(result.getIsError());
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("mysql"));
    }

    @Test
    void execute_withoutOptionalServices_shouldHandleGracefully() {
        // Remove optional services to simulate disabled databases
        ReflectionTestUtils.setField(tool, "sqlServerService", null);
        ReflectionTestUtils.setField(tool, "mongoService", null);
        ReflectionTestUtils.setField(tool, "esService", null);

        when(databaseService.getAvailableDatasources()).thenReturn(Set.of("primary"));
        when(databaseService.getDefaultDatasourceName()).thenReturn("primary");
        when(databaseService.getDatasourceMetadata()).thenReturn(Map.of("primary", "Main DB"));

        McpToolResult result = tool.execute(Map.of());

        assertFalse(result.getIsError());
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("mysql"));
        assertTrue(text.contains("not_configured"));
    }

    @Test
    void execute_noDatasources_shouldShowHint() {
        // Remove all services to simulate no configuration
        ReflectionTestUtils.setField(tool, "databaseService", null);
        ReflectionTestUtils.setField(tool, "sqlServerService", null);
        ReflectionTestUtils.setField(tool, "mongoService", null);
        ReflectionTestUtils.setField(tool, "esService", null);

        McpToolResult result = tool.execute(Map.of());

        assertFalse(result.getIsError());
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("hint"));
        assertTrue(text.contains("No datasources configured"));
    }
}