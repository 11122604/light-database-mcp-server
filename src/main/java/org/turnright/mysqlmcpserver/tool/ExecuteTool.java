package org.turnright.mysqlmcpserver.tool;

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
public class ExecuteTool implements McpToolHandler {

    private final DatabaseService databaseService;

    @Override
    public String getName() {
        return "mysql_execute";
    }

    @Override
    public String getDescription() {
        return "Execute INSERT, UPDATE, DELETE, or other modifying SQL statements on the MySQL database. " +
               "Use this tool to modify data in tables. Returns the number of affected rows. " +
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
                    "description", "The SQL statement to execute (INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, etc.)"
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

            // Safety check: prevent SELECT queries from being executed here
            String normalizedSql = sql.trim().toUpperCase();
            if (normalizedSql.startsWith("SELECT")) {
                return McpToolResult.error("SELECT queries should use mysql_query tool instead.");
            }

            // Safety check: readOnly mode
            if (databaseService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource '" +
                    (datasource != null ? datasource : databaseService.getDefaultDatasourceName()) +
                    "' is in read-only mode. Write operations are disabled. " +
                    "Set readOnly=false in configuration to enable writes.");
            }

            int affectedRows = databaseService.executeUpdate(datasource, sql);

            return McpToolResult.success("Statement executed successfully on datasource '" +
                (datasource != null ? datasource : databaseService.getDefaultDatasourceName()) +
                "'. Affected rows: " + affectedRows);

        } catch (Exception e) {
            log.error("Error executing statement: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to execute statement: " + e.getMessage());
        }
    }
}