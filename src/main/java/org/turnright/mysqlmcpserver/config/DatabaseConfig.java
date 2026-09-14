package org.turnright.mysqlmcpserver.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.turnright.mysqlmcpserver.registry.JdbcDataSourceRegistry;

import javax.sql.DataSource;

/**
 * MySQL 数据源配置
 * 支持多数据源注册
 *
 * 注意：MySQL 现为可选配置
 * - 设置 MYSQL_ENABLED=true 来启用
 * - 配置 MYSQL_DATASOURCES_N_*（如 MYSQL_DATASOURCES_0_URL）
 * - 未配置时不创建数据源，list_datasources 将返回提示信息
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DatabaseProperties.class)
@RequiredArgsConstructor
public class DatabaseConfig {

    private final DatabaseProperties properties;

    /**
     * 创建 MySQL 数据源注册表（条件创建）
     * 只有在 MYSQL_ENABLED=true 时才创建
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "database.mysql", name = "enabled", havingValue = "true")
    public JdbcDataSourceRegistry mysqlDataSourceRegistry() {
        log.info("Initializing MySQL datasources registry");

        String defaultName = properties.getDefaultDatasourceName();
        JdbcDataSourceRegistry registry = new JdbcDataSourceRegistry("MySQL", defaultName);

        for (DataSourceConfig config : properties.getAllDatasourceConfigs()) {
            // 防御多数据源索引跳号（如配置了 0 和 2 未配 1）产生的 null 元素
            if (config == null) {
                log.warn("Skipping null datasource config (check datasource index continuity)");
                continue;
            }
            HikariDataSource ds = createHikariDataSource(config);
            registry.register(config.getName(), ds, config.getDescription(), config.isReadOnly());
            log.info("Registered MySQL datasource: {} (description: {}, readOnly: {})",
                config.getName(), config.getDescription(), config.isReadOnly());
        }

        return registry;
    }

    /**
     * 向后兼容：提供默认 DataSource Bean（仅当 MySQL 配置存在时）
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "database.mysql", name = "enabled", havingValue = "true")
    public DataSource dataSource(JdbcDataSourceRegistry registry) {
        return registry.getDefaultDataSource();
    }

    private HikariDataSource createHikariDataSource(DataSourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        // Detect driver based on URL
        if (config.getUrl().startsWith("jdbc:h2")) {
            hikariConfig.setDriverClassName("org.h2.Driver");
        } else if (config.getUrl().startsWith("jdbc:mysql")) {
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }

        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setPoolName("turnright-mysql-" + config.getName());

        // MySQL-specific optimizations
        if (config.getUrl().startsWith("jdbc:mysql")) {
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
        }

        // Lazy initialization - don't fail startup if database is unreachable
        // -1 means never fail on startup, try to connect lazily
        hikariConfig.setInitializationFailTimeout(-1);

        return new HikariDataSource(hikariConfig);
    }
}