package org.turnright.mysqlmcpserver.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.turnright.mysqlmcpserver.registry.MongoDataSourceRegistry;

import java.util.concurrent.TimeUnit;

/**
 * MongoDB 数据源配置
 * 支持多数据源注册
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MongoProperties.class)
@RequiredArgsConstructor
public class MongoConfig {

    private final MongoProperties properties;

    /**
     * 创建 MongoDB 数据源注册表
     */
    @Bean
    @ConditionalOnProperty(prefix = "database.mongodb", name = "enabled", havingValue = "true")
    public MongoDataSourceRegistry mongoDataSourceRegistry() {
        log.info("Initializing MongoDB datasources registry");

        String defaultName = properties.getDefaultDatasourceName();
        MongoDataSourceRegistry registry = new MongoDataSourceRegistry(defaultName);

        for (MongoDataSourceConfig config : properties.getAllDatasourceConfigs()) {
            // 防御多数据源索引跳号产生的 null 元素
            if (config == null) {
                log.warn("Skipping null datasource config (check datasource index continuity)");
                continue;
            }
            MongoClient client = createMongoClient(config);
            registry.register(config.getName(), client, config.getDescription(), config.isReadOnly(), config.getDatabase());
            log.info("Registered MongoDB datasource: {} (database: {}, description: {}, readOnly: {})",
                config.getName(), config.getDatabase(), config.getDescription(), config.isReadOnly());
        }

        return registry;
    }

    /**
     * 向后兼容：提供默认 MongoClient Bean
     */
    @Bean
    @ConditionalOnProperty(prefix = "database.mongodb", name = "enabled", havingValue = "true")
    public MongoClient mongoClient(MongoDataSourceRegistry registry) {
        return registry.getDefaultDataSource();
    }

    private MongoClient createMongoClient(MongoDataSourceConfig config) {
        String connectionString;
        if (config.getUri() != null && !config.getUri().isEmpty()) {
            connectionString = config.getUri();
        } else {
            StringBuilder sb = new StringBuilder("mongodb://");
            if (config.getUsername() != null && config.getPassword() != null &&
                !config.getUsername().isEmpty() && !config.getPassword().isEmpty()) {
                sb.append(config.getUsername()).append(":").append(config.getPassword()).append("@");
            }
            sb.append(config.getHost()).append(":").append(config.getPort());
            if (config.getDatabase() != null) {
                sb.append("/").append(config.getDatabase());
            }
            connectionString = sb.toString();
        }

        ConnectionString connString = new ConnectionString(connectionString);

        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(connString)
            .applyToConnectionPoolSettings(builder -> builder
                .maxSize(config.getMaxPoolSize())
                .minSize(config.getMinPoolSize())
                .maxConnectionIdleTime(config.getMaxIdleTimeMs(), TimeUnit.MILLISECONDS)
                .maxConnectionLifeTime(config.getMaxConnectionLifeTimeMs(), TimeUnit.MILLISECONDS))
            .build();

        return MongoClients.create(settings);
    }
}