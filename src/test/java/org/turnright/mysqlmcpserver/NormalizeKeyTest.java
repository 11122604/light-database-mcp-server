package org.turnright.mysqlmcpserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import org.turnright.mysqlmcpserver.config.DatabaseProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置键映射规则测试
 * 验证 mcp-config.env 中的 <DB>_* 键被映射到正确的 @ConfigurationProperties 路径
 * @author luliang
 */
class NormalizeKeyTest {

    @Test
    void mysqlToggle_shouldMapToDatabaseMysql() {
        assertEquals("database.mysql.enabled", TurnrightMysqlMcpServerApplication.normalizeKey("MYSQL_ENABLED"));
        assertEquals("database.mysql.default-name",
            TurnrightMysqlMcpServerApplication.normalizeKey("MYSQL_DEFAULT_DATASOURCE"));
    }

    @Test
    void mysqlDatasource_shouldMapToIndexedList() {
        assertEquals("database.mysql.datasources[0].name",
            TurnrightMysqlMcpServerApplication.normalizeKey("MYSQL_DATASOURCES_0_NAME"));
        assertEquals("database.mysql.datasources[0].url",
            TurnrightMysqlMcpServerApplication.normalizeKey("MYSQL_DATASOURCES_0_URL"));
        assertEquals("database.mysql.datasources[1].read-only",
            TurnrightMysqlMcpServerApplication.normalizeKey("MYSQL_DATASOURCES_1_READONLY"));
    }

    @Test
    void otherDatabases_shouldKeepTheirOwnPrefix() {
        assertEquals("database.sqlserver.datasources[0].url",
            TurnrightMysqlMcpServerApplication.normalizeKey("SQLSERVER_DATASOURCES_0_URL"));
        assertEquals("database.mongodb.datasources[0].uri",
            TurnrightMysqlMcpServerApplication.normalizeKey("MONGODB_DATASOURCES_0_URI"));
    }

    @Test
    void legacyDatabasePrefix_shouldNotMapToMysql() {
        // 旧前缀已移除，不得再被解析成 MySQL 数据源属性
        assertNotEquals("database.mysql.datasources[0].url",
            TurnrightMysqlMcpServerApplication.normalizeKey("DATABASE_DATASOURCES_0_URL"));
    }

    @Test
    void normalizedKeys_shouldBindToDatabaseProperties() {
        // 端到端：mcp-config.env 的 MYSQL_* 键经 normalizeKey 后必须能被 Spring 绑定
        MockEnvironment env = new MockEnvironment();
        Map.of(
            "MYSQL_ENABLED", "true",
            "MYSQL_DATASOURCES_0_NAME", "default",
            "MYSQL_DATASOURCES_0_URL", "jdbc:mysql://localhost:3306/mydb"
        ).forEach((rawKey, value) ->
            env.setProperty(TurnrightMysqlMcpServerApplication.normalizeKey(rawKey), value));

        DatabaseProperties properties = Binder.get(env)
            .bind("database.mysql", Bindable.of(DatabaseProperties.class))
            .get();

        assertTrue(properties.isEnabled());
        assertEquals(1, properties.getAllDatasourceConfigs().size());
        assertEquals("default", properties.getDefaultDatasourceName());
        assertEquals("jdbc:mysql://localhost:3306/mydb", properties.getAllDatasourceConfigs().get(0).getUrl());
    }
}
