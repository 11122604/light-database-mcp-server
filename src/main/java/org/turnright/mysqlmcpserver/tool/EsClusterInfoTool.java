package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.ElasticsearchService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ElasticsearchService.class)
public class EsClusterInfoTool implements McpToolHandler {

    private final ElasticsearchService esService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "es_cluster_info";
    }

    @Override
    public String getDescription() {
        return "Get Elasticsearch cluster information including version, health status, and node count. " +
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
                "include_health", Map.of(
                    "type", "boolean",
                    "description", "Include cluster health status (default: true)"
                )
            )
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            Boolean includeHealth = (Boolean) arguments.getOrDefault("include_health", true);

            Map<String, Object> info = esService.getClusterInfo(datasource);

            if (includeHealth) {
                Map<String, Object> health = esService.getClusterHealth(datasource);
                info.put("health", health);
            }

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : esService.getDefaultDatasourceName(),
                "cluster", info
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error getting Elasticsearch cluster info: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to get cluster info: " + e.getMessage());
        }
    }
}