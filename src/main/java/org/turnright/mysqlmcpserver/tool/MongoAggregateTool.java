package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.MongoService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MongoService.class)
public class MongoAggregateTool implements McpToolHandler {

    private final MongoService mongoService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "mongo_aggregate";
    }

    @Override
    public String getDescription() {
        return "Run an aggregation pipeline on a MongoDB collection. " +
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
                "pipeline", Map.of(
                    "type", "array",
                    "description", "Aggregation pipeline stages (array of objects)",
                    "items", Map.of("type", "object")
                )
            ),
            "required", List.of("collection", "pipeline")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String database = (String) arguments.get("database");
            String collection = (String) arguments.get("collection");
            List<Map<String, Object>> pipelineList = (List<Map<String, Object>>) arguments.get("pipeline");

            if (collection == null || pipelineList == null) {
                return McpToolResult.error("Missing required parameters");
            }

            List<Document> pipeline = new ArrayList<>();
            for (Map<String, Object> stage : pipelineList) {
                pipeline.add(new Document(stage));
            }

            List<Map<String, Object>> results = mongoService.aggregate(datasource, database, collection, pipeline);

            // 动态组装响应：database 未显式指定时（使用数据源默认库）不展示该字段
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("datasource", datasource != null ? datasource : mongoService.getDefaultDatasourceName());
            if (database != null) {
                response.put("database", database);
            }
            response.put("collection", collection);
            response.put("results", results);
            response.put("count", results.size());

            String jsonResult = objectMapper.writeValueAsString(response);

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error executing MongoDB aggregation: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to run aggregation: " + e.getMessage());
        }
    }
}