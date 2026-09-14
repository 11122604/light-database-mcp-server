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
 * Oracle 查询工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OracleQueryTool implements McpToolHandler {

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private OracleService oracleService;

    @Override
    public String getName() {
        return "oracle_query";
    }

    @Override
    public String getDescription() {
        return "Execute a SELECT query on the Oracle database and return results. " +
               "Supports SELECT, DESCRIBE, EXPLAIN, and WITH (CTE) statements. " +
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
                "sql", Map.of(
                    "type", "string",
                    "description", "The SQL query to execute (e.g., 'SELECT * FROM USERS')"
                )
            ),
            "required", List.of("sql")
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
            String sql = (String) arguments.get("sql");
            if (sql == null || sql.trim().isEmpty()) {
                return McpToolResult.error("Missing required parameter: sql");
            }

            // Safety check for read-only queries
            String normalizedSql = sql.trim().toUpperCase();
            if (!normalizedSql.startsWith("SELECT") &&
                !normalizedSql.startsWith("DESC") &&
                !normalizedSql.startsWith("DESCRIBE") &&
                !normalizedSql.startsWith("EXPLAIN") &&
                !normalizedSql.startsWith("WITH")) {
                return McpToolResult.error("Only SELECT, DESC, DESCRIBE, EXPLAIN, and WITH queries are allowed. " +
                    "Use oracle_execute for INSERT, UPDATE, DELETE, MERGE operations.");
            }

            List<Map<String, Object>> results = oracleService.executeQuery(datasource, sql);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : oracleService.getDefaultDatasourceName(),
                "rows", results,
                "row_count", results.size(),
                "message", "Query executed successfully"
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error executing Oracle query: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to execute query: " + e.getMessage());
        }
    }
}