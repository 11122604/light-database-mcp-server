package org.turnright.mysqlmcpserver.registry;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcDataSourceRegistryTest {

    private JdbcDataSourceRegistry registry;
    private HikariDataSource mockDataSource1;
    private HikariDataSource mockDataSource2;

    @BeforeEach
    void setUp() {
        registry = new JdbcDataSourceRegistry("MySQL", "primary");
        mockDataSource1 = mock(HikariDataSource.class);
        mockDataSource2 = mock(HikariDataSource.class);
        when(mockDataSource1.isClosed()).thenReturn(false);
        when(mockDataSource2.isClosed()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        registry.closeAll();
    }

    @Test
    void register_shouldAddDataSource() {
        registry.register("primary", mockDataSource1);

        assertTrue(registry.hasDataSource("primary"));
        assertEquals(mockDataSource1, registry.getDataSource("primary"));
    }

    @Test
    void register_nullName_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
            registry.register(null, mockDataSource1));
    }

    @Test
    void register_emptyName_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
            registry.register("", mockDataSource1));
    }

    @Test
    void register_nullDataSource_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
            registry.register("test", null));
    }

    @Test
    void register_multipleDataSources() {
        registry.register("primary", mockDataSource1);
        registry.register("secondary", mockDataSource2);

        Set<String> names = registry.getDataSourceNames();
        assertEquals(2, names.size());
        assertTrue(names.contains("primary"));
        assertTrue(names.contains("secondary"));
    }

    @Test
    void getDataSource_nullName_shouldReturnDefault() {
        registry.register("primary", mockDataSource1);

        HikariDataSource ds = registry.getDataSource(null);
        assertEquals(mockDataSource1, ds);
    }

    @Test
    void getDataSource_emptyName_shouldReturnDefault() {
        registry.register("primary", mockDataSource1);

        HikariDataSource ds = registry.getDataSource("");
        assertEquals(mockDataSource1, ds);
    }

    @Test
    void getDataSource_unknownName_shouldThrowException() {
        registry.register("primary", mockDataSource1);

        assertThrows(IllegalArgumentException.class, () -> registry.getDataSource("unknown"));
    }

    @Test
    void isReadOnly_unknownName_shouldThrowException() {
        registry.register("primary", mockDataSource1);

        assertThrows(IllegalArgumentException.class, () -> registry.isReadOnly("unknown"));
    }

    @Test
    void getDefaultDataSource_shouldReturnConfiguredDefault() {
        registry.register("secondary", mockDataSource2);
        registry.register("primary", mockDataSource1);

        HikariDataSource ds = registry.getDefaultDataSource();
        assertEquals(mockDataSource1, ds);
    }

    @Test
    void getDefaultDataSource_noDefaultConfigured_shouldReturnFirst() {
        JdbcDataSourceRegistry reg = new JdbcDataSourceRegistry("MySQL", null);
        reg.register("first", mockDataSource1);
        reg.register("second", mockDataSource2);

        HikariDataSource ds = reg.getDefaultDataSource();
        assertEquals(mockDataSource1, ds);
    }

    @Test
    void getDefaultDataSource_noDataSources_shouldThrowException() {
        JdbcDataSourceRegistry reg = new JdbcDataSourceRegistry("MySQL", "primary");

        assertThrows(IllegalStateException.class, () -> reg.getDefaultDataSource());
    }

    @Test
    void getDefaultDataSourceName_shouldReturnCorrectName() {
        registry.register("primary", mockDataSource1);
        registry.register("secondary", mockDataSource2);

        assertEquals("primary", registry.getDefaultDataSourceName());
    }

    @Test
    void getDataSourceType_shouldReturnCorrectType() {
        assertEquals("MySQL", registry.getDataSourceType());
    }

    @Test
    void closeAll_shouldCloseAllDataSources() {
        registry.register("primary", mockDataSource1);
        registry.register("secondary", mockDataSource2);

        registry.closeAll();

        verify(mockDataSource1).close();
        verify(mockDataSource2).close();
        assertTrue(registry.getDataSourceNames().isEmpty());
    }

    @Test
    void closeAll_alreadyClosed_shouldSkip() {
        when(mockDataSource1.isClosed()).thenReturn(true);
        registry.register("primary", mockDataSource1);

        registry.closeAll();

        verify(mockDataSource1, never()).close();
    }

    @Test
    void register_overwrite_shouldCloseExisting() {
        registry.register("primary", mockDataSource1);
        registry.register("primary", mockDataSource2);

        verify(mockDataSource1).close();
        assertEquals(mockDataSource2, registry.getDataSource("primary"));
    }
}