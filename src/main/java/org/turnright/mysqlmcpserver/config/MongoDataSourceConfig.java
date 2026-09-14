package org.turnright.mysqlmcpserver.config;

import lombok.Data;

/**
 * MongoDB 单个数据源配置
 */
@Data
public class MongoDataSourceConfig {

    /**
     * 数据源名称（唯一标识）
     */
    private String name;

    /**
     * 数据源描述
     */
    private String description;

    /**
     * MongoDB URI
     */
    private String uri;

    /**
     * 主机地址
     */
    private String host = "localhost";

    /**
     * 端口
     */
    private int port = 27017;

    /**
     * 数据库名
     */
    private String database;

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
    private int maxPoolSize = 100;

    /**
     * 连接池最小连接数
     */
    private int minPoolSize = 10;

    /**
     * 最大空闲时间（毫秒）
     */
    private long maxIdleTimeMs = 60000;

    /**
     * 最大连接生命周期（毫秒）
     */
    private long maxConnectionLifeTimeMs = 300000;

    /**
     * 安全模式：只读（默认 true）
     * true = 只允许查询操作（find, aggregate）
     * false = 允许写入操作（insert, update, delete）
     */
    private boolean readOnly = true;
}