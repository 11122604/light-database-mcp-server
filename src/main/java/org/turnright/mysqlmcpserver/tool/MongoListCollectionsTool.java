package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.MongoService;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MongoService.class)
public class MongoListCollectionsTool implements McpToolHandler {

    private final MongoService mongoService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "mongo_list_collections";
    }

    @Override
    public String getDescription() {
        return "List all collections in a MongoDB database, or list all databases. " +
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
                    "description", "Database name (optional - if not provided, lists all databases)"
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

            if (database == null || database.isEmpty()) {
                // List all databases
                List<String> databases = mongoService.listDatabases(datasource);
                String jsonResult = objectMapper.writeValueAsString(Map.of(
                    "success", true,
                    "datasource", datasource != null ? datasource : mongoService.getDefaultDatasourceName(),
                    "databases", databases,
                    "count", databases.size()
                ));
                return McpToolResult.success(jsonResult);
            } else {
                // List collections in the specified database
                List<String> collections = mongoService.listCollections(datasource, database);
                String jsonResult = objectMapper.writeValueAsString(Map.of(
                    "success", true,
                    "datasource", datasource != null ? datasource : mongoService.getDefaultDatasourceName(),
                    "database", database,
                    "collections", collections,
                    "count", collections.size()
                ));
                return McpToolResult.success(jsonResult);
            }

        } catch (Exception e) {
            log.error("Error listing MongoDB collections: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to list collections: " + e.getMessage());
        }
    }
}