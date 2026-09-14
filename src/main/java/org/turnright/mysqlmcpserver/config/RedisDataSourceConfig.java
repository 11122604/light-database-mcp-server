package org.turnright.mysqlmcpserver.config;

import lombok.Data;

/**
 * Redis 单个数据源配置
 */
@Data
public class RedisDataSourceConfig {

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
    private int port = 6379;

    /**
     * 密码
     */
    private String password;

    /**
     * 连接超时（毫秒）
     */
    private int timeout = 2000;

    /**
     * 连接池最大连接数
     */
    private int maxTotal = 8;

    /**
     * 连接池最大空闲连接数
     */
    private int maxIdle = 8;

    /**
     * 连接池最小空闲连接数
     */
    private int minIdle = 0;

    /**
     * 安全模式：只读（默认 true）
     */
    private boolean readOnly = true;
}
