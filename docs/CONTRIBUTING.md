# 贡献指南

## 开发环境

- Java 11+
- Maven 3.6+（使用 mvnw wrapper）
- Lombok IDE 插件
- 涉及 Oracle 的开发需自备 JDBC 驱动，见 [oracle-setup.md](oracle-setup.md)（仓库 `lib/ojdbc11.jar` 目前是占位文件）

## 构建与运行

| 命令 | 说明 |
|------|------|
| `./mvnw clean package` | 完整构建（含测试） |
| `./mvnw clean package -DskipTests` | 跳过测试构建 |
| `./mvnw test` | 运行测试并生成覆盖率报告 |
| `./mvnw jacoco:report` | 单独生成覆盖率报告 |
| `./mvnw spring-boot:run` | IDE 开发启动 |

> 数据源配置（可选）、启动参数与传输优先级见 [USAGE.md](../USAGE.md)。

## 配置与端点

- 数据源配置格式、环境变量字段、优先级与启动方式：[USAGE.md](../USAGE.md)
- HTTP 端点（Streamable HTTP / legacy SSE / 健康检查 / 管理页）：[RUNBOOK.md](RUNBOOK.md#http-端点)

## 测试

```bash
./mvnw test
```

覆盖率报告: `target/site/jacoco/index.html`

**覆盖率要求: 80%+**（低于阈值构建失败）

测试依赖:
- H2 数据库（JDBC 单元测试）
- Embedded MongoDB（MongoDB 单元测试）
- Reactor Test（SSE 测试）

## 数据源配置

六种数据库（MySQL、SQL Server、MongoDB、Elasticsearch、Oracle、Redis）均可选配，每种支持多数据源，单数据源写法（`DATABASE_URL` 等）已移除；未配置任何数据源时服务正常启动，`list_datasources` 返回提示。

配置格式、字段表、多数据源示例与 `READONLY` 安全语义见 [USAGE.md 环境变量配置](../USAGE.md#环境变量配置)。

> 开发时注意：`mcp-config.env` 由 **Java 直接读取**，不经 Shell 解析，因此 URL 中的 `&`、密码中的 `!@#$` 等特殊字符可原样写入，无需转义。

## 项目结构

```
src/main/java/org/turnright/mysqlmcpserver/
├── config/                    # 配置类：各库 Properties / Config + 条件装配
│   ├── DatabaseProperties.java / DatabaseConfig.java              # MySQL
│   ├── SqlServerProperties.java / SqlServerConfig.java
│   ├── MongoProperties.java / MongoConfig.java
│   ├── ElasticsearchProperties.java / ElasticsearchConfig.java
│   ├── OracleProperties.java / OracleConfig.java
│   ├── RedisProperties.java / RedisConfig.java
│   ├── McpTransportProperties.java
│   └── DataSourceConfig.java  # JDBC 单数据源配置模型
├── registry/                  # 数据源注册表（Jdbc / Mongo / Redis / Elasticsearch）
├── service/                   # 数据库业务服务（一库一 Service）
├── tool/                      # MCP 工具（48 个，按启用的数据源条件注册）
│   ├── McpToolHandler.java    # 工具接口（name / description / inputSchema / execute）
│   ├── QueryTool.java         # 查询（只允许 SELECT / SHOW / DESCRIBE / EXPLAIN）
│   ├── ExecuteTool.java       # 写入（有 readOnly 检查）
│   └── ListDataSourcesTool.java
├── mcp/                       # MCP 协议实现
│   ├── McpServerCore.java     # 工具注册和分发（JSON-RPC）
│   └── McpServer.java         # stdio 传输
├── controller/                # HTTP 端点
│   ├── McpStreamableHttpController.java   # Streamable HTTP（/mcp）
│   └── McpSseController.java              # 旧版 SSE（/mcp/sse）
├── admin/                     # 管理界面（页面 + REST API + 配置写回与热生效）
└── model/                     # JSON-RPC 与 MCP 数据模型
```

### 工具与数据源的装配关系

工具通过 Spring 条件装配按启用的数据源动态注册：

```
<DB>_ENABLED=true
  → *Config   @ConditionalOnProperty       创建 Registry Bean
  → *Service  @ConditionalOnBean(Registry) 创建 Service Bean
  → *Tool     @ConditionalOnBean(Service)  注册工具
```

> **Oracle 例外**：6 个 Oracle 工具类没有 `@ConditionalOnBean`，改用 `@Autowired(required = false)` 注入 `OracleService`，因此始终注册，未启用时调用返回"未配置"错误。新增工具请遵循上面的装配链，避免同类不一致。

## 添加新工具

```java
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(DatabaseService.class)   // 按数据源动态注册，见上文装配链
public class MyTool implements McpToolHandler {

    private final DatabaseService databaseService;

    @Override
    public String getName() {
        return "mysql_my_tool";   // 工具名带库前缀，保持全局唯一
    }

    @Override
    public String getDescription() {
        return "Describe what this tool does, and whether it is a WRITE operation.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "datasource", Map.of("type", "string", "description", "数据源名（可选，缺省用默认源）"),
                "sql", Map.of("type", "string", "description", "SQL 语句")
            ),
            "required", List.of("sql")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> args) {
        String datasource = (String) args.get("datasource");
        String sql = (String) args.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            return McpToolResult.error("Missing required parameter: sql");
        }
        // 写入操作必须先检查 readOnly
        if (databaseService.isReadOnly(datasource)) {
            return McpToolResult.error("SAFETY: Datasource is in read-only mode. Write operations are disabled. "
                + "Set readOnly=false in configuration to enable writes.");
        }
        return McpToolResult.success(result);
    }
}
```

### 工具开发规范

- 工具名带库前缀（如 `mysql_`、`es_`），全局唯一
- 实现 `getInputSchema()`，明确定义参数类型与 `required` 列表；除 `list_datasources` 外都应支持可选 `datasource` 参数
- 验证所有用户输入，缺参时返回 `McpToolResult.error`
- 写入操作必须先检查 `service.isReadOnly(datasource)`，并返回统一的 `SAFETY: Datasource is in read-only mode...` 文案
- 用类级 `@ConditionalOnBean(<Service>.class)` 按数据源动态注册；不要用字段级 `@Autowired(required = false)` 绕过条件装配（Oracle 工具是历史例外，勿再沿用）
- `datasource` 为 null 时由 Service 选择默认数据源
- 为工具补充单元测试（参考 `src/test/java/.../tool/` 下现有测试）

## 提交格式

```
<type>: <description>
```

类型: feat, fix, refactor, docs, test, chore, perf, ci