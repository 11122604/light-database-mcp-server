package org.turnright.mysqlmcpserver.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabasePropertiesTest {

    @Test
    void defaultValues_shouldBeSet() {
        DatabaseProperties props = new DatabaseProperties();

        assertFalse(props.isEnabled());
        assertNull(props.getDefaultName());
        assertTrue(props.getDatasources().isEmpty());
        assertTrue(props.getAllDatasourceConfigs().isEmpty());
        assertNull(props.getDefaultDatasourceName());
    }

    @Test
    void getAllDatasourceConfigs_shouldReturnDatasources() {
        DatabaseProperties props = new DatabaseProperties();
        DataSourceConfig c1 = new DataSourceConfig();
        c1.setName("primary");
        c1.setUrl("jdbc:mysql://primary:3306/db");
        DataSourceConfig c2 = new DataSourceConfig();
        c2.setName("analytics");
        props.setDatasources(List.of(c1, c2));

        assertEquals(2, props.getAllDatasourceConfigs().size());
        assertEquals("primary", props.getAllDatasourceConfigs().get(0).getName());
        assertEquals("analytics", props.getAllDatasourceConfigs().get(1).getName());
    }

    @Test
    void defaultDatasourceName_explicit_shouldReturnSetName() {
        DatabaseProperties props = new DatabaseProperties();
        props.setDefaultName("primary");
        DataSourceConfig c = new DataSourceConfig();
        c.setName("secondary");
        props.setDatasources(List.of(c));

        assertEquals("primary", props.getDefaultDatasourceName());
    }

    @Test
    void defaultDatasourceName_withoutExplicit_shouldReturnFirst() {
        DatabaseProperties props = new DatabaseProperties();
        DataSourceConfig c1 = new DataSourceConfig();
        c1.setName("first");
        DataSourceConfig c2 = new DataSourceConfig();
        c2.setName("second");
        props.setDatasources(List.of(c1, c2));

        assertEquals("first", props.getDefaultDatasourceName());
    }

    @Test
    void setters_shouldUpdateValues() {
        DatabaseProperties props = new DatabaseProperties();
        props.setEnabled(true);
        props.setDefaultName("custom");

        assertTrue(props.isEnabled());
        assertEquals("custom", props.getDefaultName());
    }
}
