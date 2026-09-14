package org.turnright.mysqlmcpserver.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataSourceConfigTest {

    @Test
    void defaultValues_shouldBeSet() {
        DataSourceConfig config = new DataSourceConfig();

        assertTrue(config.isReadOnly());  // 默认只读
        assertEquals(10, config.getMaxPoolSize());
        assertEquals(2, config.getMinIdle());
        assertEquals(300000, config.getIdleTimeout());
        assertEquals(30000, config.getConnectionTimeout());
    }

    @Test
    void readOnly_whenSetFalse_shouldReturnFalse() {
        DataSourceConfig config = new DataSourceConfig();
        config.setReadOnly(false);
        assertFalse(config.isReadOnly());
    }

    @Test
    void setters_shouldUpdateValues() {
        DataSourceConfig config = new DataSourceConfig();
        config.setName("test");
        config.setDescription("Test datasource");
        config.setUrl("jdbc:mysql://localhost:3306/test");
        config.setUsername("user");
        config.setPassword("pass");
        config.setMaxPoolSize(20);
        config.setMinIdle(5);
        config.setReadOnly(false);

        assertEquals("test", config.getName());
        assertEquals("Test datasource", config.getDescription());
        assertEquals("jdbc:mysql://localhost:3306/test", config.getUrl());
        assertEquals("user", config.getUsername());
        assertEquals("pass", config.getPassword());
        assertEquals(20, config.getMaxPoolSize());
        assertEquals(5, config.getMinIdle());
        assertFalse(config.isReadOnly());
    }
}