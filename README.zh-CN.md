# Turnright Database MCP Server

[English](README.md) | **中文**

多数据库 MCP 服务器，向 AI 工具（Claude Code 等）暴露统一的数据库操作能力。支持 MySQL、SQL Server、MongoDB、Elasticsearch、Oracle、Redis，每种数据库均可配置**多个数据源**。
<img width="1384" height="506" alt="企业微信截图_17893718801180" src="https://github.com/user-attachments/assets/5c0f30d9-c43f-46f0-b513-a867c9509ad4" />


## 快速开始

### 1. 构建

```bash
./mvnw clean package -DskipTests
```

产物：`target/turnright-database-mcp-server-0.0.1-SNAPSHOT.jar`

### 2. 配置

复制模板并填写数据库连接：

```bash
cp .env.example mcp-config.env
```

配置统一使用**多数据源格式**（`<DB>_ENABLED` + `<DB>_DATASOURCES_N_*`），单数据源写法（`DATABASE_URL` 等）已移除。字段说明见 [USAGE.md](USAGE.md#环境变量配置)。

### 3. 启动

```bash
./start.sh                  # 传输模式与端口跟随 mcp-config.env
./start.sh http             # 强制 HTTP
./start.sh stdio            # 强制 stdio
./start.sh 9001             # 端口覆盖为 9001，模式不强制
./start.sh http 9000 ./my.env   # 指定模式、端口与配置文件
```

Windows 使用 `start.bat [http|stdio] [端口]`，参数规则相同。

启动脚本按优先级查找 JAR：先 `target/turnright-database-mcp-server-0.0.1-SNAPSHOT.jar`，再脚本同目录的部署 JAR。

## 传输模式

| 模式 | 说明 |
|------|------|
| `stdio` | MCP 标准输入输出，供 Claude Code / Claude Desktop 本地拉起，无 HTTP 端口 |
| `http` | 启动 Web 服务，同时提供 Streamable HTTP（推荐）与旧版 SSE 端点 |

**优先级**（高 → 低）：

```
-p/--port 命令行参数
  > System property（-Dmcp.transport / -Dmcp.port）
  > OS 环境变量（MCP_TRANSPORT / MCP_PORT）
  > mcp-config.env 文件
  > 默认值（stdio / 8080）
```

> 注意：`start.sh` / `start.bat` 会把 `mcp-config.env` 的键导出为环境变量，会**覆盖**外部已导出的同名变量。因此用启动脚本时，配置文件的值优先于 shell 里预先 `export` 的值；要临时切换模式，请用显式命令行参数。

## Claude Code 配置

**stdio 模式**（由 Claude Code 拉起进程）：

```json
{
  "mcpServers": {
    "turnright-database": {
      "command": "java",
      "args": ["-jar", "path/to/turnright-database-mcp-server-0.0.1-SNAPSHOT.jar"]
    }
  }
}
```

**HTTP 模式**：

```json
{
  "mcpServers": {
    "turnright-database": {
      "url": "http://localhost:9000/mcp"
    }
  }
}
```

> 端口以 `mcp-config.env` 的 `MCP_PORT` 为准（程序默认 8080，仓库示例配置为 9000）。旧版 SSE 客户端可改用 `http://localhost:9000/mcp/sse`（legacy，仍兼容）。

> 数据库连接（含凭证）统一放在 `mcp-config.env`，或使用完整属性名的环境变量（如 `DATABASE_MYSQL_DATASOURCES_0_URL`）。不要在 MCP 的 `env` 里传 `DATABASE_URL` 等旧单源变量。

## 支持的数据库

| 数据库 | 工具数 | 多数据源 |
|--------|--------|---------|
| MySQL | 5 | ✅ |
| SQL Server | 6 | ✅ |
| MongoDB | 7 | ✅ |
| Elasticsearch | 9 | ✅ |
| Oracle | 6 | ✅ |
| Redis | 14 | ✅ |

## MCP 工具

共 48 个工具（含通用的 `list_datasources`）。工具通过 Spring 条件装配**按启用的数据源动态注册**：某库 `*_ENABLED=false` 时，其工具不会出现在 `tools/list` 中。

> **例外**：6 个 Oracle 工具始终注册（未启用时调用会返回"未配置"错误），这是当前实现的已知差异。

- **通用**：`list_datasources`
- **MySQL**：`mysql_query`、`mysql_execute`、`mysql_table_schema`、`mysql_list_tables`、`mysql_database_info`
- **SQL Server**：`sqlserver_query`、`sqlserver_execute`、`sqlserver_table_schema`、`sqlserver_list_tables`、`sqlserver_database_info`、`sqlserver_stored_procedure`
- **MongoDB**：`mongo_find`、`mongo_insert`、`mongo_update`、`mongo_delete`、`mongo_aggregate`、`mongo_list_collections`、`mongo_database_info`
- **Elasticsearch**：`es_search`、`es_get_document`、`es_index_document`、`es_update_document`、`es_delete_document`、`es_list_indices`、`es_index_info`、`es_create_index`、`es_cluster_info`
- **Oracle**：`oracle_query`、`oracle_execute`、`oracle_table_schema`、`oracle_list_tables`、`oracle_database_info`、`oracle_stored_procedure`
- **Redis**：`redis_get`、`redis_set`、`redis_delete`、`redis_keys`、`redis_scan`、`redis_ttl`、`redis_type`、`redis_incr`、`redis_decr`、`redis_hash_get`、`redis_hash_all`、`redis_list_range`、`redis_set_members`、`redis_info`

每个工具的**完整参数、必填项与调用示例**见 [USAGE.md](USAGE.md#mcp-工具)。

## 多数据源

除 `list_datasources` 外，所有工具都支持可选的 `datasource` 参数；不传时使用该库的默认数据源（由 `<DB>_DEFAULT_DATASOURCE` 指定，缺省取第一个）。

```json
{
  "name": "mysql_query",
  "arguments": {
    "datasource": "analytics",
    "sql": "SELECT * FROM reports LIMIT 10"
  }
}
```

## 数据源管理界面（Web UI）

HTTP 模式下访问 `http://localhost:<MCP_PORT>/admin`：

- **查看 / 测试**：列出各库数据源，支持逐库「连接测试」并实时返回延迟
- **配置修改**：新增 / 编辑 / 删除数据源，已启用类型**即时热生效**（无需重启）
- **写入保护**：修改类操作需要管理口令。设置非空 `MCP_ADMIN_TOKEN`（环境变量或 `mcp-config.env`）；未设置时页面只读，仅可查看与测试连接

> ⚠️ **保存会重写整个 `mcp-config.env`**：文件中的所有注释会丢失，且界面未暴露的字段（`MAX_POOL_SIZE`、`MIN_IDLE`、`IDLE_TIMEOUT`、`CONNECTION_TIMEOUT`、Redis `TIMEOUT`/`MAX_TOTAL` 等）**不会被写回**。若依赖这些手写参数，请先备份文件，或只用界面做临时调整。

> 未启用（`*_ENABLED=false`）的库类型：改动会保存到文件，需设为启用并重启后生效。

管理接口（供前端调用，写操作需请求头 `X-Admin-Token`）：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/admin/api/state` | 全部类型与数据源配置（密码不回显） |
| `POST` | `/admin/api/datasource` | 新增 / 更新数据源，已启用类型热生效 |
| `DELETE` | `/admin/api/datasource?type=&name=` | 删除数据源 |
| `POST` | `/admin/api/test` | 用给定字段做一次性连接测试（不注册） |

## 文档

- [USAGE.md](USAGE.md) - 使用指南：配置项、全部工具参数、调用示例
- [docs/RUNBOOK.md](docs/RUNBOOK.md) - 运维手册：部署、端点、监控、排障
- [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) - 贡献指南：开发环境、测试、项目结构
- [docs/oracle-setup.md](docs/oracle-setup.md) - Oracle JDBC 驱动安装
- [.env.example](.env.example) - 配置模板（复制为 `mcp-config.env` 使用）
- `mcp-config.env` - 本机实际配置（含凭证，已被 `.gitignore` 忽略，不入库）

## 系统要求

- Java 11+（Spring Boot 2.7.18）
- 数据库服务：MySQL 8.0+、SQL Server 2019+、MongoDB 4.4+、Elasticsearch 8.x、Oracle（需手动提供 JDBC 驱动）
