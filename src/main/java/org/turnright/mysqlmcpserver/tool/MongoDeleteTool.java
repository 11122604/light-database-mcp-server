package org.turnright.mysqlmcpserver.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
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
public class MongoDeleteTool implements McpToolHandler {

    private final MongoService mongoService;

    @Override
    public String getName() {
        return "mongo_delete";
    }

    @Override
    public String getDescription() {
        return "Delete documents from a MongoDB collection. Use multi=true to delete multiple documents. " +
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
                    "description", "Database name (optional - defaults to the datasource's configured database)"
                ),
                "collection", Map.of(
                    "type", "string",
                    "description", "Collection name"
                ),
                "filter", Map.of(
                    "type", "object",
                    "description", "Filter to match documents to delete"
                ),
                "multi", Map.of(
                    "type", "boolean",
                    "description", "Delete multiple documents (default: false)"
                )
            ),
            "required", List.of("collection", "filter")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String database = (String) arguments.get("database");
            String collection = (String) arguments.get("collection");
            Map<String, Object> filterMap = (Map<String, Object>) arguments.get("filter");
            boolean multi = arguments.get("multi") instanceof Boolean && (Boolean) arguments.get("multi");

            if (collection == null || collection.isEmpty()) {
                return McpToolResult.error("Missing required parameter: collection");
            }

            // Safety check: readOnly mode
            if (mongoService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource '" +
                    (datasource != null ? datasource : mongoService.getDefaultDatasourceName()) +
                    "' is in read-only mode. Write operations are disabled. " +
                    "Set readOnly=false in configuration to enable writes.");
            }

            // 空 filter 一律拒绝：multi=false 时 deleteOne({}) 也会按自然序删除任意一条文档
            if (filterMap == null || filterMap.isEmpty()) {
                return McpToolResult.error("SAFETY: Cannot delete documents without a filter. " +
                    "Provide a specific filter to select documents to delete.");
            }

            Document filter = new Document(filterMap);
            long deleted = mongoService.deleteDocuments(datasource, database, collection, filter, multi);

            return McpToolResult.success("Delete successful on datasource '" +
                (datasource != null ? datasource : mongoService.getDefaultDatasourceName()) +
                "'. Deleted count: " + deleted);

        } catch (Exception e) {
            log.error("Error executing MongoDB delete: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to delete documents: " + e.getMessage());
        }
    }
}