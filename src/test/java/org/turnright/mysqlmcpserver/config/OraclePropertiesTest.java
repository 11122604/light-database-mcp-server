package org.turnright.mysqlmcpserver.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OraclePropertiesTest {

    @Test
    void defaultValues_shouldBeSet() {
        OracleProperties props = new OracleProperties();

        assertFalse(props.isEnabled());
        assertNull(props.getDefaultName());
        assertTrue(props.getDatasources().isEmpty());
        assertNull(props.getDefaultDatasourceName());
    }

    @Test
    void getAllDatasourceConfigs_shouldReturnDatasources() {
        OracleProperties props = new OracleProperties();
        DataSourceConfig c = new DataSourceConfig();
        c.setName("default");
        c.setUrl("jdbc:oracle:thin:@host:1521:ORCL");
        props.setDatasources(List.of(c));

        assertEquals(1, props.getAllDatasourceConfigs().size());
        assertEquals("default", props.getAllDatasourceConfigs().get(0).getName());
    }

    @Test
    void defaultDatasourceName_explicit_shouldReturnSetName() {
        OracleProperties props = new OracleProperties();
        props.setDefaultName("primary");

        assertEquals("primary", props.getDefaultDatasourceName());
    }

    @Test
    void defaultDatasourceName_withoutExplicit_shouldReturnFirst() {
        OracleProperties props = new OracleProperties();
        DataSourceConfig c = new DataSourceConfig();
        c.setName("reporting");
        props.setDatasources(List.of(c));

        assertEquals("reporting", props.getDefaultDatasourceName());
    }
}
