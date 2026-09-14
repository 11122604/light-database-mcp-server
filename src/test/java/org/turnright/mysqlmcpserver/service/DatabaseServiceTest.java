package org.turnright.mysqlmcpserver.service;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.turnright.mysqlmcpserver.registry.JdbcDataSourceRegistry;

import java.sql.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseServiceTest {

    @Mock
    private JdbcDataSourceRegistry registry;

    @Mock
    private HikariDataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private ResultSetMetaData metaData;

    @Mock
    private DatabaseMetaData databaseMetaData;

    private DatabaseService databaseService;

    @BeforeEach
    void setUp() throws SQLException {
        databaseService = new DatabaseService(registry);
        when(registry.getDataSource(null)).thenReturn(dataSource);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        // lenient：getDataSourceType 仅在 getDatabaseInfo 相关测试使用，其余测试不触发
        lenient().when(registry.getDataSourceType()).thenReturn("MySQL");
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    void executeQuery_shouldReturnResults() throws SQLException {
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        when(metaData.getColumnLabel(2)).thenReturn("name");
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getObject(1)).thenReturn(1, 2);
        when(resultSet.getObject(2)).thenReturn("Alice", "Bob");
        when(resultSet.wasNull()).thenReturn(false);

        List<Map<String, Object>> results = databaseService.executeQuery("SELECT * FROM users");

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).get("id"));
        assertEquals("Alice", results.get(0).get("name"));
        assertEquals(2, results.get(1).get("id"));
        assertEquals("Bob", results.get(1).get("name"));

        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void executeUpdate_shouldReturnAffectedRows() throws SQLException {
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeUpdate(anyString())).thenReturn(5);

        int affectedRows = databaseService.executeUpdate("DELETE FROM users WHERE id > 10");

        assertEquals(5, affectedRows);
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void listTables_shouldReturnTableNames() throws SQLException {
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(connection.getCatalog()).thenReturn("testdb");
        when(databaseMetaData.getTables("testdb", null, "%", new String[]{"TABLE"})).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("TABLE_NAME")).thenReturn("users", "orders");

        List<String> tables = databaseService.listTables();

        assertEquals(2, tables.size());
        assertEquals("users", tables.get(0));
        assertEquals("orders", tables.get(1));
    }

    @Test
    void getDatabaseInfo_shouldReturnInfoMap() throws SQLException {
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(connection.getCatalog()).thenReturn("testdb");
        when(connection.isReadOnly()).thenReturn(false);
        when(databaseMetaData.getDatabaseProductName()).thenReturn("MySQL");
        when(databaseMetaData.getDatabaseProductVersion()).thenReturn("8.0.33");
        when(databaseMetaData.getDriverName()).thenReturn("MySQL Connector/J");
        when(databaseMetaData.getDriverVersion()).thenReturn("8.4.0");
        when(databaseMetaData.getJDBCMajorVersion()).thenReturn(4);
        when(databaseMetaData.getJDBCMinorVersion()).thenReturn(2);
        when(databaseMetaData.getURL()).thenReturn("jdbc:mysql://localhost:3306/testdb");
        when(databaseMetaData.getUserName()).thenReturn("root");
        when(databaseMetaData.supportsTransactions()).thenReturn(true);
        when(databaseMetaData.getMaxConnections()).thenReturn(100);

        Map<String, Object> info = databaseService.getDatabaseInfo();

        assertEquals("default", info.get("datasource"));
        assertEquals("MySQL", info.get("datasource_type"));
        assertEquals("testdb", info.get("database_name"));
        assertEquals("MySQL", info.get("database_product_name"));
        assertEquals("8.0.33", info.get("database_product_version"));
        assertEquals("root", info.get("username"));
        assertTrue((Boolean) info.get("supports_transactions"));
        assertFalse((Boolean) info.get("read_only"));
        assertEquals(100, info.get("max_connections"));
    }
}