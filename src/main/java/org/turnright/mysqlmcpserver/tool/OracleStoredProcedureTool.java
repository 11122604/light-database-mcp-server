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
 * Oracle 存储过程工具
 * 支持包调用: PACKAGE_NAME.PROCEDURE_NAME
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OracleStoredProcedureTool implements McpToolHandler {

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private OracleService oracleService;

    @Override
    public String getName() {
        return "oracle_stored_procedure";
    }

    @Override
    public String getDescription() {
        return "Execute an Oracle stored procedure or function. " +
               "Supports package calls (PACKAGE.PROCEDURE). " +
               "Supports IN, OUT, and IN OUT parameters. " +
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
                "procedure_name", Map.of(
                    "type", "string",
                    "description", "The procedure name to execute (e.g., 'MY_PROC' or 'MY_PACKAGE.MY_PROC')"
                ),
                "parameters", Map.of(
                    "type", "object",
                    "description", "Input parameters as key-value pairs (optional)",
                    "additionalProperties", Map.of("type", "string")
                ),
                "out_parameters", Map.of(
                    "type", "array",
                    "description", "Names of OUT parameters to retrieve (optional)",
                    "items", Map.of("type", "string")
                )
            ),
            "required", List.of("procedure_name")
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
            String procedureName = (String) arguments.get("procedure_name");
            if (procedureName == null || procedureName.trim().isEmpty()) {
                return McpToolResult.error("Missing required parameter: procedure_name");
            }

            Map<String, Object> params = (Map<String, Object>) arguments.get("parameters");
            List<String> outParams = (List<String>) arguments.get("out_parameters");

            List<Map<String, Object>> results = oracleService.executeStoredProcedure(datasource, procedureName, params, outParams);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : oracleService.getDefaultDatasourceName(),
                "procedure_name", procedureName,
                "results", results,
                "message", "Stored procedure executed successfully"
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error executing Oracle stored procedure: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to execute stored procedure: " + e.getMessage());
        }
    }
}