package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.MongoService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongoFindToolTest {

    @Mock
    private MongoService mongoService;

    private MongoFindTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tool = new MongoFindTool(mongoService, objectMapper);
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mongo_find", tool.getName());
    }

    @Test
    void getDescription_shouldContainMongo() {
        assertTrue(tool.getDescription().contains("MongoDB"));
        assertTrue(tool.getDescription().contains("collection"));
    }

    @Test
    void getInputSchema_shouldHaveRequiredFields() {
        Map<String, Object> schema = tool.getInputSchema();
        assertTrue(schema.containsKey("properties"));
        assertTrue(schema.containsKey("required"));

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertTrue(properties.containsKey("database"));
        assertTrue(properties.containsKey("collection"));
        assertTrue(properties.containsKey("filter"));
        assertTrue(properties.containsKey("datasource"));
        // database 非必填：省略时由数据源默认库兜底（F4）
        assertEquals(List.of("collection"), schema.get("required"));
    }

    @Test
    void execute_validQuery_shouldReturnDocuments() throws Exception {
        when(mongoService.getDefaultDatasourceName()).thenReturn("default");
        when(mongoService.findDocuments(isNull(), eq("mydb"), eq("users"), any(), any(), any(), eq(100)))
            .thenReturn(List.of(Map.of("_id", "1", "name", "Alice")));

        McpToolResult result = tool.execute(Map.of(
            "database", "mydb",
            "collection", "users"
        ));

        assertFalse(result.getIsError());
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("success"));
        assertTrue(text.contains("mydb"));
        assertTrue(text.contains("users"));
    }

    @Test
    void execute_withFilter_shouldApplyFilter() throws Exception {
        when(mongoService.getDefaultDatasourceName()).thenReturn("default");
        when(mongoService.findDocuments(isNull(), eq("mydb"), eq("users"), any(Document.class), any(), any(), eq(10)))
            .thenReturn(List.of(Map.of("name", "Alice")));

        McpToolResult result = tool.execute(Map.of(
            "database", "mydb",
            "collection", "users",
            "filter", Map.of("status", "active"),
            "limit", 10
        ));

        assertFalse(result.getIsError());
    }

    @Test
    void execute_missingDatabase_shouldDefaultToConfiguredDatabase() throws Exception {
        // database 可选：省略时以 null 传给 service，由数据源默认库兜底
        when(mongoService.getDefaultDatasourceName()).thenReturn("default");
        when(mongoService.findDocuments(isNull(), isNull(), eq("users"), any(), any(), any(), eq(100)))
            .thenReturn(List.of());

        McpToolResult result = tool.execute(Map.of("collection", "users"));

        assertFalse(result.getIsError());
        verify(mongoService).findDocuments(isNull(), isNull(), eq("users"), any(), any(), any(), eq(100));
    }

    @Test
    void execute_missingCollection_shouldReturnError() {
        McpToolResult result = tool.execute(Map.of("database", "mydb"));

        assertTrue(result.getIsError());
        assertTrue(result.getContent().get(0).getText().contains("collection"));
    }

    @Test
    void execute_withDatasource_shouldUseSpecifiedDs() throws Exception {
        lenient().when(mongoService.getDefaultDatasourceName()).thenReturn("default");
        when(mongoService.findDocuments(eq("secondary"), eq("mydb"), eq("users"), any(), any(), any(), eq(100)))
            .thenReturn(List.of());

        McpToolResult result = tool.execute(Map.of(
            "datasource", "secondary",
            "database", "mydb",
            "collection", "users"
        ));

        assertFalse(result.getIsError());
        verify(mongoService).findDocuments(eq("secondary"), eq("mydb"), eq("users"), any(), any(), any(), eq(100));
    }
}

@ExtendWith(MockitoExtension.class)
class MongoInsertToolTest {

    @Mock
    private MongoService mongoService;

    private MongoInsertTool tool;

    @BeforeEach
    void setUp() {
        tool = new MongoInsertTool(mongoService);
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mongo_insert", tool.getName());
    }

    @Test
    void execute_insertOne_shouldReturnSuccess() throws Exception {
        when(mongoService.getDefaultDatasourceName()).thenReturn("default");
        when(mongoService.insertDocument(isNull(), eq("mydb"), eq("users"), any(Document.class)))
            .thenReturn(1L);

        McpToolResult result = tool.execute(Map.of(
            "database", "mydb",
            "collection", "users",
            "document", Map.of("name", "NewUser", "email", "test@example.com")
        ));

        assertFalse(result.getIsError());
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("success"));
    }

    @Test
    void execute_insertMany_shouldReturnSuccess() throws Exception {
        when(mongoService.getDefaultDatasourceName()).thenReturn("default");
        when(mongoService.insertDocument(isNull(), eq("mydb"), eq("users"), any(Document.class)))
            .thenReturn(1L);

        McpToolResult result = tool.execute(Map.of(
            "database", "mydb",
            "collection", "users",
            "document", Map.of("name", "User1")
        ));

        assertFalse(result.getIsError());
    }
}

