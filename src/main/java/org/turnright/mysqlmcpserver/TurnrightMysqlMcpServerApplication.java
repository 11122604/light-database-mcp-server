package org.turnright.mysqlmcpserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.turnright.mysqlmcpserver.config.*;
import org.turnright.mysqlmcpserver.mcp.McpServer;
import org.turnright.mysqlmcpserver.mcp.McpServerCore;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties({
    McpTransportProperties.class,
    DatabaseProperties.class,
    SqlServerProperties.class,
    MongoProperties.class,
    ElasticsearchProperties.class,
    RedisProperties.class
})
public class TurnrightMysqlMcpServerApplication {

    public static void main(String[] args) {
        log.info("Starting turnright-database-mcp-server...");

        // Load mcp-config.env file into Properties (for Spring Boot ConfigurationProperties binding)
        Properties configProps = loadEnvFile("mcp-config.env");

        // Parse -p/--port argument for simple port specification
        String portArg = parsePortArgument(args);

        // 解析传输模式（优先级：System property -D > 环境变量 MCP_TRANSPORT > 配置文件 > stdio）
        String transport = resolveTransport(configProps);
        // 把解析出的权威传输模式写回 System property（最高优先级）：
        // 保证 @ConditionalOnProperty(mcp.transport=http) 与下方的 web 开关决策一致。
        // 否则 application.yaml 的 mcp.transport: ${MCP_TRANSPORT:stdio} 会以默认 stdio
        // 覆盖 defaultProperties 里的 http，造成"web 空跑 + stdio runner 阻塞"的错位。
        System.setProperty("mcp.transport", transport);

        SpringApplication app = new SpringApplication(TurnrightMysqlMcpServerApplication.class);

        // 解析端口（优先级：命令行 -p > -Dmcp.port > MCP_PORT 环境变量 > 配置文件）
        String port = resolvePort(portArg, configProps);
        if (port != null) {
            // Set as System property for Tomcat (high priority)
            System.setProperty("server.port", port);
            configProps.setProperty("server.port", port);
            log.info("Port set to: {}", port);
        }

        // Disable web server for stdio mode
        if ("stdio".equalsIgnoreCase(transport)) {
            configProps.setProperty("spring.main.web-application-type", "none");
            log.info("Running in stdio transport mode (web server disabled)");
        } else {
            log.info("Running in HTTP/SSE transport mode (web server enabled)");
        }

        // Use Spring Boot's default properties (picked up by ConfigurationProperties)
        app.setDefaultProperties(configProps);
        app.run(args);
    }

