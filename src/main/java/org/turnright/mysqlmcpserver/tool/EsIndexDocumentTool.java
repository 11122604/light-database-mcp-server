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
public class EsIndexDocumentTool implements McpToolHandler {

    private final ElasticsearchService esService;

    @Override
    public String getName() {
        return "es_index_document";
    }

    @Override
    public String getDescription() {
        return "Index a document into Elasticsearch. Optionally provide an ID, or let Elasticsearch generate one. " +
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
                    "description", "Document ID (optional - will be auto-generated if not provided)"
                ),
                "document", Map.of(
                    "type", "object",
                    "description", "Document content to index"
                )
            ),
            "required", List.of("index", "document")
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

            if (index == null || document == null) {
                return McpToolResult.error("Missing required parameters: index and document");
            }

            // Safety check: readOnly mode
            if (esService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource '" +
                    (datasource != null ? datasource : esService.getDefaultDatasourceName()) +
                    "' is in read-only mode. Write operations are disabled. " +
                    "Set readOnly=false in configuration to enable writes.");
            }

            String generatedId = esService.indexDocument(datasource, index, id, document);

            return McpToolResult.success("Document indexed successfully on datasource '" +
                (datasource != null ? datasource : esService.getDefaultDatasourceName()) +
                "'. ID: " + generatedId);

        } catch (Exception e) {
            log.error("Error indexing Elasticsearch document: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to index document: " + e.getMessage());
        }
    }
}