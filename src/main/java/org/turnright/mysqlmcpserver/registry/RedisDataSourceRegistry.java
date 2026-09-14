package org.turnright.mysqlmcpserver.registry;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Redis 数据源注册表
 * 管理 JedisPool 连接池和数据源元数据
 */
@Slf4j
public class RedisDataSourceRegistry {

    private final String dataSourceType;
    private final String defaultDataSourceName;
    private final Map<String, JedisPool> pools = new LinkedHashMap<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();
    private final Map<String, Boolean> readOnlyFlags = new LinkedHashMap<>();

    public RedisDataSourceRegistry(String dataSourceType, String defaultDataSourceName) {
        this.dataSourceType = dataSourceType;
        this.defaultDataSourceName = defaultDataSourceName;
    }

    /**
     * 注册数据源
     */
    public void register(String name, JedisPool pool, String description, boolean readOnly) {
        pools.put(name, pool);
        metadata.put(name, description);
        readOnlyFlags.put(name, readOnly);
        log.info("Registered {} datasource '{}' with description: {}, readOnly: {}", dataSourceType, name, description, readOnly);
    }

    /**
     * 获取 Redis 连接
     */
    public Jedis getConnection(String name) {
        String dsName = name != null ? name : defaultDataSourceName;
        JedisPool pool = pools.get(dsName);
        if (pool == null) {
            throw new IllegalArgumentException("Datasource '" + dsName + "' not found");
        }
        return pool.getResource();
    }

    /**
     * 获取所有数据源名称
     */
    public Set<String> getDataSourceNames() {
        return pools.keySet();
    }

    /**
     * 获取数据源元数据（描述）
     */
    public Map<String, String> getDataSourceMetadata() {
        return metadata;
    }

    /**
     * 检查数据源是否为只读模式
     */
    public boolean isReadOnly(String name) {
        String dsName = name != null ? name : defaultDataSourceName;
        if (!pools.containsKey(dsName)) {
            throw new IllegalArgumentException("Datasource '" + dsName + "' not found");
        }
        return readOnlyFlags.getOrDefault(dsName, true);
    }

    /**
     * 获取默认数据源名称
     */
    public String getDefaultDataSourceName() {
        return defaultDataSourceName;
    }

    public String getDataSourceType() {
        return dataSourceType;
    }

    /**
     * 移除并关闭单个连接池（管理界面热删）
     */
    public void remove(String name) {
        JedisPool removed = pools.remove(name);
        metadata.remove(name);
        readOnlyFlags.remove(name);
        if (removed == null) {
            log.warn("{} datasource '{}' not found", dataSourceType, name);
            return;
        }
        try {
            removed.close();
            log.info("Removed and closed {} datasource '{}'", dataSourceType, name);
        } catch (Exception e) {
            log.error("Error closing {} datasource '{}': {}", dataSourceType, name, e.getMessage());
        }
    }

    /**
     * 关闭所有连接池（应用停止时释放底层连接）
     */
    @PreDestroy
    public void closeAll() {
        log.info("Closing all {} datasources", dataSourceType);
        for (Map.Entry<String, JedisPool> entry : pools.entrySet()) {
            try {
                entry.getValue().close();
                log.info("Closed {} datasource: {}", dataSourceType, entry.getKey());
            } catch (Exception e) {
                log.error("Error closing {} datasource {}: {}", dataSourceType, entry.getKey(), e.getMessage());
            }
        }
        pools.clear();
        metadata.clear();
        readOnlyFlags.clear();
    }
}