    /**
     * Parse -p or --port argument from command line
     * Supports: -p 9000, -p9000, --port 9000, --port=9000
     */
    private static String parsePortArgument(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-p") || arg.equals("--port")) {
                if (i + 1 < args.length) {
                    return args[i + 1];
                }
            } else if (arg.length() > 2 && arg.charAt(1) == 'p' && Character.isDigit(arg.charAt(2))) {
                // 仅匹配 -p9000 这类紧凑端口写法，避免把 -proxy 等误当端口
                return arg.substring(2);
            } else if (arg.startsWith("--port=")) {
                return arg.substring(7);
            }
        }
        return null;
    }

    /**
     * 解析传输模式。优先级：System property(-D，start.bat) > OS 环境变量 MCP_TRANSPORT(start.sh)
     * > 配置文件 mcp-config.env > 默认 stdio。
     */
    private static String resolveTransport(Properties configProps) {
        String fromSystem = System.getProperty("mcp.transport");
        if (fromSystem != null && !fromSystem.isEmpty()) {
            return fromSystem;
        }
        String fromEnv = System.getenv("MCP_TRANSPORT");
        if (fromEnv != null && !fromEnv.isEmpty()) {
            return fromEnv;
        }
        String fromFile = configProps.getProperty("mcp.transport");
        if (fromFile != null && !fromFile.isEmpty()) {
            return fromFile;
        }
        return "stdio";
    }

    /**
     * 解析端口。优先级：命令行 -p > System property(-Dmcp.port) > OS 环境变量 MCP_PORT > 配置文件。
     */
    private static String resolvePort(String portArg, Properties configProps) {
        if (portArg != null) {
            return portArg;
        }
        String fromSystem = System.getProperty("mcp.port");
        if (fromSystem != null && !fromSystem.isEmpty()) {
            return fromSystem;
        }
        String fromEnv = System.getenv("MCP_PORT");
        if (fromEnv != null && !fromEnv.isEmpty()) {
            return fromEnv;
        }
        return configProps.getProperty("mcp.port");
    }

    /**
     * Normalize environment variable keys to Spring Boot property names
     * Maps config file keys to correct @ConfigurationProperties paths
     * 包级可见，便于单元测试直接验证映射规则
     */
    static String normalizeKey(String key) {
        // MCP transport properties
        if (key.equals("MCP_TRANSPORT")) return "mcp.transport";
        if (key.equals("MCP_PORT")) return "mcp.port";
        if (key.equals("MCP_ADMIN_TOKEN")) return "mcp.admin-token";

        // MySQL properties (prefix: database.mysql)
        if (key.equals("MYSQL_ENABLED")) return "database.mysql.enabled";
        if (key.equals("MYSQL_DEFAULT_DATASOURCE")) return "database.mysql.default-name";
        if (key.startsWith("MYSQL_DATASOURCES_")) {
            // Multi-datasource: MYSQL_DATASOURCES_0_NAME -> database.mysql.datasources[0].name
            return normalizeDatasourceKey(key, "database.mysql");
        }

        // SQL Server properties (prefix: database.sqlserver)
        if (key.equals("SQLSERVER_ENABLED")) return "database.sqlserver.enabled";
        if (key.equals("SQLSERVER_DEFAULT_DATASOURCE")) return "database.sqlserver.default-name";
        if (key.startsWith("SQLSERVER_DATASOURCES_")) {
            return normalizeDatasourceKey(key, "database.sqlserver");
        }
        if (key.startsWith("SQLSERVER_")) {
            String suffix = key.substring("SQLSERVER_".length()).toLowerCase();
            if (suffix.equals("readonly")) suffix = "read-only";
            return "database.sqlserver." + suffix;
        }

        // MongoDB properties (prefix: database.mongodb)
        if (key.equals("MONGODB_ENABLED")) return "database.mongodb.enabled";
        if (key.equals("MONGODB_DEFAULT_DATASOURCE")) return "database.mongodb.default-name";
        if (key.startsWith("MONGODB_DATASOURCES_")) {
            return normalizeDatasourceKey(key, "database.mongodb");
        }
        if (key.startsWith("MONGODB_")) {
            String suffix = key.substring("MONGODB_".length()).toLowerCase();
            if (suffix.equals("readonly")) suffix = "read-only";
            return "database.mongodb." + suffix;
        }

        // Elasticsearch properties (prefix: database.elasticsearch)
        if (key.equals("ES_ENABLED")) return "database.elasticsearch.enabled";
        if (key.equals("ES_DEFAULT_DATASOURCE")) return "database.elasticsearch.default-name";
        if (key.startsWith("ES_DATASOURCES_")) {
            return normalizeDatasourceKey(key, "database.elasticsearch");
        }
        if (key.startsWith("ES_")) {
            String suffix = key.substring("ES_".length()).toLowerCase();
            if (suffix.equals("readonly")) suffix = "read-only";
            return "database.elasticsearch." + suffix;
        }

        // Redis properties (prefix: database.redis)
        if (key.equals("REDIS_ENABLED")) return "database.redis.enabled";
        if (key.equals("REDIS_DEFAULT_DATASOURCE")) return "database.redis.default-name";
        if (key.startsWith("REDIS_DATASOURCES_")) {
            return normalizeDatasourceKey(key, "database.redis");
        }
        if (key.startsWith("REDIS_")) {
            String suffix = key.substring("REDIS_".length()).toLowerCase();
            if (suffix.equals("readonly")) suffix = "read-only";
            return "database.redis." + suffix;
        }

        // Oracle properties (prefix: database.oracle)
        if (key.equals("ORACLE_ENABLED")) return "database.oracle.enabled";
        if (key.equals("ORACLE_DEFAULT_DATASOURCE")) return "database.oracle.default-name";
        if (key.startsWith("ORACLE_DATASOURCES_")) {
            return normalizeDatasourceKey(key, "database.oracle");
        }
        if (key.startsWith("ORACLE_")) {
            String suffix = key.substring("ORACLE_".length()).toLowerCase();
            if (suffix.equals("readonly")) suffix = "read-only";
            return "database.oracle." + suffix;
        }

        // Default: convert underscores to dots and lowercase
        return key.toLowerCase().replace('_', '.');
    }

    /**
     * Normalize multi-datasource indexed keys
     * MYSQL_DATASOURCES_0_NAME -> database.mysql.datasources[0].name
     */
    private static String normalizeDatasourceKey(String key, String prefix) {
        // Pattern: <DB>_DATASOURCES_<N>_<FIELD>
        // Extract index and field name
        String rest = key.substring(key.indexOf("DATASOURCES_") + "DATASOURCES_".length());
        // rest is like "0_NAME" or "1_READONLY"
        int underscoreIdx = rest.indexOf('_');
        if (underscoreIdx > 0) {
            String index = rest.substring(0, underscoreIdx);
            String field = rest.substring(underscoreIdx + 1).toLowerCase();
            // Handle readOnly -> read-only conversion
            if (field.equals("readonly")) field = "read-only";
            return prefix + ".datasources[" + index + "]." + field;
        }
        return prefix + "." + key.toLowerCase().replace('_', '.');
    }

    /**
     * Load .env file and return Properties for Spring Boot
     * Handles special characters like &, !, = correctly
     * Sets both System properties (high priority) and returns Properties (fallback)
     */
    private static Properties loadEnvFile(String filename) {
        Properties props = new Properties();
        File envFile = new File(filename);
        if (!envFile.exists()) {
            envFile = new File(System.getProperty("user.dir"), filename);
        }
        if (!envFile.exists()) {
            log.debug("Config file {} not found, using environment variables", filename);
            return props;
        }

        log.info("Loading config file: {}", envFile.getAbsolutePath());
        // mcp-config.env 统一按 UTF-8 读取（FileReader 会按平台编码，Windows/GBK 下中文乱码）
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(envFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip empty lines and comments
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                // Parse KEY=value
                int eqIdx = trimmed.indexOf('=');
                if (eqIdx > 0) {
                    String key = trimmed.substring(0, eqIdx).trim();
                    String value = trimmed.substring(eqIdx + 1);  // Don't trim value to preserve spaces

                    // Normalize common keys for Spring Boot compatibility
                    String normalizedKey = normalizeKey(key);

                    // mcp.transport / mcp.port 不写入 System property：
                    // 否则文件值会以 System property 的最高优先级压过 start.sh 的 MCP_TRANSPORT
                    // 和 start.bat 的 -D。这两个键只放进 configProps，经 app.setDefaultProperties
                    // 提供给 Spring（优先级低于 System property / OS 环境变量，正好允许外部覆盖）。
                    if (!"mcp.transport".equals(normalizedKey) && !"mcp.port".equals(normalizedKey)) {
                        // Set as System property (high priority for ConfigurationProperties binding)
                        if (System.getProperty(normalizedKey) == null) {
                            System.setProperty(normalizedKey, value);
                        }
                    }

                    // Also add to Properties (fallback)
                    props.setProperty(normalizedKey, value);

                    log.debug("Loaded: {}={}", normalizedKey, value.length() > 50 ? value.substring(0, 50) + "..." : value);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load config file {}: {}", filename, e.getMessage());
        }
        return props;
    }

    /**
     * Stdio transport mode - runs MCP server on stdin/stdout
     * Only enabled when mcp.transport=stdio (no default fallback)
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.transport", havingValue = "stdio")
    public McpServer mcpStdioServer(ObjectMapper objectMapper, McpServerCore mcpServerCore) {
        log.info("Initializing MCP Server with stdio transport");
        return new McpServer(objectMapper, mcpServerCore);
    }

    /**
     * Stdio mode runner - blocks on stdin
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.transport", havingValue = "stdio")
    public CommandLineRunner stdioRunner(McpServer mcpServer, McpServerCore mcpServerCore) {
        return args -> {
            log.info("MCP Server initialized (stdio mode)");
            log.info("Available tools: {}", mcpServerCore.getToolNames());
            log.info("Starting MCP Server stdin transport...");
            mcpServer.run();
        };
    }

    /**
     * HTTP/SSE mode info runner - just logs startup info
     */
    @Bean
    @ConditionalOnProperty(name = "mcp.transport", havingValue = "http")
    public CommandLineRunner httpRunner(McpTransportProperties properties, McpServerCore mcpServerCore) {
        return args -> {
            // 实际监听端口：-p/配置文件优先写入 server.port，否则取 mcp.port 配置
            String actualPort = System.getProperty("server.port", String.valueOf(properties.getPort()));
            log.info("MCP Server initialized (HTTP mode)");
            log.info("Streamable HTTP: http://localhost:{}/mcp", actualPort);
            log.info("SSE endpoint (legacy): http://localhost:{}/mcp/sse", actualPort);
            log.info("Message endpoint (legacy): http://localhost:{}/mcp/message", actualPort);
            log.info("Health check: http://localhost:{}/mcp/health", actualPort);
            log.info("Available tools: {}", mcpServerCore.getToolNames());
        };
    }
}