package org.turnright.mysqlmcpserver.registry;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 数据源注册表实现
 * 管理 MySQL 和 SQL Server 的多个 HikariDataSource
 */
@Slf4j
public class JdbcDataSourceRegistry implements DataSourceRegistry<HikariDataSource> {

    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, String> descriptions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> readOnlyStates = new ConcurrentHashMap<>();
    private final String defaultDataSourceName;
    private final String dataSourceType;

    public JdbcDataSourceRegistry(String dataSourceType, String defaultDataSourceName) {
        this.dataSourceType = dataSourceType;
        this.defaultDataSourceName = defaultDataSourceName;
    }

    @Override
    public void register(String name, HikariDataSource dataSource) {
        register(name, dataSource, null, true);
    }

    @Override
    public void register(String name, HikariDataSource dataSource, String description) {
        register(name, dataSource, description, true);
    }

    @Override
    public void register(String name, HikariDataSource dataSource, String description, boolean readOnly) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("DataSource name cannot be null or empty");
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource cannot be null");
        }

        // Atomic put - returns the previous value if one existed
        HikariDataSource existing = dataSources.put(name, dataSource);
        if (existing != null && !existing.isClosed()) {
            log.warn("Overwriting existing datasource: {}", name);
            try {
                existing.close();
            } catch (Exception e) {
                log.error("Error closing existing datasource {}: {}", name, e.getMessage());
            }
        }

        // Store description
        if (description != null && !description.isEmpty()) {
            descriptions.put(name, description);
        }

        // Store readOnly state
        readOnlyStates.put(name, readOnly);

        log.info("Registered JDBC datasource '{}' for {} with description: {}, readOnly: {}",
            name, dataSourceType, description != null ? description : "N/A", readOnly);
    }

    @Override
    public HikariDataSource getDataSource(String name) {
        if (name == null || name.isEmpty()) {
            return getDefaultDataSource();
        }
        HikariDataSource ds = dataSources.get(name);
        if (ds == null) {
            // 未知数据源不再静默回退默认库，避免读/写落在错误数据库
            throw new IllegalArgumentException("Unknown datasource '" + name
                + "'. Available datasources: " + dataSources.keySet());
        }
        return ds;
    }

    @Override
    public Set<String> getDataSourceNames() {
        return dataSources.keySet();
    }

    @Override
    public String getDataSourceDescription(String name) {
        return descriptions.get(name);
    }

    @Override
    public Map<String, String> getDataSourceMetadata() {
        Map<String, String> metadata = new HashMap<>();
        for (String name : dataSources.keySet()) {
            metadata.put(name, descriptions.getOrDefault(name, "No description provided"));
        }
        return metadata;
    }

    @Override
    public HikariDataSource getDefaultDataSource() {
        if (defaultDataSourceName != null && dataSources.containsKey(defaultDataSourceName)) {
            return dataSources.get(defaultDataSourceName);
        }
        // 如果没有指定默认，返回第一个
        if (!dataSources.isEmpty()) {
            return dataSources.values().iterator().next();
        }
        throw new IllegalStateException("No datasources registered for " + dataSourceType);
    }

    @Override
    public String getDefaultDataSourceName() {
        if (defaultDataSourceName != null && dataSources.containsKey(defaultDataSourceName)) {
            return defaultDataSourceName;
        }
        if (!dataSources.isEmpty()) {
            return dataSources.keySet().iterator().next();
        }
        return null;
    }

    @Override
    public boolean hasDataSource(String name) {
        return dataSources.containsKey(name);
    }

    @Override
    public boolean isReadOnly(String name) {
        if (name == null || name.isEmpty()) {
            name = getDefaultDataSourceName();
        }
        // 注册表为空时按只读处理（无数据源可写）
        if (name == null) {
            return true;
        }
        if (!dataSources.containsKey(name)) {
            throw new IllegalArgumentException("Unknown datasource '" + name
                + "'. Available datasources: " + dataSources.keySet());
        }
        // Default to true if not explicitly set
        return readOnlyStates.getOrDefault(name, true);
    }

    @Override
    public String getDataSourceType() {
        return dataSourceType;
    }

    /**
     * 移除并关闭单个数据源（管理界面热删）
     */
    public void remove(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Datasource name cannot be null or empty");
        }
        HikariDataSource removed = dataSources.remove(name);
        descriptions.remove(name);
        readOnlyStates.remove(name);
        if (removed == null) {
            log.warn("Datasource '{}' not found for {}, nothing removed", name, dataSourceType);
            return;
        }
        try {
            if (!removed.isClosed()) {
                removed.close();
            }
            log.info("Removed and closed datasource '{}' for {}", name, dataSourceType);
        } catch (Exception e) {
            log.error("Error closing datasource '{}': {}", name, e.getMessage());
        }
    }

    @PreDestroy
    @Override
    public void closeAll() {
        log.info("Closing all JDBC datasources for {}", dataSourceType);
        for (Map.Entry<String, HikariDataSource> entry : dataSources.entrySet()) {
            try {
                HikariDataSource ds = entry.getValue();
                if (ds != null && !ds.isClosed()) {
                    ds.close();
                    log.info("Closed datasource: {}", entry.getKey());
                }
            } catch (Exception e) {
                log.error("Error closing datasource {}: {}", entry.getKey(), e.getMessage());
            }
        }
        dataSources.clear();
        descriptions.clear();
        readOnlyStates.clear();
    }
}