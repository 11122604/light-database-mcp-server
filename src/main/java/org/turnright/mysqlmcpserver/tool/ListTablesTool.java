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
public class ListTablesTool implements McpToolHandler {

    private final DatabaseService databaseService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "mysql_list_tables";
    }

    @Override
    public String getDescription() {
        return "List all tables in the connected MySQL database. " +
               "Use this tool to discover available tables before querying or getting schema. " +
               "Optional 'datasource' parameter to specify which configured MySQL datasource to use.";
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

        try {
            String datasource = (String) arguments.get("datasource");
            List<String> tables = databaseService.listTables(datasource);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : databaseService.getDefaultDatasourceName(),
                "tables", tables,
                "table_count", tables.size(),
                "message", "Found " + tables.size() + " tables in the database"
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error listing tables: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to list tables: " + e.getMessage());
        }
    }
}