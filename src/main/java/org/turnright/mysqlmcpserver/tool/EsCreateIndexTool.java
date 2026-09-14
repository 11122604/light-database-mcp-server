package org.turnright.mysqlmcpserver.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.ElasticsearchService;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ElasticsearchService.class)
public class EsCreateIndexTool implements McpToolHandler {

    private final ElasticsearchService esService;

    @Override
    public String getName() {
        return "es_create_index";
    }

    @Override
    public String getDescription() {
        return "Create a new Elasticsearch index with optional mappings and settings. " +
               "Optional 'datasource' parameter to specify which configured Elasticsearch datasource to use.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "datasource", Map.of(
                    "type", "string",
                    "description", "Datasource name to use (optional, uses default if not specified)"
                ),
                "index", Map.of(
                    "type", "string",
                    "description", "Index name to create"
                ),
                "mappings", Map.of(
                    "type", "object",
                    "description", "Index mappings (field definitions)"
                ),
                "settings", Map.of(
                    "type", "object",
                    "description", "Index settings (e.g., number of shards)"
                )
            ),
            "required", List.of("index")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String index = (String) arguments.get("index");
            Map<String, Object> mappings = (Map<String, Object>) arguments.get("mappings");
            Map<String, Object> settings = (Map<String, Object>) arguments.get("settings");

            if (index == null) {
                return McpToolResult.error("Missing required parameter: index");
            }

            // Safety check: readOnly mode
            if (esService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource '" +
                    (datasource != null ? datasource : esService.getDefaultDatasourceName()) +
                    "' is in read-only mode. Write operations are disabled. " +
                    "Set readOnly=false in configuration to enable writes.");
            }

            boolean created = esService.createIndex(datasource, index, mappings, settings);

            return McpToolResult.success(created
                ? "Index created successfully on datasource '" +
                    (datasource != null ? datasource : esService.getDefaultDatasourceName()) + "': " + index
                : "Failed to create index");

        } catch (Exception e) {
            log.error("Error creating Elasticsearch index: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to create index: " + e.getMessage());
        }
    }
}