package org.turnright.mysqlmcpserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.turnright.mysqlmcpserver.registry.RedisDataSourceRegistry;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;

/**
 * Redis 缓存服务
 * 支持多数据源切换和 readOnly 安全模式
 */
@Slf4j
@Service
@ConditionalOnBean(name = "redisDataSourceRegistry")
public class RedisService {

    private final RedisDataSourceRegistry registry;

    public RedisService(@Qualifier("redisDataSourceRegistry") RedisDataSourceRegistry registry) {
        this.registry = registry;
    }

    // ==================== 基础操作 ====================

    public String get(String datasource, String key) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.get(key);
        }
    }

    public String set(String datasource, String key, String value) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.set(key, value);
        }
    }

    public Long delete(String datasource, String... keys) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.del(keys);
        }
    }

    public Set<String> keys(String datasource, String pattern) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.keys(pattern);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> scan(String datasource, String cursor, int count) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            ScanParams params = new ScanParams().count(count);
            ScanResult<String> result = jedis.scan(cursor, params);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("cursor", result.getCursor());
            response.put("keys", result.getResult());
            return response;
        }
    }

    public Boolean exists(String datasource, String key) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.exists(key);
        }
    }

    public Long ttl(String datasource, String key) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.ttl(key);
        }
    }

    public Long expire(String datasource, String key, long seconds) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.expire(key, seconds);
        }
    }

    public String type(String datasource, String key) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.type(key);
        }
    }

    // ==================== 字符串操作 ====================

    public Long incr(String datasource, String key) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.incr(key);
        }
    }

    public Long decr(String datasource, String key) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.decr(key);
        }
    }

    // ==================== Hash 操作 ====================

    public String hget(String datasource, String key, String field) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.hget(key, field);
        }
    }

    public Map<String, String> hgetAll(String datasource, String key) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.hgetAll(key);
        }
    }

    // ==================== List 操作 ====================

    public List<String> lrange(String datasource, String key, long start, long stop) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.lrange(key, start, stop);
        }
    }

    // ==================== Set 操作 ====================

    public Set<String> smembers(String datasource, String key) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            return jedis.smembers(key);
        }
    }

    // ==================== 信息操作 ====================

    public String info(String datasource, String section) {
        try (Jedis jedis = registry.getConnection(datasource)) {
            if (section != null && !section.isEmpty()) {
                return jedis.info(section);
            }
            return jedis.info();
        }
    }

    // ==================== 元数据代理 ====================

    public boolean isReadOnly(String datasource) {
        return registry.isReadOnly(datasource != null ? datasource : registry.getDefaultDataSourceName());
    }

    public Set<String> getAvailableDatasources() {
        return registry.getDataSourceNames();
    }

    public String getDefaultDatasourceName() {
        return registry.getDefaultDataSourceName();
    }

    public Map<String, String> getDatasourceMetadata() {
        return registry.getDataSourceMetadata();
    }
}
