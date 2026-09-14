package org.turnright.mysqlmcpserver.registry;

import com.mongodb.client.MongoClient;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MongoDB 数据源注册表实现
 */
@Slf4j
public class MongoDataSourceRegistry implements DataSourceRegistry<MongoClient> {

    private final Map<String, MongoClient> clients = new ConcurrentHashMap<>();
    private final Map<String, String> descriptions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> readOnlyStates = new ConcurrentHashMap<>();
    private final Map<String, String> defaultDatabases = new ConcurrentHashMap<>();
    private final String defaultDataSourceName;

    public MongoDataSourceRegistry(String defaultDataSourceName) {
        this.defaultDataSourceName = defaultDataSourceName;
    }

    @Override
    public void register(String name, MongoClient client) {
        register(name, client, null, true);
    }

    @Override
    public void register(String name, MongoClient client, String description) {
        register(name, client, description, true);
    }

    @Override
    public void register(String name, MongoClient client, String description, boolean readOnly) {
        register(name, client, description, readOnly, null);
    }

    /**
     * 注册数据源并记录其默认数据库（未显式指定 database 时使用该库）
     */
    public void register(String name, MongoClient client, String description, boolean readOnly, String defaultDatabase) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("MongoClient name cannot be null or empty");
        }
        if (client == null) {
            throw new IllegalArgumentException("MongoClient cannot be null");
        }

        // Atomic put - returns the previous value if one existed
        MongoClient existing = clients.put(name, client);
        if (existing != null) {
            log.warn("Overwriting existing MongoDB client: {}", name);
            try {
                existing.close();
            } catch (Exception e) {
                log.error("Error closing existing MongoDB client {}: {}", name, e.getMessage());
            }
        }

        // Store description
        if (description != null && !description.isEmpty()) {
            descriptions.put(name, description);
        }

        // Store readOnly state
        readOnlyStates.put(name, readOnly);

        // Store default database
        if (defaultDatabase != null && !defaultDatabase.isEmpty()) {
            defaultDatabases.put(name, defaultDatabase);
        }

        log.info("Registered MongoDB datasource '{}' with description: {}, readOnly: {}",
            name, description != null ? description : "N/A", readOnly);
    }

    @Override
    public MongoClient getDataSource(String name) {
        if (name == null || name.isEmpty()) {
            return getDefaultDataSource();
        }
        MongoClient client = clients.get(name);
        if (client == null) {
            // 未知数据源不再静默回退默认库，避免读/写落在错误数据库
            throw new IllegalArgumentException("Unknown datasource '" + name
                + "'. Available datasources: " + clients.keySet());
        }
        return client;
    }

    @Override
    public Set<String> getDataSourceNames() {
        return clients.keySet();
    }

    @Override
    public String getDataSourceDescription(String name) {
        return descriptions.get(name);
    }

    @Override
    public Map<String, String> getDataSourceMetadata() {
        Map<String, String> metadata = new HashMap<>();
        for (String name : clients.keySet()) {
            metadata.put(name, descriptions.getOrDefault(name, "No description provided"));
        }
        return metadata;
    }

    @Override
    public MongoClient getDefaultDataSource() {
        if (defaultDataSourceName != null && clients.containsKey(defaultDataSourceName)) {
            return clients.get(defaultDataSourceName);
        }
        if (!clients.isEmpty()) {
            return clients.values().iterator().next();
        }
        throw new IllegalStateException("No MongoDB datasources registered");
    }

    @Override
    public String getDefaultDataSourceName() {
        if (defaultDataSourceName != null && clients.containsKey(defaultDataSourceName)) {
            return defaultDataSourceName;
        }
        if (!clients.isEmpty()) {
            return clients.keySet().iterator().next();
        }
        return null;
    }

    @Override
    public boolean hasDataSource(String name) {
        return clients.containsKey(name);
    }

    /**
     * 获取数据源配置的默认数据库名
     * @param dataSourceName 数据源名称（null 使用默认数据源）
     * @return 默认数据库名，未配置时返回 null
     */
    public String getDefaultDatabase(String dataSourceName) {
        if (dataSourceName == null || dataSourceName.isEmpty()) {
            dataSourceName = getDefaultDataSourceName();
        }
        if (dataSourceName == null) {
            return null;
        }
        if (!clients.containsKey(dataSourceName)) {
            throw new IllegalArgumentException("Unknown datasource '" + dataSourceName
                + "'. Available datasources: " + clients.keySet());
        }
        return defaultDatabases.get(dataSourceName);
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
        if (!clients.containsKey(name)) {
            throw new IllegalArgumentException("Unknown datasource '" + name
                + "'. Available datasources: " + clients.keySet());
        }
        // Default to true if not explicitly set
        return readOnlyStates.getOrDefault(name, true);
    }

    @Override
    public String getDataSourceType() {
        return "MongoDB";
    }

    /**
     * 移除并关闭单个 MongoClient（管理界面热删）
     */
    public void remove(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Datasource name cannot be null or empty");
        }
        MongoClient removed = clients.remove(name);
        descriptions.remove(name);
        readOnlyStates.remove(name);
        defaultDatabases.remove(name);
        if (removed == null) {
            log.warn("MongoDB datasource '{}' not found, nothing removed", name);
            return;
        }
        try {
            removed.close();
            log.info("Removed and closed MongoDB datasource '{}'", name);
        } catch (Exception e) {
            log.error("Error closing MongoDB client '{}': {}", name, e.getMessage());
        }
    }

    @PreDestroy
    @Override
    public void closeAll() {
        log.info("Closing all MongoDB clients");
        for (Map.Entry<String, MongoClient> entry : clients.entrySet()) {
            try {
                MongoClient client = entry.getValue();
                if (client != null) {
                    client.close();
                    log.info("Closed MongoDB datasource: {}", entry.getKey());
                }
            } catch (Exception e) {
                log.error("Error closing MongoDB client {}: {}", entry.getKey(), e.getMessage());
            }
        }
        clients.clear();
        descriptions.clear();
        readOnlyStates.clear();
        defaultDatabases.clear();
    }
}