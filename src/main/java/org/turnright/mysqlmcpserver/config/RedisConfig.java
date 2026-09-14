package org.turnright.mysqlmcpserver.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.turnright.mysqlmcpserver.registry.RedisDataSourceRegistry;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Redis 数据源配置
 * 支持多数据源注册
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RedisProperties.class)
@RequiredArgsConstructor
public class RedisConfig {

    private final RedisProperties properties;

    /**
     * 创建 Redis 数据源注册表
     */
    @Bean
    @ConditionalOnProperty(prefix = "database.redis", name = "enabled", havingValue = "true")
    public RedisDataSourceRegistry redisDataSourceRegistry() {
        log.info("Initializing Redis datasources registry");

        String defaultName = properties.getDefaultDatasourceName();
        RedisDataSourceRegistry registry = new RedisDataSourceRegistry("Redis", defaultName);

        for (RedisDataSourceConfig config : properties.getAllDatasourceConfigs()) {
            // 防御多数据源索引跳号产生的 null 元素
            if (config == null) {
                log.warn("Skipping null datasource config (check datasource index continuity)");
                continue;
            }
            JedisPool pool = createJedisPool(config);
            registry.register(config.getName(), pool, config.getDescription(), config.isReadOnly());
            log.info("Registered Redis datasource: {} (host: {}:{}, description: {}, readOnly: {})",
                config.getName(), config.getHost(), config.getPort(), config.getDescription(), config.isReadOnly());
        }

        return registry;
    }

    private JedisPool createJedisPool(RedisDataSourceConfig config) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getMaxTotal());
        poolConfig.setMaxIdle(config.getMaxIdle());
        poolConfig.setMinIdle(config.getMinIdle());
        poolConfig.setBlockWhenExhausted(true);
        // 池耗尽时最多等待 timeout 毫秒后抛异常，避免并发无限阻塞挂死服务
        poolConfig.setMaxWaitMillis(config.getTimeout());

        if (config.getPassword() != null && !config.getPassword().isEmpty()) {
            return new JedisPool(poolConfig, config.getHost(), config.getPort(),
                config.getTimeout(), config.getPassword());
        }
        return new JedisPool(poolConfig, config.getHost(), config.getPort(), config.getTimeout());
    }
}
