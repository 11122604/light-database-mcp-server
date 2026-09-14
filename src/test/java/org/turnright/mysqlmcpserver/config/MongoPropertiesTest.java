package org.turnright.mysqlmcpserver.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MongoPropertiesTest {

    @Test
    void defaultValues_shouldBeSet() {
        MongoProperties props = new MongoProperties();

        assertFalse(props.isEnabled());
        assertNull(props.getDefaultName());
        assertTrue(props.getDatasources().isEmpty());
        assertNull(props.getDefaultDatasourceName());
    }

    @Test
    void getAllDatasourceConfigs_shouldReturnDatasources() {
        MongoProperties props = new MongoProperties();
        MongoDataSourceConfig c = new MongoDataSourceConfig();
        c.setName("default");
        c.setUri("mongodb://localhost:27017");
        c.setDatabase("main_db");
        props.setDatasources(List.of(c));

        assertEquals(1, props.getAllDatasourceConfigs().size());
        assertEquals("default", props.getAllDatasourceConfigs().get(0).getName());
    }

    @Test
    void defaultDatasourceName_explicit_shouldReturnSetName() {
        MongoProperties props = new MongoProperties();
        props.setDefaultName("primary");
        MongoDataSourceConfig c = new MongoDataSourceConfig();
        c.setName("secondary");
        props.setDatasources(List.of(c));

        assertEquals("primary", props.getDefaultDatasourceName());
    }

    @Test
    void defaultDatasourceName_withoutExplicit_shouldReturnFirst() {
        MongoProperties props = new MongoProperties();
        MongoDataSourceConfig c = new MongoDataSourceConfig();
        c.setName("logs");
        props.setDatasources(List.of(c));

        assertEquals("logs", props.getDefaultDatasourceName());
    }
}
