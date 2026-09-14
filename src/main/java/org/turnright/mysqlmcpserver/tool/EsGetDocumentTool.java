package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
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
public class EsGetDocumentTool implements McpToolHandler {

    private final ElasticsearchService esService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "es_get_document";
    }

    @Override
    public String getDescription() {
        return "Get a specific document by ID from an Elasticsearch index. " +
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

            Map<String, Object> result = esService.getDocument(datasource, index, id);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : esService.getDefaultDatasourceName(),
                "index", index,
                "id", id,
                "document", result
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error getting Elasticsearch document: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to get document: " + e.getMessage());
        }
    }
}