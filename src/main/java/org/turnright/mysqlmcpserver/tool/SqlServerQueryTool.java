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
public class SqlServerQueryTool implements McpToolHandler {

    private final SqlServerService sqlServerService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "sqlserver_query";
    }

    @Override
    public String getDescription() {
        return "Execute a SELECT query on the SQL Server database and return results. " +
               "Supports SELECT, SHOW, DESCRIBE, EXPLAIN, and EXEC statements. " +
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
                "sql", Map.of(
                    "type", "string",
                    "description", "The SQL query to execute (e.g., 'SELECT * FROM dbo.Users')"
                )
            ),
            "required", List.of("sql")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String sql = (String) arguments.get("sql");
            if (sql == null || sql.trim().isEmpty()) {
                return McpToolResult.error("Missing required parameter: sql");
            }

            // Safety check for read-only queries
            String normalizedSql = sql.trim().toUpperCase();
            if (!normalizedSql.startsWith("SELECT") &&
                !normalizedSql.startsWith("SHOW") &&
                !normalizedSql.startsWith("DESCRIBE") &&
                !normalizedSql.startsWith("EXPLAIN") &&
                !normalizedSql.startsWith("EXEC") &&
                !normalizedSql.startsWith("SP_")) {
                return McpToolResult.error("Only SELECT, SHOW, DESCRIBE, EXPLAIN, EXEC, and stored procedure calls are allowed. " +
                    "Use sqlserver_execute for INSERT, UPDATE, DELETE operations.");
            }

            List<Map<String, Object>> results = sqlServerService.executeQuery(datasource, sql);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : sqlServerService.getDefaultDatasourceName(),
                "rows", results,
                "row_count", results.size(),
                "message", "Query executed successfully"
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error executing SQL Server query: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to execute query: " + e.getMessage());
        }
    }
}