# Redis 功能模块设计

**日期**: 2026-06-08  
**状态**: 已确认  
**方案**: A — 完全遵循现有模式

---

## 1. 需求概述

为 turnright-database-mcp-server 添加 Redis 支持，使 AI 代理可以通过 MCP 协议查询和管理 Redis 缓存数据。

- **场景**: 缓存数据查询（主要）
- **权限**: readOnly 开关控制所有操作
- **连接**: 单机直连（host + port + password）

---

## 2. 技术选型

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 客户端 | Jedis 5.x | 同步 API，与现有 JDBC 风格一致 |
| 连接池 | JedisPool | 线程安全，阻塞式获取连接 |
| 配置绑定 | @ConfigurationProperties | 与现有所有数据库配置一致 |
| 工具粒度 | 一个操作一个 Tool | 与现有 34 个 Tool 风格统一 |

---

## 3. 架构

```
mcp-config.env → TurnrightMysqlMcpServerApplication.loadEnvFile()
  → System.setProperty("database.redis.*")
    → RedisProperties (@ConfigurationProperties)
      → RedisConfig (@Configuration, 创建 Bean)
        → RedisDataSourceRegistry (JedisPool 管理)
          → RedisService (业务逻辑 + readOnly 检查)
            → RedisXxxTool (14 个 MCP 工具)
```

---

## 4. 配置文件

### 4.1 config/RedisDataSourceConfig.java

```java
@Data
public class RedisDataSourceConfig {
    private String name;
    private String description;
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int timeout = 2000;
    private int maxTotal = 8;
    private int maxIdle = 8;
    private int minIdle = 0;
    private boolean readOnly = true;
}
```

### 4.2 config/RedisProperties.java

```java
@Data
@ConfigurationProperties(prefix = "database.redis")
public class RedisProperties {
    private boolean enabled = false;
    private String defaultName;

    // 单数据源
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int timeout = 2000;
    private int maxTotal = 8;
    private int maxIdle = 8;
    private int minIdle = 0;
    private String description;
    private boolean readOnly = true;

    // 多数据源
    private List<RedisDataSourceConfig> datasources = new ArrayList<>();

    public List<RedisDataSourceConfig> getAllDatasourceConfigs() { ... }
    public String getDefaultDatasourceName() { ... }
}
```

### 4.3 config/RedisConfig.java

```java
@Configuration
@EnableConfigurationProperties(RedisProperties.class)
@RequiredArgsConstructor
public class RedisConfig {

    @Bean
    @ConditionalOnProperty(prefix = "database.redis", name = "enabled", havingValue = "true")
    public RedisDataSourceRegistry redisDataSourceRegistry() { ... }
}
```

### 4.4 mcp-config.env 配置示例

```bash
# Redis
REDIS_ENABLED=true
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=mypassword
REDIS_DESCRIPTION=Redis缓存服务器
REDIS_READONLY=true

# Redis 多数据源（可选）
# REDIS_DATASOURCES_0_NAME=primary
# REDIS_DATASOURCES_0_HOST=redis-prod
# REDIS_DATASOURCES_0_PORT=6379
# REDIS_DATASOURCES_0_PASSWORD=pass
# REDIS_DATASOURCES_0_DESCRIPTION=生产Redis缓存
# REDIS_DATASOURCES_0_READONLY=true
```

---

## 5. Registry 层

### 5.1 registry/RedisDataSourceRegistry.java

```java
public class RedisDataSourceRegistry {
    // 字段
    private final String dataSourceType = "Redis";
    private final String defaultDataSourceName;
    private final Map<String, JedisPool> pools = new LinkedHashMap<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();
    private final Map<String, Boolean> readOnlyFlags = new LinkedHashMap<>();

    // 方法
    public void register(String name, JedisPool pool, String description, boolean readOnly) { ... }
    public Jedis getConnection(String name) { ... }
    public Set<String> getDataSourceNames() { ... }
    public Map<String, String> getDataSourceMetadata() { ... }
    public boolean isReadOnly(String name) { ... }
    public String getDefaultDataSourceName() { ... }
}
```

每个注册方法创建一个 JedisPool，调用 `getConnection()` 时从池中获取 Jedis 实例。调用方用 try-with-resources 释放连接回池。

---

## 6. 服务层

### 6.1 service/RedisService.java

