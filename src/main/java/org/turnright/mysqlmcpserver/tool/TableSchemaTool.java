package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.DatabaseService;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(DatabaseService.class)
public class TableSchemaTool implements McpToolHandler {

    private final DatabaseService databaseService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "mysql_table_schema";
    }

    @Override
    public String getDescription() {
        return "Get the schema information for a specific table including column names, data types, " +
               "nullability, defaults, and remarks. Useful for understanding table structure before querying. " +
               "Optional 'datasource' parameter to specify which configured MySQL datasource to use.";
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
                "table_name", Map.of(
                    "type", "string",
                    "description", "The name of the table to get schema for"
                ),
                "include_indexes", Map.of(
                    "type", "boolean",
                    "description", "Whether to include index information (default: false)"
                ),
                "include_primary_key", Map.of(
                    "type", "boolean",
                    "description", "Whether to include primary key information (default: false)"
                )
            ),
            "required", List.of("table_name")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called with arguments: {}", getName(), arguments);

        try {
            String datasource = (String) arguments.get("datasource");
            String tableName = (String) arguments.get("table_name");
            if (tableName == null || tableName.trim().isEmpty()) {
                return McpToolResult.error("Missing required parameter: table_name");
            }

            Boolean includeIndexes = (Boolean) arguments.getOrDefault("include_indexes", false);
            Boolean includePrimaryKey = (Boolean) arguments.getOrDefault("include_primary_key", false);

            List<Map<String, Object>> columns = databaseService.getTableSchema(datasource, tableName);
            List<Map<String, Object>> indexes = includeIndexes
                ? databaseService.getTableIndexes(datasource, tableName)
                : List.of();
            List<Map<String, Object>> primaryKey = includePrimaryKey
                ? databaseService.getTablePrimaryKey(datasource, tableName)
                : List.of();

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : databaseService.getDefaultDatasourceName(),
                "table_name", tableName,
                "columns", columns,
                "column_count", columns.size(),
                "indexes", indexes,
                "primary_key", primaryKey,
                "message", "Schema retrieved successfully for table '" + tableName + "'"
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error getting table schema: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to get table schema: " + e.getMessage());
        }
    }
}