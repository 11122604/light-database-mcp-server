package org.turnright.mysqlmcpserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 数据源配置属性
 * 仅支持多数据源（datasources 列表）。单数据源配置已移除，统一走多源。
 */
@Data
@ConfigurationProperties(prefix = "database.elasticsearch")
public class ElasticsearchProperties {

    /**
     * 默认数据源名称（缺省时取第一个配置的数据源）
     */
    private String defaultName;

    /**
     * 是否启用 Elasticsearch（默认 false，需要显式设置 true）
     */
    private boolean enabled = false;

    /**
     * 多数据源配置列表
     */
    private List<ElasticsearchDataSourceConfig> datasources = new ArrayList<>();

    /**
     * 获取所有数据源配置（仅多源）
     */
    public List<ElasticsearchDataSourceConfig> getAllDatasourceConfigs() {
        return datasources;
    }

    /**
     * 获取默认数据源名称
     */
    public String getDefaultDatasourceName() {
        if (defaultName != null && !defaultName.isEmpty()) {
            return defaultName;
        }
        if (!datasources.isEmpty()) {
            return datasources.get(0).getName();
        }
        return null;
    }
}
