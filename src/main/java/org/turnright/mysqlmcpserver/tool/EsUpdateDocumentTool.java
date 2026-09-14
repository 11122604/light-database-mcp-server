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
public class EsUpdateDocumentTool implements McpToolHandler {

    private final ElasticsearchService esService;

    @Override
    public String getName() {
        return "es_update_document";
    }

    @Override
    public String getDescription() {
        return "Update an existing document in Elasticsearch by ID. " +
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
                    "description", "Index name"
                ),
                "id", Map.of(
                    "type", "string",
                    "description", "Document ID"
                ),
                "document", Map.of(
                    "type", "object",
                    "description", "Fields to update"
                )
            ),
            "required", List.of("index", "id", "document")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String index = (String) arguments.get("index");
            String id = (String) arguments.get("id");
            Map<String, Object> document = (Map<String, Object>) arguments.get("document");

            if (index == null || id == null || document == null) {
                return McpToolResult.error("Missing required parameters: index, id, and document");
            }

            // Safety check: readOnly mode
            if (esService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource '" +
                    (datasource != null ? datasource : esService.getDefaultDatasourceName()) +
                    "' is in read-only mode. Write operations are disabled. " +
                    "Set readOnly=false in configuration to enable writes.");
            }

            boolean updated = esService.updateDocument(datasource, index, id, document);

            return McpToolResult.success(updated
                ? "Document updated successfully on datasource '" +
                    (datasource != null ? datasource : esService.getDefaultDatasourceName()) + "'"
                : "Document update failed");

        } catch (Exception e) {
            log.error("Error updating Elasticsearch document: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to update document: " + e.getMessage());
        }
    }
}