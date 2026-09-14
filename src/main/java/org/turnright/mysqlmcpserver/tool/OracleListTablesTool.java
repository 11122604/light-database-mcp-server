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
 * Oracle 表列表工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OracleListTablesTool implements McpToolHandler {

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private OracleService oracleService;

    @Override
    public String getName() {
        return "oracle_list_tables";
    }

    @Override
    public String getDescription() {
        return "List all tables in the Oracle database. " +
               "Optionally filter by owner/schema. " +
               "System tables (SYS_, BIN$, MVIEW$) are filtered out. " +
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
                "owner", Map.of(
                    "type", "string",
                    "description", "Owner/schema name to filter tables (optional)"
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
            String owner = (String) arguments.get("owner");

            List<String> tables = oracleService.listTables(datasource, owner);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : oracleService.getDefaultDatasourceName(),
                "owner", owner != null ? owner : "all",
                "tables", tables,
                "table_count", tables.size(),
                "message", "Tables listed successfully"
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error listing Oracle tables: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to list tables: " + e.getMessage());
        }
    }
}