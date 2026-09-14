package org.turnright.mysqlmcpserver.config;

import lombok.Data;

/**
 * 单个数据源配置
 * 用于多数据源配置列表中的每个数据源
 */
@Data
public class DataSourceConfig {

    /**
     * 数据源名称（唯一标识，用于工具调用时指定）
     */
    private String name;

    /**
     * 数据源描述（帮助 AI 理解此数据源的用途）
     */
    private String description;

    /**
     * JDBC URL
     */
    private String url;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 连接池最大连接数
     */
    private int maxPoolSize = 10;

    /**
     * 最小空闲连接数
     */
    private int minIdle = 2;

    /**
     * 空闲超时（毫秒）
     */
    private long idleTimeout = 300000;

    /**
     * 连接超时（毫秒）
     */
    private long connectionTimeout = 30000;

    /**
     * 安全模式：只读（默认 true）
     * true = 只允许查询操作（SELECT）
     * false = 允许写入操作（INSERT, UPDATE, DELETE）
     */
    private boolean readOnly = true;
}