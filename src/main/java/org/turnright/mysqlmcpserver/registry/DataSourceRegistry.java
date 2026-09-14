package org.turnright.mysqlmcpserver.registry;

import java.util.Map;
import java.util.Set;

/**
 * 数据源注册表接口
 * 用于管理多个同类型的数据源
 */
public interface DataSourceRegistry<T> {

    /**
     * 注册数据源
     * @param name 数据源名称（唯一标识）
     * @param dataSource 数据源实例
     */
    void register(String name, T dataSource);

    /**
     * 注册数据源（带描述）
     * @param name 数据源名称（唯一标识）
     * @param dataSource 数据源实例
     * @param description 数据源描述（用途说明）
     */
    void register(String name, T dataSource, String description);

    /**
     * 注册数据源（带描述和只读状态）
     * @param name 数据源名称（唯一标识）
     * @param dataSource 数据源实例
     * @param description 数据源描述（用途说明）
     * @param readOnly 是否只读模式
     */
    void register(String name, T dataSource, String description, boolean readOnly);

    /**
     * 获取指定名称的数据源
     * @param name 数据源名称
     * @return 数据源实例
     * @throws IllegalArgumentException 名称不存在时抛出（不再静默回退默认数据源）
     */
    T getDataSource(String name);

    /**
     * 获取所有数据源名称
     * @return 数据源名称集合
     */
    Set<String> getDataSourceNames();

    /**
     * 获取数据源描述
     * @param name 数据源名称
     * @return 数据源描述，不存在时返回 null
     */
    String getDataSourceDescription(String name);

    /**
     * 获取所有数据源的元数据（名称+描述）
     * @return 数据源元数据Map
     */
    Map<String, String> getDataSourceMetadata();

    /**
     * 获取默认数据源
     * @return 默认数据源实例
     */
    T getDefaultDataSource();

    /**
     * 获取默认数据源名称
     * @return 默认数据源名称
     */
    String getDefaultDataSourceName();

    /**
     * 检查数据源是否存在
     * @param name 数据源名称
     * @return 是否存在
     */
    boolean hasDataSource(String name);

    /**
     * 检查数据源是否为只读模式
     * @param name 数据源名称
     * @return 是否只读（默认 true）
     */
    boolean isReadOnly(String name);

    /**
     * 获取数据源类型描述
     * @return 类型描述（如 "MySQL", "MongoDB"）
     */
    String getDataSourceType();

    /**
     * 关闭所有数据源（清理资源）
     */
    void closeAll();
}