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
public class EsDeleteDocumentTool implements McpToolHandler {

    private final ElasticsearchService esService;

    @Override
    public String getName() {
        return "es_delete_document";
    }

    @Override
    public String getDescription() {
        return "Delete a document from Elasticsearch by ID. " +
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
                    "description", "Document ID to delete"
                )
            ),
            "required", List.of("index", "id")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String index = (String) arguments.get("index");
            String id = (String) arguments.get("id");

            if (index == null || id == null) {
                return McpToolResult.error("Missing required parameters: index and id");
            }

            // Safety check: readOnly mode
            if (esService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource '" +
                    (datasource != null ? datasource : esService.getDefaultDatasourceName()) +
                    "' is in read-only mode. Write operations are disabled. " +
                    "Set readOnly=false in configuration to enable writes.");
            }

            boolean deleted = esService.deleteDocument(datasource, index, id);

            return McpToolResult.success(deleted
                ? "Document deleted successfully on datasource '" +
                    (datasource != null ? datasource : esService.getDefaultDatasourceName()) + "'"
                : "Document not found");

        } catch (Exception e) {
            log.error("Error deleting Elasticsearch document: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to delete document: " + e.getMessage());
        }
    }
}