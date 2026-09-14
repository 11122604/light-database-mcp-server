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
public class EsIndexInfoTool implements McpToolHandler {

    private final ElasticsearchService esService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "es_index_info";
    }

    @Override
    public String getDescription() {
        return "Get information about an Elasticsearch index including mappings, settings, and aliases. " +
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

            if (index == null) {
                return McpToolResult.error("Missing required parameter: index");
            }

            Map<String, Object> info = esService.getIndexInfo(datasource, index);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : esService.getDefaultDatasourceName(),
                "index", index,
                "info", info
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error getting Elasticsearch index info: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to get index info: " + e.getMessage());
        }
    }
}