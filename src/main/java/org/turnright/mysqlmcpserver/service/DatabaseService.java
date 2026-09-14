package org.turnright.mysqlmcpserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.turnright.mysqlmcpserver.registry.JdbcDataSourceRegistry;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * MySQL 数据库服务
 * 支持多数据源切换
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(JdbcDataSourceRegistry.class)
public class DatabaseService {

    /**
     * 单次查询最大返回行数（防御大表全量物化导致 OOM）
     */
    private static final int MAX_QUERY_ROWS = 10000;

    private final JdbcDataSourceRegistry registry;

    /**
     * 执行查询（使用默认数据源）
     */
    public List<Map<String, Object>> executeQuery(String sql) throws SQLException {
        return executeQuery(null, sql);
    }

    /**
     * 执行查询（指定数据源）
     */
    public List<Map<String, Object>> executeQuery(String dataSourceName, String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL statement cannot be null or empty");
        }

        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.debug("Executing query on datasource '{}', length: {} chars", actualDsName, sql.length());

        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // 防御大表全量物化导致 OOM：限制单次查询返回行数
            stmt.setMaxRows(MAX_QUERY_ROWS);

            try (ResultSet rs = stmt.executeQuery(sql)) {

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        if (rs.wasNull()) {
                            value = null;
                        }
                        // JOIN 同名列加序号后缀，避免后者覆盖前者导致数据静默丢失
                        String key = columnName;
                        int dup = 1;
                        while (row.containsKey(key)) {
                            key = columnName + "_" + (++dup);
                        }
                        row.put(key, value);
                    }
                    results.add(row);
                }
            }
        }

        log.info("Query returned {} rows", results.size());
        return results;
    }

    /**
     * 执行更新（使用默认数据源）
     */
    public int executeUpdate(String sql) throws SQLException {
        return executeUpdate(null, sql);
    }

    /**
     * 执行更新（指定数据源）
     */
    public int executeUpdate(String dataSourceName, String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL statement cannot be null or empty");
        }

        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.debug("Executing update on datasource '{}', length: {} chars", actualDsName, sql.length());

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            int affectedRows = stmt.executeUpdate(sql);
            log.info("Update affected {} rows", affectedRows);
            return affectedRows;
        }
    }

    /**
     * 获取表结构
     */
    public List<Map<String, Object>> getTableSchema(String tableName) throws SQLException {
        return getTableSchema(null, tableName);
    }

    public List<Map<String, Object>> getTableSchema(String dataSourceName, String tableName) throws SQLException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }

        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting schema for table '{}' on datasource '{}'", tableName, actualDsName);

        List<Map<String, Object>> schema = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();

            // ResultSet 显式关闭，避免连接池复用时游标/内存滞留
            try (ResultSet columns = metaData.getColumns(catalog, null, tableName, null)) {
                while (columns.next()) {
                    Map<String, Object> columnInfo = new LinkedHashMap<>();
                    columnInfo.put("column_name", columns.getString("COLUMN_NAME"));
                    columnInfo.put("data_type", columns.getString("TYPE_NAME"));
                    columnInfo.put("column_size", columns.getInt("COLUMN_SIZE"));
                    columnInfo.put("nullable", "YES".equals(columns.getString("IS_NULLABLE")));
                    columnInfo.put("default_value", columns.getString("COLUMN_DEF"));
                    columnInfo.put("remarks", columns.getString("REMARKS"));
                    columnInfo.put("ordinal_position", columns.getInt("ORDINAL_POSITION"));
                    schema.add(columnInfo);
                }
            }
        }

        log.info("Schema for {} has {} columns", tableName, schema.size());
        return schema;
    }

    /**
     * 列出所有表
     */
    public List<String> listTables() throws SQLException {
        return listTables(null);
    }

    public List<String> listTables(String dataSourceName) throws SQLException {
        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Listing all tables on datasource '{}'", actualDsName);

        List<String> tables = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();

            // ResultSet 显式关闭
            try (ResultSet rs = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
        }

        log.info("Found {} tables", tables.size());
        return tables;
    }

    /**
     * 获取数据库信息
     */
    public Map<String, Object> getDatabaseInfo() throws SQLException {
        return getDatabaseInfo(null);
    }

    public Map<String, Object> getDatabaseInfo(String dataSourceName) throws SQLException {
        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting database info for datasource '{}'", actualDsName);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("datasource", actualDsName);
        info.put("datasource_type", registry.getDataSourceType());

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            info.put("database_name", conn.getCatalog());
            info.put("database_product_name", metaData.getDatabaseProductName());
            info.put("database_product_version", metaData.getDatabaseProductVersion());
            info.put("driver_name", metaData.getDriverName());
            info.put("driver_version", metaData.getDriverVersion());
            info.put("jdbc_major_version", metaData.getJDBCMajorVersion());
            info.put("jdbc_minor_version", metaData.getJDBCMinorVersion());
            info.put("url", metaData.getURL());
            info.put("username", metaData.getUserName());
            info.put("supports_transactions", metaData.supportsTransactions());
            info.put("read_only", conn.isReadOnly());
            info.put("max_connections", metaData.getMaxConnections());
        }

        return info;
    }

    /**
     * 获取表索引
     */
    public List<Map<String, Object>> getTableIndexes(String tableName) throws SQLException {
        return getTableIndexes(null, tableName);
    }

    public List<Map<String, Object>> getTableIndexes(String dataSourceName, String tableName) throws SQLException {
        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting indexes for table '{}' on datasource '{}'", tableName, actualDsName);

        List<Map<String, Object>> indexes = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();

            // ResultSet 显式关闭
            try (ResultSet rs = metaData.getIndexInfo(catalog, null, tableName, false, false)) {
                while (rs.next()) {
                    Map<String, Object> indexInfo = new LinkedHashMap<>();
                    indexInfo.put("index_name", rs.getString("INDEX_NAME"));
                    indexInfo.put("column_name", rs.getString("COLUMN_NAME"));
                    indexInfo.put("non_unique", rs.getBoolean("NON_UNIQUE"));
                    indexInfo.put("type", rs.getShort("TYPE"));
                    indexInfo.put("ordinal_position", rs.getShort("ORDINAL_POSITION"));
                    indexInfo.put("sort_order", rs.getString("ASC_OR_DESC"));
                    indexes.add(indexInfo);
                }
            }
        }

        return indexes;
    }

    /**
     * 获取表主键
     */
    public List<Map<String, Object>> getTablePrimaryKey(String tableName) throws SQLException {
        return getTablePrimaryKey(null, tableName);
    }

    public List<Map<String, Object>> getTablePrimaryKey(String dataSourceName, String tableName) throws SQLException {
        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting primary key for table '{}' on datasource '{}'", tableName, actualDsName);

        List<Map<String, Object>> pkColumns = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();

            // ResultSet 显式关闭
            try (ResultSet rs = metaData.getPrimaryKeys(catalog, null, tableName)) {
                while (rs.next()) {
                    Map<String, Object> pkInfo = new LinkedHashMap<>();
                    pkInfo.put("column_name", rs.getString("COLUMN_NAME"));
                    pkInfo.put("key_seq", rs.getShort("KEY_SEQ"));
                    pkInfo.put("pk_name", rs.getString("PK_NAME"));
                    pkColumns.add(pkInfo);
                }
            }
        }

        return pkColumns;
    }

    /**
     * 获取可用数据源列表
     */
    public Set<String> getAvailableDatasources() {
        return registry.getDataSourceNames();
    }

    /**
     * 获取默认数据源名称
     */
    public String getDefaultDatasourceName() {
        return registry.getDefaultDataSourceName();
    }

    /**
     * 获取数据源元数据（包含描述）
     */
    public Map<String, String> getDatasourceMetadata() {
        return registry.getDataSourceMetadata();
    }

    /**
     * 检查数据源是否为只读模式
     * @param datasource 数据源名称（null 使用默认）
     */
    public boolean isReadOnly(String datasource) {
        return registry.isReadOnly(datasource);
    }
}