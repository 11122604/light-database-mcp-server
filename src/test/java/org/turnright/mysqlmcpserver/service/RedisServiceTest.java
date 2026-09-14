package org.turnright.mysqlmcpserver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.turnright.mysqlmcpserver.registry.RedisDataSourceRegistry;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisServiceTest {

    @Mock
    private RedisDataSourceRegistry registry;

    @Mock
    private Jedis jedis;

    private RedisService service;

    @BeforeEach
    void setUp() {
        when(registry.getConnection(any())).thenReturn(jedis);
        when(registry.getDefaultDataSourceName()).thenReturn("primary");
        service = new RedisService(registry);
    }

    // ==================== 基础操作 ====================

    @Test
    void get_shouldReturnValue() {
        when(jedis.get("mykey")).thenReturn("myvalue");

        String result = service.get(null, "mykey");

        assertEquals("myvalue", result);
        verify(jedis, atLeastOnce()).close();
    }

    @Test
    void get_nonexistent_shouldReturnNull() {
        when(jedis.get("missing")).thenReturn(null);

        String result = service.get(null, "missing");

        assertNull(result);
    }

    @Test
    void set_shouldReturnOK() {
        when(jedis.set("mykey", "myvalue")).thenReturn("OK");

        String result = service.set(null, "mykey", "myvalue");

        assertEquals("OK", result);
    }

    @Test
    void delete_shouldReturnCount() {
        when(jedis.del("key1", "key2")).thenReturn(2L);

        Long result = service.delete(null, "key1", "key2");

        assertEquals(2L, result);
    }

    @Test
    void keys_shouldReturnMatching() {
        when(jedis.keys("user:*")).thenReturn(Set.of("user:1", "user:2"));

        Set<String> result = service.keys(null, "user:*");

        assertEquals(2, result.size());
        assertTrue(result.contains("user:1"));
    }

    @Test
    void ttl_shouldReturnSeconds() {
        when(jedis.ttl("mykey")).thenReturn(3600L);

        Long result = service.ttl(null, "mykey");

        assertEquals(3600L, result);
    }

    @Test
    void ttl_noExpiry_shouldReturnNegativeOne() {
        when(jedis.ttl("persistent")).thenReturn(-1L);

        Long result = service.ttl(null, "persistent");

        assertEquals(-1L, result);
    }

    @Test
    void type_shouldReturnDataType() {
        when(jedis.type("mykey")).thenReturn("string");

        String result = service.type(null, "mykey");

        assertEquals("string", result);
    }

    // ==================== 字符串操作 ====================

    @Test
    void incr_shouldReturnNewValue() {
        when(jedis.incr("counter")).thenReturn(11L);

        Long result = service.incr(null, "counter");

        assertEquals(11L, result);
    }

    @Test
    void decr_shouldReturnNewValue() {
        when(jedis.decr("counter")).thenReturn(9L);

        Long result = service.decr(null, "counter");

        assertEquals(9L, result);
    }

    // ==================== Hash 操作 ====================

    @Test
    void hget_shouldReturnFieldValue() {
        when(jedis.hget("user:1", "name")).thenReturn("Alice");

        String result = service.hget(null, "user:1", "name");

        assertEquals("Alice", result);
    }

    @Test
    void hgetAll_shouldReturnAllFields() {
        when(jedis.hgetAll("user:1")).thenReturn(Map.of("name", "Alice", "age", "30"));

        Map<String, String> result = service.hgetAll(null, "user:1");

        assertEquals(2, result.size());
        assertEquals("Alice", result.get("name"));
        assertEquals("30", result.get("age"));
    }

    // ==================== List 操作 ====================

    @Test
    void lrange_shouldReturnElements() {
        when(jedis.lrange("mylist", 0, -1)).thenReturn(java.util.List.of("a", "b", "c"));

        var result = service.lrange(null, "mylist", 0, -1);

        assertEquals(3, result.size());
        assertEquals("a", result.get(0));
        assertEquals("c", result.get(2));
    }

    // ==================== Set 操作 ====================

    @Test
    void smembers_shouldReturnMembers() {
        when(jedis.smembers("myset")).thenReturn(Set.of("member1", "member2"));

        Set<String> result = service.smembers(null, "myset");

        assertEquals(2, result.size());
        assertTrue(result.contains("member1"));
    }

    // ==================== 信息操作 ====================

    @Test
    void info_shouldReturnServerInfo() {
        String mockInfo = "# Server\nredis_version:7.0.0\n";
        when(jedis.info()).thenReturn(mockInfo);

        String result = service.info(null, null);

        assertTrue(result.contains("redis_version"));
    }

    @Test
    void info_withSection_shouldReturnSectionInfo() {
        when(jedis.info("memory")).thenReturn("# Memory\nused_memory:1000000\n");

        String result = service.info(null, "memory");

        assertTrue(result.contains("used_memory"));
    }

    // ==================== 元数据代理 ====================

    @Test
    void isReadOnly_shouldDelegateToRegistry() {
        when(registry.isReadOnly("primary")).thenReturn(true);

        assertTrue(service.isReadOnly("primary"));
    }

    @Test
    void getAvailableDatasources_shouldDelegateToRegistry() {
        when(registry.getDataSourceNames()).thenReturn(Set.of("primary"));

        assertEquals(Set.of("primary"), service.getAvailableDatasources());
    }

    @Test
    void getDefaultDatasourceName_shouldDelegateToRegistry() {
        assertEquals("primary", service.getDefaultDatasourceName());
    }

    @Test
    void getDatasourceMetadata_shouldDelegateToRegistry() {
        when(registry.getDataSourceMetadata()).thenReturn(Map.of("primary", "Test"));

        assertEquals(Map.of("primary", "Test"), service.getDatasourceMetadata());
    }
}
