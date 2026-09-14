package org.turnright.mysqlmcpserver.registry;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ElasticsearchDataSourceRegistryTest {

    private ElasticsearchDataSourceRegistry registry;
    private ElasticsearchClient mockClient1;
    private ElasticsearchClient mockClient2;

    @BeforeEach
    void setUp() {
        registry = new ElasticsearchDataSourceRegistry("primary");
        mockClient1 = mock(ElasticsearchClient.class);
        mockClient2 = mock(ElasticsearchClient.class);
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

        ElasticsearchClient client = registry.getDataSource(null);
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

        ElasticsearchClient client = registry.getDefaultDataSource();
        assertEquals(mockClient1, client);
    }

    @Test
    void getDefaultDataSource_noDefaultConfigured_shouldReturnFirst() {
        ElasticsearchDataSourceRegistry reg = new ElasticsearchDataSourceRegistry(null);
        reg.register("first", mockClient1);
        reg.register("second", mockClient2);

        ElasticsearchClient client = reg.getDefaultDataSource();
        assertEquals(mockClient1, client);
    }

    @Test
    void getDefaultDataSource_noClients_shouldThrowException() {
        ElasticsearchDataSourceRegistry reg = new ElasticsearchDataSourceRegistry("primary");

        assertThrows(IllegalStateException.class, () -> reg.getDefaultDataSource());
    }

    @Test
    void getDefaultDataSourceName_shouldReturnCorrectName() {
        registry.register("primary", mockClient1);

        assertEquals("primary", registry.getDefaultDataSourceName());
    }

    @Test
    void getDataSourceType_shouldReturnElasticsearch() {
        assertEquals("Elasticsearch", registry.getDataSourceType());
    }

    @Test
    void closeAll_shouldNotThrow() {
        registry.register("primary", mockClient1);
        registry.register("secondary", mockClient2);

        registry.closeAll();

        assertTrue(registry.getDataSourceNames().isEmpty());
    }

    @Test
    void register_overwrite_shouldNotThrow() {
        registry.register("primary", mockClient1);
        registry.register("primary", mockClient2);

        assertEquals(mockClient2, registry.getDataSource("primary"));
    }
}