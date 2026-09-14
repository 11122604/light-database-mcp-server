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
public class SqlServerStoredProcedureTool implements McpToolHandler {

    private final SqlServerService sqlServerService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "sqlserver_stored_procedure";
    }

    @Override
    public String getDescription() {
        return "Execute a SQL Server stored procedure with optional parameters. Returns the result set if available. " +
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
                "procedure_name", Map.of(
                    "type", "string",
                    "description", "The stored procedure name (e.g., 'dbo.GetUsers' or 'sp_help')"
                ),
                "parameters", Map.of(
                    "type", "object",
                    "description", "Optional parameters as key-value pairs",
                    "additionalProperties", Map.of("type", "object")
                )
            ),
            "required", List.of("procedure_name")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String procedureName = (String) arguments.get("procedure_name");
            if (procedureName == null || procedureName.trim().isEmpty()) {
                return McpToolResult.error("Missing required parameter: procedure_name");
            }

            Map<String, Object> params = (Map<String, Object>) arguments.get("parameters");

            List<Map<String, Object>> results = sqlServerService.executeStoredProcedure(datasource, procedureName, params);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : sqlServerService.getDefaultDatasourceName(),
                "procedure_name", procedureName,
                "results", results,
                "row_count", results.size()
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error executing SQL Server stored procedure: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to execute stored procedure: " + e.getMessage());
        }
    }
}