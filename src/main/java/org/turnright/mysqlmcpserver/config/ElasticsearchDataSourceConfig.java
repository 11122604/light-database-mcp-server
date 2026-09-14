package org.turnright.mysqlmcpserver.config;

import lombok.Data;

/**
 * Elasticsearch 单个数据源配置
 */
@Data
public class ElasticsearchDataSourceConfig {

    /**
     * 数据源名称（唯一标识）
     */
    private String name;

    /**
     * 数据源描述
     */
    private String description;

    /**
     * 主机地址
     */
    private String host = "localhost";

    /**
     * 端口
     */
    private int port = 9200;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * API Key（可选，替代用户名密码）
     */
    private String apiKey;

    /**
     * 是否启用 SSL
     */
    private boolean ssl = false;

    /**
     * SSL 证书指纹（可选，用于自签名证书）
     */
    private String fingerprint;

    /**
     * 安全模式：只读（默认 true）
     * true = 只允许查询操作（search, get）
     * false = 允许写入操作（index, update, delete document, create index）
     */
    private boolean readOnly = true;
}