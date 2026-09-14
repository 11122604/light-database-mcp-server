package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.MongoService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MongoService.class)
public class MongoDatabaseInfoTool implements McpToolHandler {

    private final MongoService mongoService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "mongo_database_info";
    }

    @Override
    public String getDescription() {
        return "Get MongoDB connection and server information, or collection statistics. " +
               "Optional 'datasource' parameter to specify which configured MongoDB datasource to use.";
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
                "database", Map.of(
                    "type", "string",
                    "description", "Database name (optional)"
                ),
                "collection", Map.of(
                    "type", "string",
                    "description", "Collection name (optional - if provided with database, returns collection stats)"
                )
            )
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String database = (String) arguments.get("database");
            String collection = (String) arguments.get("collection");

            if (database != null && collection != null) {
                // Return collection stats
                Map<String, Object> stats = mongoService.getCollectionStats(datasource, database, collection);
                String jsonResult = objectMapper.writeValueAsString(Map.of(
                    "success", true,
                    "datasource", datasource != null ? datasource : mongoService.getDefaultDatasourceName(),
                    "database", database,
                    "collection", collection,
                    "stats", stats
                ));
                return McpToolResult.success(jsonResult);
            } else {
                // Return general database info
                Map<String, Object> info = mongoService.getDatabaseInfo(datasource);
                String jsonResult = objectMapper.writeValueAsString(Map.of(
                    "success", true,
                    "datasource", datasource != null ? datasource : mongoService.getDefaultDatasourceName(),
                    "connection_info", info
                ));
                return McpToolResult.success(jsonResult);
            }

        } catch (Exception e) {
            log.error("Error getting MongoDB info: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to get info: " + e.getMessage());
        }
    }
}