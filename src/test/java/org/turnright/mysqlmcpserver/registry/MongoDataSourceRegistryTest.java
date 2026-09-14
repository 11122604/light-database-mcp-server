package org.turnright.mysqlmcpserver.registry;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MongoDataSourceRegistryTest {

    private MongoDataSourceRegistry registry;
    private MongoClient mockClient1;
    private MongoClient mockClient2;

    @BeforeEach
    void setUp() {
        registry = new MongoDataSourceRegistry("primary");
        mockClient1 = mock(MongoClient.class);
        mockClient2 = mock(MongoClient.class);
    }

    @AfterEach
    void tearDown() {
        registry.closeAll();
    }

    @Test
    void register_shouldAddClient() {
        registry.register("primary", mockClient1);

        assertTrue(registry.hasDataSource("primary"));
        assertEquals(mockClient1, registry.getDataSource("primary"));
    }

    @Test
    void register_nullName_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
            registry.register(null, mockClient1));
    }

    @Test
    void register_emptyName_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
            registry.register("", mockClient1));
    }

    @Test
    void register_nullClient_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
            registry.register("test", null));
    }

    @Test
    void register_multipleClients() {
        registry.register("primary", mockClient1);
        registry.register("secondary", mockClient2);

        Set<String> names = registry.getDataSourceNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("primary"));
        assertTrue(names.contains("secondary"));
    }

    @Test
    void getDataSource_nullName_shouldReturnDefault() {
        registry.register("primary", mockClient1);

        MongoClient client = registry.getDataSource(null);
        assertEquals(mockClient1, client);
    }

    @Test
    void getDataSource_unknownName_shouldThrowException() {
        registry.register("primary", mockClient1);

        assertThrows(IllegalArgumentException.class, () -> registry.getDataSource("unknown"));
    }

    @Test
    void isReadOnly_unknownName_shouldThrowException() {
        registry.register("primary", mockClient1);

        assertThrows(IllegalArgumentException.class, () -> registry.isReadOnly("unknown"));
    }

    @Test
    void getDefaultDataSource_shouldReturnConfiguredDefault() {
        registry.register("secondary", mockClient2);
        registry.register("primary", mockClient1);

        MongoClient client = registry.getDefaultDataSource();
        assertEquals(mockClient1, client);
    }

    @Test
    void getDefaultDataSource_noDefaultConfigured_shouldReturnFirst() {
        MongoDataSourceRegistry reg = new MongoDataSourceRegistry(null);
        reg.register("first", mockClient1);
        reg.register("second", mockClient2);

        MongoClient client = reg.getDefaultDataSource();
        assertEquals(mockClient1, client);
    }

    @Test
    void getDefaultDataSource_noClients_shouldThrowException() {
        MongoDataSourceRegistry reg = new MongoDataSourceRegistry("primary");

        assertThrows(IllegalStateException.class, () -> reg.getDefaultDataSource());
    }

    @Test
    void getDefaultDataSourceName_shouldReturnCorrectName() {
        registry.register("primary", mockClient1);

        assertEquals("primary", registry.getDefaultDataSourceName());
    }

    @Test
    void getDataSourceType_shouldReturnMongoDB() {
        assertEquals("MongoDB", registry.getDataSourceType());
    }

    @Test
    void closeAll_shouldCloseAllClients() {
        registry.register("primary", mockClient1);
        registry.register("secondary", mockClient2);

        registry.closeAll();

        verify(mockClient1).close();
        verify(mockClient2).close();
        assertTrue(registry.getDataSourceNames().isEmpty());
    }

    @Test
    void register_overwrite_shouldCloseExisting() {
        registry.register("primary", mockClient1);
        registry.register("primary", mockClient2);

        verify(mockClient1).close();
        assertEquals(mockClient2, registry.getDataSource("primary"));
    }

    @Test
    void register_withDefaultDatabase_shouldStoreIt() {
        registry.register("primary", mockClient1, "Primary", true, "main_db");

        assertEquals("main_db", registry.getDefaultDatabase("primary"));
        // 未注册的数据源名抛错（与 getDataSource 语义一致）
        assertThrows(IllegalArgumentException.class, () -> registry.getDefaultDatabase("secondary"));
    }

    @Test
    void getDefaultDatabase_unknownName_shouldThrowException() {
        registry.register("primary", mockClient1);

        assertThrows(IllegalArgumentException.class, () -> registry.getDefaultDatabase("unknown"));
    }

    @Test
    void getDefaultDatabase_nullName_shouldReturnConfiguredDefault() {
        registry.register("primary", mockClient1, "Primary", true, "main_db");

        assertEquals("main_db", registry.getDefaultDatabase(null));
    }
}