package org.turnright.mysqlmcpserver.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.turnright.mysqlmcpserver.registry.JdbcDataSourceRegistry;

import javax.sql.DataSource;

/**
 * SQL Server 数据源配置
 * 支持多数据源注册
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SqlServerProperties.class)
@RequiredArgsConstructor
public class SqlServerConfig {

    private final SqlServerProperties properties;

    /**
     * 创建 SQL Server 数据源注册表
     */
    @Bean
    @ConditionalOnProperty(prefix = "database.sqlserver", name = "enabled", havingValue = "true")
    public JdbcDataSourceRegistry sqlServerDataSourceRegistry() {
        log.info("Initializing SQL Server datasources registry");

        String defaultName = properties.getDefaultDatasourceName();
        JdbcDataSourceRegistry registry = new JdbcDataSourceRegistry("SQL Server", defaultName);

        for (DataSourceConfig config : properties.getAllDatasourceConfigs()) {
            // 防御多数据源索引跳号产生的 null 元素
            if (config == null) {
                log.warn("Skipping null datasource config (check datasource index continuity)");
                continue;
            }
            HikariDataSource ds = createHikariDataSource(config);
            registry.register(config.getName(), ds, config.getDescription(), config.isReadOnly());
            log.info("Registered SQL Server datasource: {} (description: {}, readOnly: {})",
                config.getName(), config.getDescription(), config.isReadOnly());
        }

        return registry;
    }

    /**
     * 向后兼容：提供默认 DataSource Bean
     */
    @Bean
    @ConditionalOnProperty(prefix = "database.sqlserver", name = "enabled", havingValue = "true")
    public DataSource sqlServerDataSource(JdbcDataSourceRegistry registry) {
        return registry.getDefaultDataSource();
    }

    private HikariDataSource createHikariDataSource(DataSourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setPoolName("turnright-sqlserver-" + config.getName());

        // SQL Server specific optimizations
        hikariConfig.addDataSourceProperty("sendStringParametersAsUnicode", "false");
        hikariConfig.addDataSourceProperty("selectMethod", "direct");
        hikariConfig.addDataSourceProperty("responseBuffering", "adaptive");
        hikariConfig.addDataSourceProperty("encrypt", "true");
        hikariConfig.addDataSourceProperty("trustServerCertificate", "true");

        // Lazy initialization - don't fail startup if database is unreachable
        hikariConfig.setInitializationFailTimeout(-1);

        return new HikariDataSource(hikariConfig);
    }
}