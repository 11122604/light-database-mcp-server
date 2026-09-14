package org.turnright.mysqlmcpserver.registry;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Elasticsearch 数据源注册表实现
 */
@Slf4j
public class ElasticsearchDataSourceRegistry implements DataSourceRegistry<ElasticsearchClient> {

    private final Map<String, ElasticsearchClient> clients = new ConcurrentHashMap<>();
    private final Map<String, String> descriptions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> readOnlyStates = new ConcurrentHashMap<>();
    private final String defaultDataSourceName;

    public ElasticsearchDataSourceRegistry(String defaultDataSourceName) {
        this.defaultDataSourceName = defaultDataSourceName;
    }

    @Override
    public void register(String name, ElasticsearchClient client) {
        register(name, client, null, true);
    }

    @Override
    public void register(String name, ElasticsearchClient client, String description) {
        register(name, client, description, true);
    }

    @Override
    public void register(String name, ElasticsearchClient client, String description, boolean readOnly) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("ElasticsearchClient name cannot be null or empty");
        }
        if (client == null) {
            throw new IllegalArgumentException("ElasticsearchClient cannot be null");
        }

        // Atomic put - returns the previous value if one existed
        ElasticsearchClient existing = clients.put(name, client);
        if (existing != null) {
            log.warn("Overwriting existing Elasticsearch client: {}", name);
        }

        // Store description
        if (description != null && !description.isEmpty()) {
            descriptions.put(name, description);
        }

        // Store readOnly state
        readOnlyStates.put(name, readOnly);

        log.info("Registered Elasticsearch datasource '{}' with description: {}, readOnly: {}",
            name, description != null ? description : "N/A", readOnly);
    }

    @Override
    public ElasticsearchClient getDataSource(String name) {
        if (name == null || name.isEmpty()) {
            return getDefaultDataSource();
        }
        ElasticsearchClient client = clients.get(name);
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
    public ElasticsearchClient getDefaultDataSource() {
        if (defaultDataSourceName != null && clients.containsKey(defaultDataSourceName)) {
            return clients.get(defaultDataSourceName);
        }
        if (!clients.isEmpty()) {
            return clients.values().iterator().next();
        }
        throw new IllegalStateException("No Elasticsearch datasources registered");
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
        return "Elasticsearch";
    }

    /**
     * 移除单个数据源映射（管理界面热删）。
     * 底层 RestClient/Transport 由 ElasticsearchConfig 在应用关闭时统一关闭，
     * 此处移除后该 client 不再被路由，避免重复关闭导致误伤。
     */
    public void remove(String name) {
        ElasticsearchClient removed = clients.remove(name);
        descriptions.remove(name);
        readOnlyStates.remove(name);
        if (removed == null) {
            log.warn("Elasticsearch datasource '{}' not found", name);
            return;
        }
        log.info("Removed Elasticsearch datasource '{}'", name);
    }

    /**
     * ElasticsearchClient 底层 RestClient/Transport 由 ElasticsearchConfig 的
     * shutdownElasticsearchClients() 统一关闭，此处仅清理注册表引用，避免重复关闭。
     */
    @PreDestroy
    @Override
    public void closeAll() {
        log.info("Clearing Elasticsearch datasource registry");
        clients.clear();
        descriptions.clear();
        readOnlyStates.clear();
    }
}