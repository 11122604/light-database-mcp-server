package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.OracleService;

import java.util.List;
import java.util.Map;

/**
 * Oracle 表结构工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OracleTableSchemaTool implements McpToolHandler {

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private OracleService oracleService;

    @Override
    public String getName() {
        return "oracle_table_schema";
    }

    @Override
    public String getDescription() {
        return "Get the schema information for a specific Oracle table including column names, data types, nullability, defaults, and remarks. " +
               "Supports OWNER.TABLE_NAME format. " +
               "Optional 'datasource' parameter to specify which configured Oracle datasource to use.";
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
                    "description", "The table name to get schema for (e.g., 'USERS' or 'SCHEMA.USERS')"
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
        log.info("Tool {} called", getName());

        if (oracleService == null) {
            return McpToolResult.error("Oracle is not enabled. Set ORACLE_ENABLED=true to use Oracle tools.");
        }

        try {
            String datasource = (String) arguments.get("datasource");
            String tableName = (String) arguments.get("table_name");
            if (tableName == null || tableName.trim().isEmpty()) {
                return McpToolResult.error("Missing required parameter: table_name");
            }

            boolean includeIndexes = Boolean.TRUE.equals(arguments.get("include_indexes"));
            boolean includePrimaryKey = Boolean.TRUE.equals(arguments.get("include_primary_key"));

            List<Map<String, Object>> columns = oracleService.getTableSchema(datasource, tableName);

            Map<String, Object> result = Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : oracleService.getDefaultDatasourceName(),
                "table_name", tableName,
                "columns", columns,
                "column_count", columns.size()
            );

            if (includeIndexes) {
                List<Map<String, Object>> indexes = oracleService.getTableIndexes(datasource, tableName);
                result = new java.util.HashMap<>(result);
                result.put("indexes", indexes);
            }

            if (includePrimaryKey) {
                List<Map<String, Object>> primaryKey = oracleService.getTablePrimaryKey(datasource, tableName);
                result = new java.util.HashMap<>(result);
                result.put("primary_key", primaryKey);
            }

            String jsonResult = objectMapper.writeValueAsString(result);
            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error getting Oracle table schema: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to get table schema: " + e.getMessage());
        }
    }
}