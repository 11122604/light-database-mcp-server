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
public class QueryTool implements McpToolHandler {

    private final DatabaseService databaseService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "mysql_query";
    }

    @Override
    public String getDescription() {
        return "Execute a SELECT query on the MySQL database and return results. " +
               "Use this tool to query data from tables. Only supports SELECT statements for safety. " +
               "Optional 'datasource' parameter to specify which configured MySQL datasource to use.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "datasource", Map.of(
                    "type", "string",
                    "description", "Datasource name to use (optional, uses default if not specified). " +
                                   "Available datasources can be listed using mysql_list_datasources tool."
                ),
                "sql", Map.of(
                    "type", "string",
                    "description", "The SELECT SQL query to execute (e.g., 'SELECT * FROM users LIMIT 10')"
                )
            ),
            "required", List.of("sql")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called with arguments: {}", getName(), arguments);

        try {
            String datasource = (String) arguments.get("datasource");
            String sql = (String) arguments.get("sql");
            if (sql == null || sql.trim().isEmpty()) {
                return McpToolResult.error("Missing required parameter: sql");
            }

            // Safety check: only allow SELECT statements
            String normalizedSql = sql.trim().toUpperCase();
            if (!normalizedSql.startsWith("SELECT") &&
                !normalizedSql.startsWith("SHOW") &&
                !normalizedSql.startsWith("DESCRIBE") &&
                !normalizedSql.startsWith("EXPLAIN")) {
                return McpToolResult.error("Only SELECT, SHOW, DESCRIBE, and EXPLAIN queries are allowed. " +
                    "Use mysql_execute for INSERT, UPDATE, DELETE operations.");
            }

            List<Map<String, Object>> results = databaseService.executeQuery(datasource, sql);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : databaseService.getDefaultDatasourceName(),
                "rows", results,
                "row_count", results.size(),
                "message", "Query executed successfully, returned " + results.size() + " rows"
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error executing query: {}", e.getMessage(), e);  // Detailed info only in logs
            return McpToolResult.error("Query execution failed. Please check the query syntax and database permissions.");
        }
    }
}