package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.SqlServerService;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(SqlServerService.class)
public class SqlServerTableSchemaTool implements McpToolHandler {

    private final SqlServerService sqlServerService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "sqlserver_table_schema";
    }

    @Override
    public String getDescription() {
        return "Get the schema information for a SQL Server table including columns, data types, indexes, and constraints. " +
               "Optional 'datasource' parameter to specify which configured SQL Server datasource to use.";
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
                    "description", "The table name (e.g., 'dbo.Users' or 'Users')"
                ),
                "include_indexes", Map.of(
                    "type", "boolean",
                    "description", "Include index information (default: false)"
                ),
                "include_primary_key", Map.of(
                    "type", "boolean",
                    "description", "Include primary key information (default: false)"
                )
            ),
            "required", List.of("table_name")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String tableName = (String) arguments.get("table_name");
            if (tableName == null || tableName.trim().isEmpty()) {
                return McpToolResult.error("Missing required parameter: table_name");
            }

            Boolean includeIndexes = (Boolean) arguments.getOrDefault("include_indexes", false);
            Boolean includePrimaryKey = (Boolean) arguments.getOrDefault("include_primary_key", false);

            List<Map<String, Object>> columns = sqlServerService.getTableSchema(datasource, tableName);
            List<Map<String, Object>> indexes = includeIndexes
                ? sqlServerService.getTableIndexes(datasource, tableName)
                : List.of();
            List<Map<String, Object>> primaryKey = includePrimaryKey
                ? sqlServerService.getTablePrimaryKey(datasource, tableName)
                : List.of();

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : sqlServerService.getDefaultDatasourceName(),
                "table_name", tableName,
                "columns", columns,
                "column_count", columns.size(),
                "indexes", indexes,
                "primary_key", primaryKey
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error getting SQL Server table schema: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to get table schema: " + e.getMessage());
        }
    }
}