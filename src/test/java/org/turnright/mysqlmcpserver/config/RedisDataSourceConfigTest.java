package org.turnright.mysqlmcpserver.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisDataSourceConfigTest {

    @Test
    void defaultValues_shouldBeSet() {
        RedisDataSourceConfig config = new RedisDataSourceConfig();

        assertEquals("localhost", config.getHost());
        assertEquals(6379, config.getPort());
        assertEquals(2000, config.getTimeout());
        assertEquals(8, config.getMaxTotal());
        assertEquals(8, config.getMaxIdle());
        assertEquals(0, config.getMinIdle());
        assertTrue(config.isReadOnly());
        assertNull(config.getName());
        assertNull(config.getPassword());
    }

    @Test
    void setters_shouldUpdateValues() {
        RedisDataSourceConfig config = new RedisDataSourceConfig();
        config.setName("primary");
        config.setDescription("Production Redis");
        config.setHost("redis-prod");
        config.setPort(6380);
        config.setPassword("secret");
        config.setTimeout(5000);
        config.setMaxTotal(20);
        config.setMaxIdle(10);
        config.setMinIdle(5);
        config.setReadOnly(false);

        assertEquals("primary", config.getName());
        assertEquals("Production Redis", config.getDescription());
        assertEquals("redis-prod", config.getHost());
        assertEquals(6380, config.getPort());
        assertEquals("secret", config.getPassword());
        assertEquals(5000, config.getTimeout());
        assertEquals(20, config.getMaxTotal());
        assertEquals(10, config.getMaxIdle());
        assertEquals(5, config.getMinIdle());
        assertFalse(config.isReadOnly());
    }
}
