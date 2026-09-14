package org.turnright.mysqlmcpserver.registry;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.junit.jupiter.api.Assertions.*;

class RedisDataSourceRegistryTest {

    private JedisPool createMockPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(1);
        poolConfig.setMaxIdle(1);
        poolConfig.setMinIdle(0);
        // Pool won't connect without a real Redis, but we only test registry logic
        return new JedisPool(poolConfig, "localhost", 6379, 2000);
    }

    @Test
    void constructor_shouldSetNameAndDefault() {
        RedisDataSourceRegistry registry = new RedisDataSourceRegistry("Redis", "default");

        assertEquals("default", registry.getDefaultDataSourceName());
    }

    @Test
    void register_shouldAddDatasourceWithMetadata() {
        RedisDataSourceRegistry registry = new RedisDataSourceRegistry("Redis", "primary");
        JedisPool pool = createMockPool();

        registry.register("primary", pool, "Production Redis", true);

        assertTrue(registry.getDataSourceNames().contains("primary"));
        assertEquals("Production Redis", registry.getDataSourceMetadata().get("primary"));
        assertTrue(registry.isReadOnly("primary"));
    }

    @Test
    void register_multipleDatasources_shouldTrackIndependently() {
        RedisDataSourceRegistry registry = new RedisDataSourceRegistry("Redis", "primary");
        JedisPool pool1 = createMockPool();
        JedisPool pool2 = createMockPool();

        registry.register("primary", pool1, "Main Cache", true);
        registry.register("cache", pool2, "Secondary Cache", false);

        assertEquals(2, registry.getDataSourceNames().size());
        assertEquals("Main Cache", registry.getDataSourceMetadata().get("primary"));
        assertEquals("Secondary Cache", registry.getDataSourceMetadata().get("cache"));
        assertTrue(registry.isReadOnly("primary"));
        assertFalse(registry.isReadOnly("cache"));
    }

    @Test
    void isReadOnly_unknownDatasource_shouldThrowException() {
        RedisDataSourceRegistry registry = new RedisDataSourceRegistry("Redis", "default");

        assertThrows(IllegalArgumentException.class, () -> registry.isReadOnly("nonexistent"));
    }
}
