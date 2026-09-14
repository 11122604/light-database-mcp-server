package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.OracleService;

import java.util.Map;

/**
 * Oracle 数据库信息工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OracleDatabaseInfoTool implements McpToolHandler {

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private OracleService oracleService;

    @Override
    public String getName() {
        return "oracle_database_info";
    }

    @Override
    public String getDescription() {
        return "Get information about the connected Oracle database including product name, version, driver info, and connection details. " +
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
                )
            )
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

            Map<String, Object> info = oracleService.getDatabaseInfo(datasource);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "database_info", info
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error getting Oracle database info: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to get database info: " + e.getMessage());
        }
    }
}