package org.turnright.mysqlmcpserver.tool;

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
public class SqlServerExecuteTool implements McpToolHandler {

    private final SqlServerService sqlServerService;

    @Override
    public String getName() {
        return "sqlserver_execute";
    }

    @Override
    public String getDescription() {
        return "Execute INSERT, UPDATE, DELETE, or other modifying SQL statements on SQL Server. " +
               "Returns the number of affected rows. " +
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
                    "description", "The SQL statement to execute (INSERT, UPDATE, DELETE, CREATE, ALTER, DROP)"
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

            if (sql.trim().toUpperCase().startsWith("SELECT")) {
                return McpToolResult.error("SELECT queries should use sqlserver_query tool instead.");
            }

            // Safety check: readOnly mode
            if (sqlServerService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource '" +
                    (datasource != null ? datasource : sqlServerService.getDefaultDatasourceName()) +
                    "' is in read-only mode. Write operations are disabled. " +
                    "Set readOnly=false in configuration to enable writes.");
            }

            int affectedRows = sqlServerService.executeUpdate(datasource, sql);

            return McpToolResult.success("Statement executed successfully on datasource '" +
                (datasource != null ? datasource : sqlServerService.getDefaultDatasourceName()) +
                "'. Affected rows: " + affectedRows);

        } catch (Exception e) {
            log.error("Error executing SQL Server statement: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to execute statement: " + e.getMessage());
        }
    }
}