```java
@Service
@ConditionalOnBean(name = "redisDataSourceRegistry")
public class RedisService {

    @Qualifier("redisDataSourceRegistry")
    private final RedisDataSourceRegistry registry;

    // 构造函数注入
    public RedisService(@Qualifier("redisDataSourceRegistry") RedisDataSourceRegistry registry) { ... }

    // 基础操作
    public String get(String ds, String key)
    public String set(String ds, String key, String value)
    public Long delete(String ds, String... keys)
    public Set<String> keys(String ds, String pattern)
    public ScanResult<String> scan(String ds, String cursor, int count)
    public Boolean exists(String ds, String key)
    public Long ttl(String ds, String key)
    public Long expire(String ds, String key, long seconds)
    public String type(String ds, String key)

    // 字符串操作
    public Long incr(String ds, String key)
    public Long decr(String ds, String key)

    // Hash 操作
    public String hget(String ds, String key, String field)
    public Map<String, String> hgetAll(String ds, String key)

    // List 操作
    public List<String> lrange(String ds, String key, long start, long stop)

    // Set 操作
    public Set<String> smembers(String ds, String key)

    // 信息操作
    public String info(String ds, String section)

    // readOnly 检查
    public boolean isReadOnly(String ds) { return registry.isReadOnly(ds); }
    public Set<String> getAvailableDatasources()
    public String getDefaultDatasourceName()
    public Map<String, String> getDatasourceMetadata()
}
```

**readOnly 模式**：所有写入方法在 Tool 层调用 `isReadOnly()` 检查，返回 "SAFETY: Datasource is in read-only mode" 错误。Service 层不内置检查，由 Tool 层负责 — 与现有 ExecuteTool 模式一致。

---

## 7. 工具层（14 个 Tool）

| # | 工具名 | 类型 | readOnly 检查 |
|---|--------|------|---------------|
| 1 | `redis_get` | 查询 | 无 |
| 2 | `redis_set` | 写入 | 有 |
| 3 | `redis_delete` | 写入 | 有 |
| 4 | `redis_ttl` | 查询 | 无 |
| 5 | `redis_keys` | 查询 | 无 |
| 6 | `redis_scan` | 查询 | 无 |
| 7 | `redis_type` | 查询 | 无 |
| 8 | `redis_incr` | 写入 | 有 |
| 9 | `redis_decr` | 写入 | 有 |
| 10 | `redis_hash_get` | 查询 | 无 |
| 11 | `redis_hash_all` | 查询 | 无 |
| 12 | `redis_list_range` | 查询 | 无 |
| 13 | `redis_set_members` | 查询 | 无 |
| 14 | `redis_info` | 查询 | 无 |

每个 Tool 实现 `McpToolHandler` 接口：`getName()`, `getDescription()`, `getInputSchema()`, `execute(Map<String, Object>)`

---

## 8. 集成点

### 8.1 TurnrightMysqlMcpServerApplication.java
- 添加 `RedisProperties.class` 到 `@EnableConfigurationProperties`
- normalizeKey() 添加 `REDIS_` 前缀映射

### 8.2 mcp/McpServerCore.java
- 添加 `RedisService` 及 14 个 Redis Tool 的注入和注册

### 8.3 tool/ListDataSourcesTool.java
- 添加 `redisService` 字段（@Autowired(required = false)）
- 添加 `"redis"` 类型的处理分支

### 8.4 pom.xml
- 添加 `redis.clients:jedis:5.1.0` 依赖

---

## 9. 修改的文件

| 文件 | 操作 |
|------|------|
| `pom.xml` | 修改 — 添加 Jedis 依赖 |
| `TurnrightMysqlMcpServerApplication.java` | 修改 — 添加 RedisProperties |
| `mcp/McpServerCore.java` | 修改 — 注册 14 个 Redis Tool |
| `tool/ListDataSourcesTool.java` | 修改 — 添加 RedisService |
| `mcp-config.env` | 修改 — 添加 Redis 配置 |

### 新增文件（18 个）

```
config/
├── RedisDataSourceConfig.java
├── RedisProperties.java
└── RedisConfig.java

registry/
└── RedisDataSourceRegistry.java

service/
└── RedisService.java

tool/
├── RedisGetTool.java
├── RedisSetTool.java
├── RedisDeleteTool.java
├── RedisTtlTool.java
├── RedisKeysTool.java
├── RedisScanTool.java
├── RedisTypeTool.java
├── RedisIncrTool.java
├── RedisDecrTool.java
├── RedisHashGetTool.java
├── RedisHashAllTool.java
├── RedisListRangeTool.java
├── RedisSetMembersTool.java
└── RedisInfoTool.java
```

---

## 10. 测试

- `RedisPropertiesTest.java` — 配置绑定测试
- `RedisDataSourceRegistryTest.java` — Registry 注册和获取测试
- `RedisServiceTest.java` — 服务层测试（使用 embedded-redis 或 mock）
- 各 Tool 的单元测试 — 验证 readOnly 检查、参数验证

---

## 11. 文档更新

- `docs/RUNBOOK.md` — 添加 Redis 配置示例和端点说明
- `docs/CONTRIBUTING.md` — 添加 Redis 开发说明
- 工具数量从 25 更新到 39
