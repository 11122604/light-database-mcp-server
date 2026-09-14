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
public class MongoUpdateTool implements McpToolHandler {

    private final MongoService mongoService;

    @Override
    public String getName() {
        return "mongo_update";
    }

    @Override
    public String getDescription() {
        return "Update documents in a MongoDB collection. Use multi=true to update multiple documents. " +
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
                    "description", "Filter to match documents"
                ),
                "update", Map.of(
                    "type", "object",
                    "description", "Update operations"
                ),
                "multi", Map.of(
                    "type", "boolean",
                    "description", "Update multiple documents (default: false)"
                )
            ),
            "required", List.of("collection", "filter", "update")
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
            Map<String, Object> updateMap = (Map<String, Object>) arguments.get("update");
            boolean multi = arguments.get("multi") instanceof Boolean && (Boolean) arguments.get("multi");

            if (collection == null || filterMap == null || updateMap == null) {
                return McpToolResult.error("Missing required parameters");
            }

            // 防御空 filter + multi=true 时更新整个集合
            if (multi && filterMap.isEmpty()) {
                return McpToolResult.error("SAFETY: Cannot update all documents with an empty filter. " +
                    "Provide a specific filter to select documents to update.");
            }

            // Safety check: readOnly mode
            if (mongoService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource '" +
                    (datasource != null ? datasource : mongoService.getDefaultDatasourceName()) +
                    "' is in read-only mode. Write operations are disabled. " +
                    "Set readOnly=false in configuration to enable writes.");
            }

            Document filter = new Document(filterMap);
            Document update = new Document(updateMap);

            long modified = mongoService.updateDocuments(datasource, database, collection, filter, update, multi);

            return McpToolResult.success("Update successful on datasource '" +
                (datasource != null ? datasource : mongoService.getDefaultDatasourceName()) +
                "'. Modified count: " + modified);

        } catch (Exception e) {
            log.error("Error executing MongoDB update: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to update documents: " + e.getMessage());
        }
    }
}