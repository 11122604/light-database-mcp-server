package org.turnright.mysqlmcpserver.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ElasticsearchPropertiesTest {

    @Test
    void defaultValues_shouldBeSet() {
        ElasticsearchProperties props = new ElasticsearchProperties();

        assertFalse(props.isEnabled());
        assertNull(props.getDefaultName());
        assertTrue(props.getDatasources().isEmpty());
        assertNull(props.getDefaultDatasourceName());
    }

    @Test
    void getAllDatasourceConfigs_shouldReturnDatasources() {
        ElasticsearchProperties props = new ElasticsearchProperties();
        ElasticsearchDataSourceConfig c = new ElasticsearchDataSourceConfig();
        c.setName("default");
        c.setHost("localhost");
        c.setPort(9200);
        props.setDatasources(List.of(c));

        assertEquals(1, props.getAllDatasourceConfigs().size());
        assertEquals("default", props.getAllDatasourceConfigs().get(0).getName());
    }

    @Test
    void defaultDatasourceName_explicit_shouldReturnSetName() {
        ElasticsearchProperties props = new ElasticsearchProperties();
        props.setDefaultName("primary");
        ElasticsearchDataSourceConfig c = new ElasticsearchDataSourceConfig();
        c.setName("secondary");
        props.setDatasources(List.of(c));

        assertEquals("primary", props.getDefaultDatasourceName());
    }

    @Test
    void defaultDatasourceName_withoutExplicit_shouldReturnFirst() {
        ElasticsearchProperties props = new ElasticsearchProperties();
        ElasticsearchDataSourceConfig c = new ElasticsearchDataSourceConfig();
        c.setName("archive");
        props.setDatasources(List.of(c));

        assertEquals("archive", props.getDefaultDatasourceName());
    }
}
