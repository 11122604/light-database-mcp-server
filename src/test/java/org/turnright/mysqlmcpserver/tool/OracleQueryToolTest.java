package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.OracleService;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OracleQueryToolTest {

    @Mock
    private OracleService oracleService;

    private OracleQueryTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tool = new OracleQueryTool(objectMapper);
        ReflectionTestUtils.setField(tool, "oracleService", oracleService);
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("oracle_query", tool.getName());
    }

    @Test
    void getDescription_shouldContainOracle() {
        assertTrue(tool.getDescription().contains("Oracle"));
        assertTrue(tool.getDescription().contains("SELECT"));
    }

    @Test
    void getInputSchema_shouldHaveSqlAndDatasource() {
        Map<String, Object> schema = tool.getInputSchema();
        assertTrue(schema.containsKey("properties"));
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("sql"));
        assertTrue(properties.containsKey("datasource"));
    }

    @Test
    void execute_validSelectQuery_shouldReturnResults() throws Exception {
        when(oracleService.getDefaultDatasourceName()).thenReturn("default");
        when(oracleService.executeQuery(null, "SELECT * FROM USERS")).thenReturn(
            List.of(Map.of("ID", 1, "NAME", "Alice"))
        );

        McpToolResult result = tool.execute(Map.of("sql", "SELECT * FROM USERS"));

        assertFalse(result.getIsError());
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("success"));
        assertTrue(text.contains("Alice"));
    }

    @Test
    void execute_withDatasource_shouldUseCorrectDs() throws Exception {
        when(oracleService.executeQuery("secondary", "SELECT * FROM ORDERS")).thenReturn(
            List.of(Map.of("ORDER_ID", 100))
        );

        McpToolResult result = tool.execute(Map.of(
            "sql", "SELECT * FROM ORDERS",
            "datasource", "secondary"
        ));

        assertFalse(result.getIsError());
    }

    @Test
    void execute_invalidStatement_shouldReturnError() {
        McpToolResult result = tool.execute(Map.of("sql", "DELETE FROM USERS"));

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("oracle_execute"));
    }

    @Test
    void execute_missingSql_shouldReturnError() {
        McpToolResult result = tool.execute(Map.of());

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("sql"));
    }

    @Test
    void execute_oracleNotEnabled_shouldReturnError() {
        ReflectionTestUtils.setField(tool, "oracleService", null);

        McpToolResult result = tool.execute(Map.of("sql", "SELECT * FROM USERS"));

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("not enabled"));
    }
}