@ExtendWith(MockitoExtension.class)
class MongoUpdateToolTest {

    @Mock
    private MongoService mongoService;

    private MongoUpdateTool tool;

    @BeforeEach
    void setUp() {
        tool = new MongoUpdateTool(mongoService);
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mongo_update", tool.getName());
    }

    @Test
    void execute_updateOne_shouldReturnModifiedCount() throws Exception {
        when(mongoService.getDefaultDatasourceName()).thenReturn("default");
        when(mongoService.updateDocuments(isNull(), eq("mydb"), eq("users"), any(Document.class), any(Document.class), eq(false)))
            .thenReturn(1L);

        McpToolResult result = tool.execute(Map.of(
            "database", "mydb",
            "collection", "users",
            "filter", Map.of("_id", "1"),
            "update", Map.of("name", "UpdatedName"),
            "multi", false
        ));

        assertFalse(result.getIsError());
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("1"));
    }
}

@ExtendWith(MockitoExtension.class)
class MongoDeleteToolTest {

    @Mock
    private MongoService mongoService;

    private MongoDeleteTool tool;

    @BeforeEach
    void setUp() {
        tool = new MongoDeleteTool(mongoService);
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mongo_delete", tool.getName());
    }

    @Test
    void execute_deleteOne_shouldReturnDeletedCount() throws Exception {
        when(mongoService.getDefaultDatasourceName()).thenReturn("default");
        when(mongoService.deleteDocuments(isNull(), eq("mydb"), eq("users"), any(Document.class), eq(false)))
            .thenReturn(1L);

        McpToolResult result = tool.execute(Map.of(
            "database", "mydb",
            "collection", "users",
            "filter", Map.of("_id", "1"),
            "multi", false
        ));

        assertFalse(result.getIsError());
    }
}

@ExtendWith(MockitoExtension.class)
class MongoListCollectionsToolTest {

    @Mock
    private MongoService mongoService;

    private MongoListCollectionsTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tool = new MongoListCollectionsTool(mongoService, objectMapper);
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mongo_list_collections", tool.getName());
    }

    @Test
    void execute_shouldReturnCollections() throws Exception {
        when(mongoService.getDefaultDatasourceName()).thenReturn("default");
        when(mongoService.listCollections(isNull(), eq("mydb")))
            .thenReturn(List.of("users", "orders", "products"));

        McpToolResult result = tool.execute(Map.of("database", "mydb"));

        assertFalse(result.getIsError());
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("users"));
        assertTrue(text.contains("orders"));
    }
}

@ExtendWith(MockitoExtension.class)
class MongoAggregateToolTest {

    @Mock
    private MongoService mongoService;

    private MongoAggregateTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tool = new MongoAggregateTool(mongoService, objectMapper);
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mongo_aggregate", tool.getName());
    }

    @Test
    void execute_shouldReturnAggregatedResults() throws Exception {
        when(mongoService.getDefaultDatasourceName()).thenReturn("default");
        when(mongoService.aggregate(isNull(), eq("mydb"), eq("orders"), any(List.class)))
            .thenReturn(List.of(Map.of("_id", "electronics", "total", 1000)));

        McpToolResult result = tool.execute(Map.of(
            "database", "mydb",
            "collection", "orders",
            "pipeline", List.of(Map.of("$group", Map.of("_id", "$category", "total", Map.of("$sum", "$amount"))))
        ));

        assertFalse(result.getIsError());
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("electronics"));
    }
}

@ExtendWith(MockitoExtension.class)
class MongoDatabaseInfoToolTest {

    @Mock
    private MongoService mongoService;

    private MongoDatabaseInfoTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tool = new MongoDatabaseInfoTool(mongoService, objectMapper);
    }

    @Test
    void getName_shouldReturnCorrectName() {
        assertEquals("mongo_database_info", tool.getName());
    }

    @Test
    void execute_shouldReturnDatabaseInfo() throws Exception {
        when(mongoService.getDefaultDatasourceName()).thenReturn("default");
        when(mongoService.getDatabaseInfo(isNull()))
            .thenReturn(Map.of("datasource", "default", "server_version", "7.0.0", "uptime_seconds", 3600L));

        McpToolResult result = tool.execute(Map.of());

        assertFalse(result.getIsError());
        String text = result.getContent().get(0).getText();
        assertTrue(text.contains("7.0.0"));
    }
}