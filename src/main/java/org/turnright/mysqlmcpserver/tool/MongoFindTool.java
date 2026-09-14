package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.MongoService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MongoService.class)
public class MongoFindTool implements McpToolHandler {

    private final MongoService mongoService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "mongo_find";
    }

    @Override
    public String getDescription() {
        return "Query documents from a MongoDB collection with optional filter, projection, sort, and limit. " +
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
                    "description", "Query filter (MongoDB query syntax as JSON object)"
                ),
                "projection", Map.of(
                    "type", "object",
                    "description", "Fields to include/exclude"
                ),
                "sort", Map.of(
                    "type", "object",
                    "description", "Sort specification (e.g., {\"name\": 1})"
                ),
                "limit", Map.of(
                    "type", "integer",
                    "description", "Maximum number of documents to return (default: 100)"
                )
            ),
            "required", List.of("collection")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String database = (String) arguments.get("database");
            String collection = (String) arguments.get("collection");

            if (collection == null || collection.isEmpty()) {
                return McpToolResult.error("Missing required parameter: collection");
            }

            Document filter = parseDocument(arguments.get("filter"));
            Document projection = parseDocument(arguments.get("projection"));
            Document sort = parseDocument(arguments.get("sort"));
            Object limitVal = arguments.get("limit");
            int limit = limitVal instanceof Number ? ((Number) limitVal).intValue() : 100;

            List<Map<String, Object>> results = mongoService.findDocuments(
                datasource, database, collection, filter, projection, sort, limit
            );

            // 动态组装响应：database 未显式指定时（使用数据源默认库）不展示该字段
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("datasource", datasource != null ? datasource : mongoService.getDefaultDatasourceName());
            if (database != null) {
                response.put("database", database);
            }
            response.put("collection", collection);
            response.put("documents", results);
            response.put("count", results.size());

            String jsonResult = objectMapper.writeValueAsString(response);

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error executing MongoDB find: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to execute find: " + e.getMessage());
        }
    }

    private Document parseDocument(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return new Document((Map<String, Object>) obj);
        }
        return null;
    }
}