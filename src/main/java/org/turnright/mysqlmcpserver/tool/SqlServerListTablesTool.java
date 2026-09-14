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
public class SqlServerListTablesTool implements McpToolHandler {

    private final SqlServerService sqlServerService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "sqlserver_list_tables";
    }

    @Override
    public String getDescription() {
        return "List all tables in the connected SQL Server database. Optionally list all schemas. " +
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
                "include_schemas", Map.of(
                    "type", "boolean",
                    "description", "Also list all schemas in the database (default: false)"
                )
            )
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            List<String> tables = sqlServerService.listTables(datasource);

            Boolean includeSchemas = (Boolean) arguments.getOrDefault("include_schemas", false);
            List<String> schemas = includeSchemas
                ? sqlServerService.listSchemas(datasource)
                : List.of();

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : sqlServerService.getDefaultDatasourceName(),
                "tables", tables,
                "table_count", tables.size(),
                "schemas", schemas
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error listing SQL Server tables: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to list tables: " + e.getMessage());
        }
    }
}