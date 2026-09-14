package org.turnright.mysqlmcpserver.service;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.turnright.mysqlmcpserver.registry.JdbcDataSourceRegistry;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OracleServiceTest {

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
    private DatabaseMetaData dbMetaData;

    private OracleService oracleService;

    @BeforeEach
    void setUp() {
        oracleService = new OracleService(registry);
    }

    @Test
    void executeQuery_shouldReturnResults() throws SQLException {
        when(registry.getDataSource(null)).thenReturn(dataSource);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT * FROM USERS")).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnLabel(1)).thenReturn("ID");
        when(metaData.getColumnLabel(2)).thenReturn("NAME");
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getObject(1)).thenReturn(1, 2);
        when(resultSet.getObject(2)).thenReturn("Alice", "Bob");
        when(resultSet.wasNull()).thenReturn(false);

        List<Map<String, Object>> results = oracleService.executeQuery("SELECT * FROM USERS");

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).get("ID"));
        assertEquals("Alice", results.get(0).get("NAME"));
    }

    @Test
    void executeUpdate_shouldReturnAffectedRows() throws SQLException {
        when(registry.getDataSource(null)).thenReturn(dataSource);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeUpdate("DELETE FROM USERS WHERE ID = 1")).thenReturn(5);

        int affectedRows = oracleService.executeUpdate("DELETE FROM USERS WHERE ID = 1");

        assertEquals(5, affectedRows);
    }

    @Test
    void listTables_shouldFilterSystemTables() throws SQLException {
        when(registry.getDataSource(null)).thenReturn(dataSource);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(dbMetaData);
        when(dbMetaData.getTables(null, null, "%", new String[]{"TABLE"})).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true, true, false);
        when(resultSet.getString("TABLE_NAME")).thenReturn("USERS", "SYS_SYSTEM", "BIN$123", "PRODUCTS");

        List<String> tables = oracleService.listTables();

        assertEquals(2, tables.size());
        assertTrue(tables.contains("USERS"));
        assertTrue(tables.contains("PRODUCTS"));
        assertFalse(tables.contains("SYS_SYSTEM"));
        assertFalse(tables.contains("BIN$123"));
    }

    @Test
    void getDatabaseInfo_shouldIncludeOracleSpecificInfo() throws SQLException {
        when(registry.getDataSource(null)).thenReturn(dataSource);
        when(registry.getDefaultDataSourceName()).thenReturn("default");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(dbMetaData);
        when(connection.isReadOnly()).thenReturn(false);
        when(dbMetaData.getDatabaseProductName()).thenReturn("Oracle");
        when(dbMetaData.getDatabaseProductVersion()).thenReturn("Oracle Database 19c");
        when(dbMetaData.getDriverName()).thenReturn("Oracle JDBC driver");
        when(dbMetaData.getDriverVersion()).thenReturn("11.2.0.4");
        when(dbMetaData.getJDBCMajorVersion()).thenReturn(11);
        when(dbMetaData.getJDBCMinorVersion()).thenReturn(2);
        when(dbMetaData.getURL()).thenReturn("jdbc:oracle:thin:@localhost:1521:ORCL");
        when(dbMetaData.getUserName()).thenReturn("SYSTEM");
        when(dbMetaData.supportsTransactions()).thenReturn(true);
        when(registry.getDataSourceType()).thenReturn("Oracle");

        // Mock Oracle-specific query
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("INSTANCE_NAME")).thenReturn("ORCL");
        when(resultSet.getString("SERVER_HOST")).thenReturn("localhost");
        when(resultSet.getString("DB_NAME")).thenReturn("ORCL");

        Map<String, Object> info = oracleService.getDatabaseInfo();

        assertEquals("Oracle", info.get("database_product_name"));
        assertEquals("Oracle Database 19c", info.get("database_product_version"));
        assertEquals("ORCL", info.get("instance_name"));
    }

    @Test
    void getAvailableDatasources_shouldReturnFromRegistry() {
        Set<String> dsNames = Set.of("primary", "secondary");
        when(registry.getDataSourceNames()).thenReturn(dsNames);

        Set<String> result = oracleService.getAvailableDatasources();

        assertEquals(2, result.size());
        assertTrue(result.contains("primary"));
        assertTrue(result.contains("secondary"));
    }

    @Test
    void getDefaultDatasourceName_shouldReturnFromRegistry() {
        when(registry.getDefaultDataSourceName()).thenReturn("primary");

        assertEquals("primary", oracleService.getDefaultDatasourceName());
    }
}