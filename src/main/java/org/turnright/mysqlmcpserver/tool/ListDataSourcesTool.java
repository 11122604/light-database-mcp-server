package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.DatabaseService;
import org.turnright.mysqlmcpserver.service.SqlServerService;
import org.turnright.mysqlmcpserver.service.MongoService;
import org.turnright.mysqlmcpserver.service.ElasticsearchService;
import org.turnright.mysqlmcpserver.service.OracleService;
import org.turnright.mysqlmcpserver.service.RedisService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool to list all available datasources across all database types
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListDataSourcesTool implements McpToolHandler {

    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private DatabaseService databaseService;

    @Autowired(required = false)
    private SqlServerService sqlServerService;

    @Autowired(required = false)
    private MongoService mongoService;

    @Autowired(required = false)
    private ElasticsearchService esService;

    @Autowired(required = false)
    private OracleService oracleService;

    @Autowired(required = false)
    private RedisService redisService;

    @Override
    public String getName() {
        return "list_datasources";
    }

    @Override
    public String getDescription() {
        return "List all available datasources for each database type (MySQL, SQL Server, MongoDB, Elasticsearch, Oracle, Redis). " +
               "Use this tool to discover datasource names before calling other database tools.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "database_type", Map.of(
                    "type", "string",
                    "description", "Database type to list datasources for (optional - if not provided, lists all). " +
                                   "Options: mysql, sqlserver, mongodb, elasticsearch, oracle",
                    "enum", List.of("mysql", "sqlserver", "mongodb", "elasticsearch", "oracle", "redis", "all")
                )
            )
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String databaseType = (String) arguments.getOrDefault("database_type", "all");

            Map<String, Object> result = new HashMap<>();

            // Check if ANY datasources are configured
            boolean hasAnyDatasource = databaseService != null ||
                sqlServerService != null ||
                mongoService != null ||
                esService != null ||
                oracleService != null ||
                redisService != null;

            if ("all".equals(databaseType) || "mysql".equals(databaseType)) {
                if (databaseService != null) {
                    Map<String, Object> mysqlInfo = new HashMap<>();
                    mysqlInfo.put("available_datasources", databaseService.getAvailableDatasources());
                    mysqlInfo.put("default_datasource", databaseService.getDefaultDatasourceName());
                    mysqlInfo.put("datasource_descriptions", databaseService.getDatasourceMetadata());
                    result.put("mysql", mysqlInfo);
                } else {
                    result.put("mysql", Map.of("status", "not_configured"));
                }
            }

            if ("all".equals(databaseType) || "sqlserver".equals(databaseType)) {
                if (sqlServerService != null) {
                    Map<String, Object> sqlServerInfo = new HashMap<>();
                    sqlServerInfo.put("available_datasources", sqlServerService.getAvailableDatasources());
                    sqlServerInfo.put("default_datasource", sqlServerService.getDefaultDatasourceName());
                    sqlServerInfo.put("datasource_descriptions", sqlServerService.getDatasourceMetadata());
                    result.put("sqlserver", sqlServerInfo);
                } else {
                    result.put("sqlserver", Map.of("status", "not_configured"));
                }
            }

            if ("all".equals(databaseType) || "mongodb".equals(databaseType)) {
                if (mongoService != null) {
                    Map<String, Object> mongoInfo = new HashMap<>();
                    mongoInfo.put("available_datasources", mongoService.getAvailableDatasources());
                    mongoInfo.put("default_datasource", mongoService.getDefaultDatasourceName());
                    mongoInfo.put("datasource_descriptions", mongoService.getDatasourceMetadata());
                    result.put("mongodb", mongoInfo);
                } else {
                    result.put("mongodb", Map.of("status", "not_configured"));
                }
            }

            if ("all".equals(databaseType) || "elasticsearch".equals(databaseType)) {
                if (esService != null) {
                    Map<String, Object> esInfo = new HashMap<>();
                    esInfo.put("available_datasources", esService.getAvailableDatasources());
                    esInfo.put("default_datasource", esService.getDefaultDatasourceName());
                    esInfo.put("datasource_descriptions", esService.getDatasourceMetadata());
                    result.put("elasticsearch", esInfo);
                } else {
                    result.put("elasticsearch", Map.of("status", "not_configured"));
                }
            }

            if ("all".equals(databaseType) || "oracle".equals(databaseType)) {
                if (oracleService != null) {
                    Map<String, Object> oracleInfo = new HashMap<>();
                    oracleInfo.put("available_datasources", oracleService.getAvailableDatasources());
                    oracleInfo.put("default_datasource", oracleService.getDefaultDatasourceName());
                    oracleInfo.put("datasource_descriptions", oracleService.getDatasourceMetadata());
                    result.put("oracle", oracleInfo);
                } else {
                    result.put("oracle", Map.of("status", "not_configured"));
                }
            }

            if ("all".equals(databaseType) || "redis".equals(databaseType)) {
                if (redisService != null) {
                    Map<String, Object> redisInfo = new HashMap<>();
                    redisInfo.put("available_datasources", redisService.getAvailableDatasources());
                    redisInfo.put("default_datasource", redisService.getDefaultDatasourceName());
                    redisInfo.put("datasource_descriptions", redisService.getDatasourceMetadata());
                    result.put("redis", redisInfo);
                } else {
                    result.put("redis", Map.of("status", "not_configured"));
                }
            }

            // Add hint when no datasources are configured
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("datasources", result);

            if (!hasAnyDatasource) {
                response.put("hint", "No datasources configured. Please configure at least one datasource in .env or application.yml before using database tools. " +
                    "Examples: MYSQL_ENABLED=true, MYSQL_DATASOURCES_0_URL=jdbc:mysql://localhost:3306/mydb, MONGODB_ENABLED=true, etc.");
            }

            String jsonResult = objectMapper.writeValueAsString(response);

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error listing datasources: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to list datasources: " + e.getMessage());
        }
    }
}