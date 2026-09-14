package org.turnright.mysqlmcpserver.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.turnright.mysqlmcpserver.registry.ElasticsearchDataSourceRegistry;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 数据源配置
 * 支持多数据源注册
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ElasticsearchProperties.class)
@RequiredArgsConstructor
public class ElasticsearchConfig {

    private final ElasticsearchProperties properties;

    /**
     * 记录本配置创建的 transport，应用关闭时统一释放底层 RestClient 连接池与线程
     */
    private final List<ElasticsearchTransport> transports = new ArrayList<>();

    /**
     * 应用关闭时释放所有 Elasticsearch 底层连接
     */
    @PreDestroy
    public void shutdownElasticsearchClients() {
        for (ElasticsearchTransport transport : transports) {
            try {
                transport.close();
                log.info("Closed Elasticsearch transport");
            } catch (Exception e) {
                log.error("Error closing Elasticsearch transport: {}", e.getMessage());
            }
        }
        transports.clear();
    }

    /**
     * 创建 Elasticsearch 数据源注册表
     */
    @Bean
    @ConditionalOnProperty(prefix = "database.elasticsearch", name = "enabled", havingValue = "true")
    public ElasticsearchDataSourceRegistry elasticsearchDataSourceRegistry() {
        log.info("Initializing Elasticsearch datasources registry");

        String defaultName = properties.getDefaultDatasourceName();
        ElasticsearchDataSourceRegistry registry = new ElasticsearchDataSourceRegistry(defaultName);

        for (ElasticsearchDataSourceConfig config : properties.getAllDatasourceConfigs()) {
            // 防御多数据源索引跳号产生的 null 元素
            if (config == null) {
                log.warn("Skipping null datasource config (check datasource index continuity)");
                continue;
            }
            ElasticsearchClient client = createElasticsearchClient(config);
            registry.register(config.getName(), client, config.getDescription(), config.isReadOnly());
            log.info("Registered Elasticsearch datasource: {} (host: {}, description: {}, readOnly: {})",
                config.getName(), config.getHost() + ":" + config.getPort(), config.getDescription(), config.isReadOnly());
        }

        return registry;
    }

    /**
     * 向后兼容：提供默认 ElasticsearchClient Bean
     */
    @Bean
    @ConditionalOnProperty(prefix = "database.elasticsearch", name = "enabled", havingValue = "true")
    public ElasticsearchClient elasticsearchClient(ElasticsearchDataSourceRegistry registry) {
        return registry.getDefaultDataSource();
    }

    private ElasticsearchClient createElasticsearchClient(ElasticsearchDataSourceConfig config) {
        HttpHost httpHost = new HttpHost(config.getHost(), config.getPort(),
            config.isSsl() ? "https" : "http");

        RestClientBuilder builder = RestClient.builder(httpHost);

        // Configure authentication
        if (config.getUsername() != null && config.getPassword() != null &&
            !config.getUsername().isEmpty() && !config.getPassword().isEmpty()) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(config.getUsername(), config.getPassword()));

            builder.setHttpClientConfigCallback(httpClientBuilder ->
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        RestClient restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper());
        transports.add(transport);

        return new ElasticsearchClient(transport);
    }
}