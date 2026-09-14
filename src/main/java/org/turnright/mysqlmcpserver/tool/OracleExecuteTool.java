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
 * Oracle 执行工具（INSERT, UPDATE, DELETE, MERGE, DDL）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OracleExecuteTool implements McpToolHandler {

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private OracleService oracleService;

    @Override
    public String getName() {
        return "oracle_execute";
    }

    @Override
    public String getDescription() {
        return "Execute INSERT, UPDATE, DELETE, MERGE, or DDL statements on the Oracle database. " +
               "Returns the number of affected rows. " +
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
                    "description", "The SQL statement to execute (e.g., 'INSERT INTO USERS (ID, NAME) VALUES (1, \"John\")')"
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

            // Safety check: readOnly mode
            if (oracleService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource '" +
                    (datasource != null ? datasource : oracleService.getDefaultDatasourceName()) +
                    "' is in read-only mode. Write operations are disabled. " +
                    "Set readOnly=false in configuration to enable writes.");
            }

            int affectedRows = oracleService.executeUpdate(datasource, sql);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : oracleService.getDefaultDatasourceName(),
                "affected_rows", affectedRows,
                "message", "Statement executed successfully"
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error executing Oracle statement: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to execute statement: " + e.getMessage());
        }
    }
}