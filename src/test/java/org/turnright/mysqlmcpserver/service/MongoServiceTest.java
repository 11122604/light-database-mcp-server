package org.turnright.mysqlmcpserver.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.FindIterable;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.turnright.mysqlmcpserver.registry.MongoDataSourceRegistry;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongoServiceTest {

    @Mock
    private MongoDataSourceRegistry registry;

    @Mock
    private MongoClient mongoClient;

    @Mock
    private MongoDatabase database;

    @Mock
    private MongoCollection<Document> collection;

    @Mock
    private FindIterable<Document> findIterable;

    @Mock
    private MongoCursor<Document> cursor;

    private MongoService mongoService;

    @BeforeEach
    void setUp() {
        mongoService = new MongoService(registry);
    }

    @Test
    void listDatabases_shouldReturnDatabaseNames() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");

        MongoIterable<String> dbIterable = mock(MongoIterable.class);
        MongoCursor<String> dbCursor = mock(MongoCursor.class);
        when(mongoClient.listDatabaseNames()).thenReturn(dbIterable);
        when(dbIterable.iterator()).thenReturn(dbCursor);
        when(dbCursor.hasNext()).thenReturn(true, true, true, false);
        when(dbCursor.next()).thenReturn("admin", "test", "mydb");

        List<String> databases = mongoService.listDatabases();

        assertEquals(3, databases.size());
        assertTrue(databases.contains("test"));
    }

    @Test
    void listCollections_shouldReturnCollectionNames() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);

        MongoIterable<String> collIterable = mock(MongoIterable.class);
        MongoCursor<String> collCursor = mock(MongoCursor.class);
        when(database.listCollectionNames()).thenReturn(collIterable);
        when(collIterable.iterator()).thenReturn(collCursor);
        when(collCursor.hasNext()).thenReturn(true, true, false);
        when(collCursor.next()).thenReturn("users", "orders");

        List<String> collections = mongoService.listCollections("mydb");

        assertEquals(2, collections.size());
        assertTrue(collections.contains("users"));
    }

    @Test
    void findDocuments_withFilter_shouldReturnResults() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);
        when(collection.find(any(Document.class))).thenReturn(findIterable);
        when(findIterable.limit(10)).thenReturn(findIterable);
        when(findIterable.iterator()).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(
            new Document("_id", "1").append("name", "Alice"),
            new Document("_id", "2").append("name", "Bob")
        );

        List<Map<String, Object>> results = mongoService.findDocuments(
            "mydb", "users",
            new Document("status", "active"),
            null, null, 10
        );

        assertEquals(2, results.size());
        assertEquals("Alice", results.get(0).get("name"));
    }

    @Test
    void findDocuments_withoutFilter_shouldReturnAll() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);
        when(collection.find()).thenReturn(findIterable);
        when(findIterable.iterator()).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(new Document("name", "Test"));

        List<Map<String, Object>> results = mongoService.findDocuments("mydb", "users", null, null, null, 0);

        assertEquals(1, results.size());
    }

    @Test
    void countDocuments_shouldReturnCount() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);
        when(collection.countDocuments(any(Document.class))).thenReturn(100L);

        long count = mongoService.countDocuments("mydb", "users", new Document("active", true));

        assertEquals(100L, count);
    }

    @Test
    void insertDocument_shouldReturnOne() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        Document doc = new Document("name", "NewUser");
        long result = mongoService.insertDocument("mydb", "users", doc);

        assertEquals(1L, result);
        verify(collection).insertOne(doc);
    }

    @Test
    void insertDocuments_shouldReturnCount() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        List<Document> docs = Arrays.asList(
            new Document("name", "User1"),
            new Document("name", "User2")
        );
        long result = mongoService.insertDocuments("mydb", "users", docs);

        assertEquals(2L, result);
        verify(collection).insertMany(docs);
    }

    @Test
    void updateDocuments_singleUpdate_shouldReturnModifiedCount() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        UpdateResult updateResult = mock(UpdateResult.class);
        when(collection.updateOne(any(Document.class), any(Document.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(1L);

        long modified = mongoService.updateDocuments(
            "mydb", "users",
            new Document("_id", "1"),
            new Document("name", "Updated"),
            false
        );

        assertEquals(1L, modified);
    }

    @Test
    void updateDocuments_multiUpdate_shouldReturnModifiedCount() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        UpdateResult updateResult = mock(UpdateResult.class);
        when(collection.updateMany(any(Document.class), any(Document.class))).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(5L);

        long modified = mongoService.updateDocuments(
            "mydb", "users",
            new Document("status", "inactive"),
            new Document("status", "active"),
            true
        );

        assertEquals(5L, modified);
    }

    @Test
    void deleteDocuments_singleDelete_shouldReturnDeletedCount() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        DeleteResult deleteResult = mock(DeleteResult.class);
        when(collection.deleteOne(any(Document.class))).thenReturn(deleteResult);
        when(deleteResult.getDeletedCount()).thenReturn(1L);

        long deleted = mongoService.deleteDocuments("mydb", "users", new Document("_id", "1"), false);

        assertEquals(1L, deleted);
    }

    @Test
    void deleteDocuments_multiDelete_shouldReturnDeletedCount() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        DeleteResult deleteResult = mock(DeleteResult.class);
        when(collection.deleteMany(any(Document.class))).thenReturn(deleteResult);
        when(deleteResult.getDeletedCount()).thenReturn(10L);

        long deleted = mongoService.deleteDocuments("mydb", "users", new Document("status", "expired"), true);

        assertEquals(10L, deleted);
    }

    @Test
    void aggregate_shouldReturnAggregatedResults() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("orders")).thenReturn(collection);

        AggregateIterable<Document> aggIterable = mock(AggregateIterable.class);
        MongoCursor<Document> aggCursor = mock(MongoCursor.class);
        when(collection.aggregate(any(List.class))).thenReturn(aggIterable);
        when(aggIterable.iterator()).thenReturn(aggCursor);
        when(aggCursor.hasNext()).thenReturn(true, false);
        when(aggCursor.next()).thenReturn(new Document("_id", "electronics").append("total", 1000));

        List<Map<String, Object>> results = mongoService.aggregate("mydb", "orders",
            Arrays.asList(new Document("$group", new Document("_id", "$category").append("total", new Document("$sum", "$amount")))));

        assertEquals(1, results.size());
        assertEquals("electronics", results.get(0).get("_id"));
    }

    @Test
    void getCollectionStats_shouldReturnStats() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);

        Document stats = new Document("ns", "mydb.users")
            .append("count", 100L)
            .append("size", 5000L)
            .append("storageSize", 4000L)
            .append("totalIndexSize", 2000L);
        when(database.runCommand(any(Document.class))).thenReturn(stats);

        Map<String, Object> result = mongoService.getCollectionStats("mydb", "users");

        assertEquals("default", result.get("datasource"));
        assertEquals("mydb.users", result.get("ns"));
        assertEquals(100L, result.get("count"));
    }

    @Test
    void getIndexNames_shouldReturnIndexNames() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        ListIndexesIterable<Document> idxIterable = mock(ListIndexesIterable.class);
        MongoCursor<Document> idxCursor = mock(MongoCursor.class);
        when(collection.listIndexes()).thenReturn(idxIterable);
        when(idxIterable.iterator()).thenReturn(idxCursor);
        when(idxCursor.hasNext()).thenReturn(true, true, false);
        when(idxCursor.next()).thenReturn(
            new Document("name", "_id_"),
            new Document("name", "name_1")
        );

        List<String> indexes = mongoService.getIndexNames("mydb", "users");

        assertEquals(2, indexes.size());
        assertTrue(indexes.contains("_id_"));
        assertTrue(indexes.contains("name_1"));
    }

    @Test
    void getDatabaseInfo_shouldReturnServerInfo() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");

        MongoDatabase adminDb = mock(MongoDatabase.class);
        when(mongoClient.getDatabase("admin")).thenReturn(adminDb);

        Document serverStatus = new Document("version", "7.0.0")
            .append("uptime", 3600L);
        when(adminDb.runCommand(any(Document.class))).thenReturn(serverStatus);

        Map<String, Object> info = mongoService.getDatabaseInfo();

        assertEquals("default", info.get("datasource"));
        assertEquals("7.0.0", info.get("server_version"));
        assertEquals(3600L, info.get("uptime_seconds"));
    }

    @Test
    void getAvailableDatasources_shouldReturnFromRegistry() {
        Set<String> dsNames = Set.of("primary", "secondary");
        when(registry.getDataSourceNames()).thenReturn(dsNames);

        Set<String> result = mongoService.getAvailableDatasources();

        assertEquals(2, result.size());
        assertTrue(result.contains("primary"));
    }

    @Test
    void getDefaultDatasourceName_shouldReturnFromRegistry() {
        when(registry.getDefaultDataSourceName()).thenReturn("primary");

        assertEquals("primary", mongoService.getDefaultDatasourceName());
    }

    @Test
    void withDatasource_shouldUseSpecifiedDataSource() {
        when(registry.getDataSource("custom")).thenReturn(mongoClient);
        MongoIterable<String> dbIterable = mock(MongoIterable.class);
        MongoCursor<String> dbCursor = mock(MongoCursor.class);
        when(mongoClient.listDatabaseNames()).thenReturn(dbIterable);
        when(dbIterable.iterator()).thenReturn(dbCursor);
        when(dbCursor.hasNext()).thenReturn(false);

        mongoService.listDatabases("custom");

        verify(registry).getDataSource("custom");
        verify(registry, never()).getDataSource(null);
    }

    @Test
    void updateDocuments_withOperator_shouldNotWrapInSet() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        UpdateResult updateResult = mock(UpdateResult.class);
        when(updateResult.getModifiedCount()).thenReturn(2L);
        when(collection.updateMany(any(Document.class), any(Document.class))).thenReturn(updateResult);

        Document operatorUpdate = new Document("$inc", new Document("count", 1));
        long modified = mongoService.updateDocuments("mydb", "users",
            new Document("_id", "1"), operatorUpdate, true);

        assertEquals(2L, modified);
        // 顶层含 $ 操作符时不得再包一层 $set（否则 $inc 会失效）
        verify(collection).updateMany(any(Document.class),
            argThat((Document doc) -> doc.containsKey("$inc") && !doc.containsKey("$set")));
    }

    @Test
    void convertDocumentToMap_shouldConvertObjectIdAndDate() {
        Document doc = new Document("_id", new ObjectId("507f1f77bcf86cd799439011"))
            .append("created_at", new java.util.Date(0))
            .append("nested", new Document("oid", new ObjectId("507f1f77bcf86cd799439012")));

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
            mongoService, "convertDocumentToMap", doc);

        assertNotNull(map);
        assertEquals("507f1f77bcf86cd799439011", map.get("_id"));
        // epoch 0 -> 1970-01-01T00:00:00Z
        assertEquals("1970-01-01T00:00:00Z", map.get("created_at"));
        // 嵌套 Document 中的 ObjectId 同样转换
        Map<String, Object> nested = (Map<String, Object>) map.get("nested");
        assertEquals("507f1f77bcf86cd799439012", nested.get("oid"));
    }

    @Test
    void updateDocuments_emptyFilter_shouldThrow() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        // 空 filter 会被 service 层兜底拦截，防止整集合被意外更新
        assertThrows(IllegalArgumentException.class, () ->
            mongoService.updateDocuments("mydb", "users", new Document(), new Document("name", "x"), true));
    }

    @Test
    void deleteDocuments_emptyFilter_shouldThrow() {
        when(registry.getDataSource(null)).thenReturn(mongoClient);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(mongoClient.getDatabase("mydb")).thenReturn(database);
        when(database.getCollection("users")).thenReturn(collection);

        // 空 filter 一律拒绝删除（multi=false 时 deleteOne({}) 也会删任意一条）
        assertThrows(IllegalArgumentException.class, () ->
            mongoService.deleteDocuments("mydb", "users", new Document(), false));
    }
}