package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.DatabaseService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(DatabaseService.class)
public class DatabaseInfoTool implements McpToolHandler {

    private final DatabaseService databaseService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "mysql_database_info";
    }

    @Override
    public String getDescription() {
        return "Get information about the connected MySQL database including product name, version, " +
               "driver info, and connection details. Useful for understanding the database environment. " +
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
            Map<String, Object> info = databaseService.getDatabaseInfo(datasource);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "database_info", info,
                "message", "Database information retrieved successfully"
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error getting database info: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to get database info: " + e.getMessage());
        }
    }
}