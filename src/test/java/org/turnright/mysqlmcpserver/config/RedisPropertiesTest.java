package org.turnright.mysqlmcpserver.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedisPropertiesTest {

    @Test
    void defaultValues_shouldBeSet() {
        RedisProperties props = new RedisProperties();

        assertFalse(props.isEnabled());
        assertNull(props.getDefaultName());
        assertTrue(props.getDatasources().isEmpty());
        assertNull(props.getDefaultDatasourceName());
    }

    @Test
    void getAllDatasourceConfigs_shouldReturnDatasources() {
        RedisProperties props = new RedisProperties();
        RedisDataSourceConfig c = new RedisDataSourceConfig();
        c.setName("default");
        c.setHost("localhost");
        props.setDatasources(List.of(c));

        assertEquals(1, props.getAllDatasourceConfigs().size());
        assertEquals("default", props.getAllDatasourceConfigs().get(0).getName());
    }

    @Test
    void defaultDatasourceName_explicit_shouldReturnSetName() {
        RedisProperties props = new RedisProperties();
        props.setDefaultName("primary");
        RedisDataSourceConfig c = new RedisDataSourceConfig();
        c.setName("cache");
        props.setDatasources(List.of(c));

        assertEquals("primary", props.getDefaultDatasourceName());
    }

    @Test
    void defaultDatasourceName_withoutExplicit_shouldReturnFirst() {
        RedisProperties props = new RedisProperties();
        RedisDataSourceConfig c1 = new RedisDataSourceConfig();
        c1.setName("primary");
        RedisDataSourceConfig c2 = new RedisDataSourceConfig();
        c2.setName("session");
        props.setDatasources(List.of(c1, c2));

        assertEquals("primary", props.getDefaultDatasourceName());
    }

    @Test
    void setters_shouldUpdateValues() {
        RedisProperties props = new RedisProperties();
        props.setEnabled(true);
        props.setDefaultName("custom");

        assertTrue(props.isEnabled());
        assertEquals("custom", props.getDefaultName());
    }
}
