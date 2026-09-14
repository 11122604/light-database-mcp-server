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
public class EsListIndicesTool implements McpToolHandler {

    private final ElasticsearchService esService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "es_list_indices";
    }

    @Override
    public String getDescription() {
        return "List all indices in the Elasticsearch cluster. " +
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
                )
            )
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            List<String> indices = esService.listIndices(datasource);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : esService.getDefaultDatasourceName(),
                "indices", indices,
                "count", indices.size()
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error listing Elasticsearch indices: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to list indices: " + e.getMessage());
        }
    }
}