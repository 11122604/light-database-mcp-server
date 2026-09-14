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
 * Oracle 数据库服务
 * 支持多数据源切换
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(name = "oracleDataSourceRegistry")
public class OracleService {

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
     * 获取表结构（使用默认数据源）
     */
    public List<Map<String, Object>> getTableSchema(String tableName) throws SQLException {
        return getTableSchema(null, tableName);
    }

    /**
     * 获取表结构（指定数据源）
     * Oracle 表名格式支持: TABLE_NAME, OWNER.TABLE_NAME
     */
    public List<Map<String, Object>> getTableSchema(String dataSourceName, String tableName) throws SQLException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }

        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting schema for table '{}' on datasource '{}'", tableName, actualDsName);

        List<Map<String, Object>> schema = new ArrayList<>();

        // Parse owner and table name
        String owner = null;
        String table = tableName;
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.");
            owner = parts[0].toUpperCase();
            table = parts[1].toUpperCase();
        }

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // ResultSet 用 try-with-resources 显式关闭，避免连接池复用时游标累积触发 ORA-01000
            try (ResultSet columns = metaData.getColumns(null, owner, table, null)) {
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
     * 列出所有表（使用默认数据源）
     */
    public List<String> listTables() throws SQLException {
        return listTables(null, null);
    }

    /**
     * 列出所有表（指定数据源）
     */
    public List<String> listTables(String dataSourceName) throws SQLException {
        return listTables(dataSourceName, null);
    }

    /**
     * 列出所有表（指定数据源和 owner/schema）
     * Oracle 过滤系统表（以 SYS_, BIN$, MVIEW$ 开头）
     */
    public List<String> listTables(String dataSourceName, String owner) throws SQLException {
        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Listing tables on datasource '{}' for owner '{}'", actualDsName, owner);

        List<String> tables = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // ResultSet 显式关闭，避免连接池复用时游标累积
            try (ResultSet rs = metaData.getTables(null, owner != null ? owner.toUpperCase() : null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // Filter out Oracle system tables and recycle bin objects
                    if (!tableName.startsWith("SYS_") &&
                        !tableName.startsWith("BIN$") &&
                        !tableName.startsWith("MVIEW$") &&
                        !tableName.startsWith("AQ$") &&
                        !tableName.startsWith("REPCAT$")) {
                        tables.add(tableName);
                    }
                }
            }
        }

        log.info("Found {} tables", tables.size());
        return tables;
    }

    /**
     * 列出所有 Schema（使用默认数据源）
     */
    public List<String> listSchemas() throws SQLException {
        return listSchemas(null);
    }

    /**
     * 列出所有 Schema（指定数据源）
     * Oracle schema = owner (用户名)
     */
    public List<String> listSchemas(String dataSourceName) throws SQLException {
        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Listing schemas on datasource '{}'", actualDsName);

        List<String> schemas = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // ResultSet 显式关闭，避免连接池复用时游标累积
            try (ResultSet rs = metaData.getSchemas()) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    // Filter out Oracle system schemas
                    if (!schema.equals("SYS") &&
                        !schema.equals("SYSTEM") &&
                        !schema.startsWith("APEX_") &&
                        !schema.startsWith("CTXSYS") &&
                        !schema.startsWith("MDSYS") &&
                        !schema.startsWith("OLAPSYS") &&
                        !schema.startsWith("ORDSYS") &&
                        !schema.startsWith("OUTLN") &&
                        !schema.startsWith("WMSYS") &&
                        !schema.startsWith("XDB") &&
                        !schema.startsWith("XS$NULL")) {
                        schemas.add(schema);
                    }
                }
            }
        }

        log.info("Found {} schemas", schemas.size());
        return schemas;
    }

    /**
     * 获取数据库信息（使用默认数据源）
     */
    public Map<String, Object> getDatabaseInfo() throws SQLException {
        return getDatabaseInfo(null);
    }

    /**
     * 获取数据库信息（指定数据源）
     */
    public Map<String, Object> getDatabaseInfo(String dataSourceName) throws SQLException {
        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting database info for datasource '{}'", actualDsName);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("datasource", actualDsName);
        info.put("datasource_type", registry.getDataSourceType());

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

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

            // Oracle specific: get instance name
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT SYS_CONTEXT('USERENV', 'INSTANCE_NAME') AS INSTANCE_NAME, " +
                     "SYS_CONTEXT('USERENV', 'SERVER_HOST') AS SERVER_HOST, " +
                     "SYS_CONTEXT('USERENV', 'DB_NAME') AS DB_NAME FROM DUAL")) {
                if (rs.next()) {
                    info.put("instance_name", rs.getString("INSTANCE_NAME"));
                    info.put("server_host", rs.getString("SERVER_HOST"));
                    info.put("db_name", rs.getString("DB_NAME"));
                }
            }
        }

        return info;
    }

    /**
     * 获取表索引（使用默认数据源）
     */
    public List<Map<String, Object>> getTableIndexes(String tableName) throws SQLException {
        return getTableIndexes(null, tableName);
    }

    /**
     * 获取表索引（指定数据源）
     */
    public List<Map<String, Object>> getTableIndexes(String dataSourceName, String tableName) throws SQLException {
        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting indexes for table '{}' on datasource '{}'", tableName, actualDsName);

        // Parse owner and table name
        String owner = null;
        String table = tableName;
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.");
            owner = parts[0].toUpperCase();
            table = parts[1].toUpperCase();
        }

        List<Map<String, Object>> indexes = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // ResultSet 显式关闭，避免连接池复用时游标累积
            try (ResultSet rs = metaData.getIndexInfo(null, owner, table, false, false)) {
                while (rs.next()) {
                    Map<String, Object> indexInfo = new LinkedHashMap<>();
                    indexInfo.put("index_name", rs.getString("INDEX_NAME"));
                    indexInfo.put("column_name", rs.getString("COLUMN_NAME"));
                    indexInfo.put("non_unique", rs.getBoolean("NON_UNIQUE"));
                    indexInfo.put("type", rs.getShort("TYPE"));
                    indexInfo.put("ordinal_position", rs.getShort("ORDINAL_POSITION"));
                    indexes.add(indexInfo);
                }
            }
        }

        return indexes;
    }

    /**
     * 获取表主键（使用默认数据源）
     */
    public List<Map<String, Object>> getTablePrimaryKey(String tableName) throws SQLException {
        return getTablePrimaryKey(null, tableName);
    }

    /**
     * 获取表主键（指定数据源）
     */
    public List<Map<String, Object>> getTablePrimaryKey(String dataSourceName, String tableName) throws SQLException {
        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting primary key for table '{}' on datasource '{}'", tableName, actualDsName);

        // Parse owner and table name
        String owner = null;
        String table = tableName;
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.");
            owner = parts[0].toUpperCase();
            table = parts[1].toUpperCase();
        }

        List<Map<String, Object>> pkColumns = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // ResultSet 显式关闭，避免连接池复用时游标累积
            try (ResultSet rs = metaData.getPrimaryKeys(null, owner, table)) {
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
     * 执行存储过程（使用默认数据源）
     */
    public List<Map<String, Object>> executeStoredProcedure(String procedureName, Map<String, Object> params) throws SQLException {
        return executeStoredProcedure(null, procedureName, params, null);
    }

    /**
     * 执行存储过程（指定数据源）
     * Oracle 支持包调用: PACKAGE_NAME.PROCEDURE_NAME
     */
    public List<Map<String, Object>> executeStoredProcedure(String dataSourceName, String procedureName,
                                                            Map<String, Object> params,
                                                            List<String> outParamNames) throws SQLException {
        if (procedureName == null || procedureName.trim().isEmpty()) {
            throw new IllegalArgumentException("Procedure name cannot be null or empty");
        }
        // Validate procedure name format (only allow valid Oracle identifiers)
        if (!procedureName.matches("^[A-Za-z_][A-Za-z0-9_]*(\\.([A-Za-z_][A-Za-z0-9_]*))?$")) {
            throw new IllegalArgumentException("Invalid procedure name format: " + procedureName);
        }

        DataSource dataSource = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Executing stored procedure '{}' on datasource '{}'", procedureName, actualDsName);

        try (Connection conn = dataSource.getConnection()) {
            // Build callable statement
            StringBuilder sqlBuilder = new StringBuilder("{call ");
            sqlBuilder.append(procedureName);

            int totalParams = (params != null ? params.size() : 0) + (outParamNames != null ? outParamNames.size() : 0);
            if (totalParams > 0) {
                sqlBuilder.append("(");
                for (int i = 0; i < totalParams; i++) {
                    sqlBuilder.append("?");
                    if (i < totalParams - 1) {
                        sqlBuilder.append(",");
                    }
                }
                sqlBuilder.append(")");
            }
            sqlBuilder.append("}");

            try (CallableStatement stmt = conn.prepareCall(sqlBuilder.toString())) {
                // Set IN parameters
                int index = 1;
                if (params != null) {
                    for (Object value : params.values()) {
                        stmt.setObject(index, value);
                        index++;
                    }
                }

                // Register OUT parameters
                if (outParamNames != null) {
                    for (int i = 0; i < outParamNames.size(); i++) {
                        stmt.registerOutParameter(index, Types.VARCHAR);
                        index++;
                    }
                }

                boolean hasResults = stmt.execute();
                List<Map<String, Object>> results = new ArrayList<>();

                if (hasResults) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                row.put(metaData.getColumnLabel(i), rs.getObject(i));
                            }
                            results.add(row);
                        }
                    }
                }

                // Get OUT parameter values
                if (outParamNames != null && !outParamNames.isEmpty()) {
                    Map<String, Object> outValues = new LinkedHashMap<>();
                    int outIndex = (params != null ? params.size() : 0) + 1;
                    for (String outName : outParamNames) {
                        outValues.put(outName, stmt.getObject(outIndex));
                        outIndex++;
                    }
                    results.add(outValues);
                }

                return results;
            }
        }
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