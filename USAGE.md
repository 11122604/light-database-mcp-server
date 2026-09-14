# Turnright Database MCP Server 使用指南

本文档覆盖配置项、启动方式、全部 MCP 工具的参数与调用示例。部署与排障见 [docs/RUNBOOK.md](docs/RUNBOOK.md)。

## 快速开始

### 1. 配置数据库

复制模板并编辑 `mcp-config.env`（统一使用**多数据源格式**，单数据源写法已移除）：

```bash
cp .env.example mcp-config.env
```

```bash
# 启用 + 第一个数据源（单源也需显式 NAME，建议命名 default）
MYSQL_ENABLED=true
MYSQL_DATASOURCES_0_NAME=default
MYSQL_DATASOURCES_0_URL=jdbc:mysql://localhost:3306/mydb
MYSQL_DATASOURCES_0_USERNAME=root
MYSQL_DATASOURCES_0_PASSWORD=password
MYSQL_DATASOURCES_0_DESCRIPTION=MySQL业务数据库
MYSQL_DATASOURCES_0_READONLY=true
```

### 2. 启动服务

```bash
./start.sh                       # 传输模式与端口跟随 mcp-config.env
./start.sh http                  # 强制 HTTP
./start.sh stdio                 # 强制 stdio
./start.sh 9001                  # 端口覆盖为 9001，模式跟随配置
./start.sh http 9000 ./my.env    # 指定模式、端口、配置文件
```

Windows 使用 `start.bat [http|stdio] [端口]`，参数规则相同。

**优先级规则**（高 → 低）：

```
-p/--port 命令行参数
  > System property（-Dmcp.transport / -Dmcp.port）
  > OS 环境变量（MCP_TRANSPORT / MCP_PORT）
  > mcp-config.env 文件
  > 默认值（stdio / 8080）
```

**启动脚本的实际效果**：

| 场景 | mcp-config.env 中 `MCP_TRANSPORT` | 命令行 | 最终值 |
|------|-----------------------------------|--------|--------|
| `./start.sh http` | stdio | http | **http** |
| `./start.sh http` | http | http | **http** |
| `./start.sh stdio` | http | stdio | **stdio** |
| `./start.sh`（无参数） | http | 不指定 | **http** |
| `./start.sh`（无参数） | stdio | 不指定 | **stdio** |
| `./start.sh`（无参数，无配置文件） | — | 不指定 | **stdio**（程序默认） |

> 关键点：`start.sh` / `start.bat` 会把 `mcp-config.env` 的键**导出为环境变量**，且会覆盖外部已导出的同名变量。所以用启动脚本时，配置文件的值优先于 shell 里预先 `export` 的值；要临时切换模式，请用显式命令行参数（如 `./start.sh stdio`）。

端口同理：`-p`/数字参数 > `-Dmcp.port` > `MCP_PORT` 环境变量 > 配置文件 `MCP_PORT` > 默认 8080。注意程序会同步设置 `server.port`，Spring 的 `application.yaml` 占位符不会被绕过。

**部署目录结构**（打包后分发/部署时）：

```
deploy/
├── turnright-database-mcp-server-0.0.1-SNAPSHOT.jar   # mvnw clean package 产物
├── mcp-config.env                                     # 数据库连接与传输配置
├── start.sh                                           # Linux / macOS 启动脚本
└── start.bat                                          # Windows 启动脚本
```

> jar、`mcp-config.env`、`start.sh`、`start.bat` 必须放在**同一目录**下：
> - 程序按当前工作目录（`user.dir`）查找 `mcp-config.env`；
> - 两个启动脚本在自身所在目录查找 jar，并先切到该目录再启动 Java，所以从任意位置调用脚本都能读到同目录的配置。
>
> 分开存放会导致配置读不到，服务以"无任何数据源"启动（`list_datasources` 只返回提示信息）。

### 3. 接入 Claude Code

Stdio 与 HTTP 两种接入方式的完整 JSON 配置见 [README.zh-CN.md 的 Claude Code 配置](README.zh-CN.md#claude-code-配置)。要点：数据库连接统一放 `mcp-config.env`，不要在 MCP 的 `env` 里传 `DATABASE_URL` 等旧单源变量（已移除）。

---

## 支持的数据库

| 数据库 | 工具数 | 多数据源 |
|--------|--------|---------|
| MySQL | 5 | ✅ |
| SQL Server | 6 | ✅ |
| MongoDB | 7 | ✅ |
| Elasticsearch | 9 | ✅ |
| Oracle | 6 | ✅ |
| Redis | 14 | ✅ |

> 工具按启用的数据源动态注册，未启用（`*_ENABLED=false`）的库不会出现在 `tools/list` 中。**例外**：6 个 Oracle 工具始终注册，未启用时调用会返回"未配置"错误。

---

## 环境变量配置

数据库统一使用**多数据源格式**，由总开关 + 数据源数组组成：

```bash
<DB>_ENABLED=true                     # 启用该类型
<DB>_DATASOURCES_0_NAME=default       # 第 0 个数据源名（单源建议 default）
<DB>_DATASOURCES_0_<FIELD>=...        # 连接字段（见下表）
<DB>_DEFAULT_DATASOURCE=primary       # 可选，指定默认；缺省取第一个
```

| 库 | 数据源前缀 | 可用字段 |
|----|-----------|---------|
| MySQL | `MYSQL_DATASOURCES_` | NAME, URL, USERNAME, PASSWORD, DESCRIPTION, READONLY, MAX_POOL_SIZE, MIN_IDLE, IDLE_TIMEOUT, CONNECTION_TIMEOUT |
| SQL Server | `SQLSERVER_DATASOURCES_` | NAME, URL, USERNAME, PASSWORD, DESCRIPTION, READONLY, MAX_POOL_SIZE, MIN_IDLE, IDLE_TIMEOUT, CONNECTION_TIMEOUT |
| MongoDB | `MONGODB_DATASOURCES_` | NAME, URI, DATABASE, HOST, PORT, USERNAME, PASSWORD, DESCRIPTION, READONLY, MAX_POOL_SIZE, MIN_POOL_SIZE, MAX_IDLE_TIME_MS, MAX_CONNECTION_LIFE_TIME_MS |
| Elasticsearch | `ES_DATASOURCES_` | NAME, HOST, PORT, USERNAME, PASSWORD, API_KEY, SSL, FINGERPRINT, DESCRIPTION, READONLY |
| Oracle | `ORACLE_DATASOURCES_` | NAME, URL, USERNAME, PASSWORD, DESCRIPTION, READONLY, MAX_POOL_SIZE, MIN_IDLE, IDLE_TIMEOUT, CONNECTION_TIMEOUT |
| Redis | `REDIS_DATASOURCES_` | NAME, HOST, PORT, PASSWORD, TIMEOUT, MAX_TOTAL, MAX_IDLE, MIN_IDLE, DESCRIPTION, READONLY |

- `READONLY` 默认 `true` = 只读；`false` = 允许写入。写入类工具在只读数据源上会被拒绝。
- 数值字段（连接池 / 超时）缺省使用默认值：JDBC 连接池 10/2（最大/最小空闲）、空闲 300000ms、连接 30000ms；MongoDB 100/10、空闲 60000ms、生命周期 300000ms；Redis 超时 2000ms、池 8/8/0。
- 各库统一使用 `<DB>_DATASOURCES_N_<FIELD>` 前缀（MySQL 即 `MYSQL_DATASOURCES_N_<FIELD>`）。

### 两种变量形式的区别（易错）

| 使用方式 | 启用键 | 数据源字段 |
|---------|--------|-----------|
| `mcp-config.env` 文件 | `MYSQL_ENABLED` | `MYSQL_DATASOURCES_0_URL` |
| OS 环境变量（Docker `-e` / `export`） | `DATABASE_MYSQL_ENABLED` | `DATABASE_MYSQL_DATASOURCES_0_URL` |

> OS 环境变量不经过程序的文件解析，直接由 Spring 的宽松绑定（relaxed binding）映射到完整属性名；而 `mcp-config.env` 文件的键由程序自身归一化，请使用简写形式（`MYSQL_ENABLED`、`MYSQL_DATASOURCES_0_URL`），不要把 OS 环境变量的完整属性名写进文件。

OS 环境变量的完整属性名前缀：

| 库 | 完整前缀 | 启用键示例 |
|----|---------|-----------|
| MySQL | `DATABASE_MYSQL_` | `DATABASE_MYSQL_ENABLED=true` |
| SQL Server | `DATABASE_SQLSERVER_` | `DATABASE_SQLSERVER_ENABLED=true` |
| MongoDB | `DATABASE_MONGODB_` | `DATABASE_MONGODB_ENABLED=true` |
| Elasticsearch | `DATABASE_ELASTICSEARCH_` | `DATABASE_ELASTICSEARCH_ENABLED=true` |
| Oracle | `DATABASE_ORACLE_` | `DATABASE_ORACLE_ENABLED=true` |
| Redis | `DATABASE_REDIS_` | `DATABASE_REDIS_ENABLED=true` |

> Oracle 的 JDBC URL 格式（SID / Service Name / TNS）、驱动安装与常见连接问题见 [oracle-setup.md](docs/oracle-setup.md)。仓库 `lib/ojdbc11.jar` 当前为占位文件（非有效驱动），运行 Oracle 前必须替换为官方 jar。

---

## 多数据源配置

仅支持**多数据源（datasources）**一种写法，单数据源配置已移除。

- 只配一个数据源：命名 `default`，工具不带 `datasource` 参数即默认使用它
- 配多个数据源：用 `<DB>_DEFAULT_DATASOURCE` 指定默认，缺省取第一个

### YAML 方式

`application.yaml` 同样支持（与 `mcp-config.env` / 环境变量等效）：

```yaml
database:
  mysql:
    enabled: true
    default-name: primary
    datasources:
      - name: primary
        url: jdbc:mysql://prod:3306/main
        username: user
        password: pass
      - name: analytics
        url: jdbc:mysql://analytics:3306/logs
        username: user
        password: pass
```

### 环境变量方式

**MySQL 多数据源：**
```bash
MYSQL_ENABLED=true
MYSQL_DATASOURCES_0_NAME=primary
MYSQL_DATASOURCES_0_URL=jdbc:mysql://prod:3306/main
MYSQL_DATASOURCES_0_USERNAME=user
MYSQL_DATASOURCES_0_PASSWORD=pass

MYSQL_DATASOURCES_1_NAME=analytics
MYSQL_DATASOURCES_1_URL=jdbc:mysql://analytics:3306/logs
MYSQL_DATASOURCES_1_USERNAME=user
MYSQL_DATASOURCES_1_PASSWORD=pass

MYSQL_DEFAULT_DATASOURCE=primary
```

**SQL Server 多数据源：**
```bash
SQLSERVER_ENABLED=true

SQLSERVER_DATASOURCES_0_NAME=primary
SQLSERVER_DATASOURCES_0_URL=jdbc:sqlserver://prod:1433;databaseName=main;encrypt=false
SQLSERVER_DATASOURCES_0_USERNAME=sa
SQLSERVER_DATASOURCES_0_PASSWORD=pass

SQLSERVER_DATASOURCES_1_NAME=reporting
SQLSERVER_DATASOURCES_1_URL=jdbc:sqlserver://reporting:1433;databaseName=reports;encrypt=false
SQLSERVER_DATASOURCES_1_USERNAME=sa
SQLSERVER_DATASOURCES_1_PASSWORD=pass

SQLSERVER_DEFAULT_DATASOURCE=primary
```

**MongoDB 多数据源：**
```bash
MONGODB_ENABLED=true

MONGODB_DATASOURCES_0_NAME=primary
MONGODB_DATASOURCES_0_URI=mongodb://prod:27017
MONGODB_DATASOURCES_0_DATABASE=main_db

MONGODB_DATASOURCES_1_NAME=logs
MONGODB_DATASOURCES_1_URI=mongodb://logs:27017
MONGODB_DATASOURCES_1_DATABASE=logs_db

MONGODB_DEFAULT_DATASOURCE=primary
```

**Elasticsearch 多数据源：**
```bash
ES_ENABLED=true

ES_DATASOURCES_0_NAME=primary
ES_DATASOURCES_0_HOST=es-prod
ES_DATASOURCES_0_PORT=9200
ES_DATASOURCES_0_USERNAME=elastic
ES_DATASOURCES_0_PASSWORD=pass

ES_DATASOURCES_1_NAME=archive
ES_DATASOURCES_1_HOST=es-archive
ES_DATASOURCES_1_PORT=9200
ES_DATASOURCES_1_USERNAME=elastic
ES_DATASOURCES_1_PASSWORD=pass

ES_DEFAULT_DATASOURCE=primary
```

**Oracle 多数据源：**
```bash
ORACLE_ENABLED=true

ORACLE_DATASOURCES_0_NAME=primary
ORACLE_DATASOURCES_0_URL=jdbc:oracle:thin:@prod:1521:PROD
ORACLE_DATASOURCES_0_USERNAME=system
ORACLE_DATASOURCES_0_PASSWORD=pass

ORACLE_DATASOURCES_1_NAME=reporting
ORACLE_DATASOURCES_1_URL=jdbc:oracle:thin:@reporting:1521:REPORT
ORACLE_DATASOURCES_1_USERNAME=report_user
ORACLE_DATASOURCES_1_PASSWORD=pass

ORACLE_DEFAULT_DATASOURCE=primary
```

**Redis 多数据源：**
```bash
REDIS_ENABLED=true

REDIS_DATASOURCES_0_NAME=primary
REDIS_DATASOURCES_0_HOST=redis-prod
REDIS_DATASOURCES_0_PORT=6379
REDIS_DATASOURCES_0_DESCRIPTION=主缓存
REDIS_DATASOURCES_0_READONLY=true

REDIS_DATASOURCES_1_NAME=session
REDIS_DATASOURCES_1_HOST=redis-session
REDIS_DATASOURCES_1_PORT=6379
REDIS_DATASOURCES_1_READONLY=false

REDIS_DEFAULT_DATASOURCE=primary
```

---

## MCP 工具

### 通用参数

除 `list_datasources` 外，**所有工具**都支持可选的 `datasource` 参数，用于指定数据源名称；不传时使用该库的默认数据源。

### 数据源管理

| 工具 | 必填参数 | 可选参数 | 说明 |
|------|---------|---------|------|
| `list_datasources` | — | `database_type` | 列出各库数据源。`database_type` 可选 `mysql`/`sqlserver`/`mongodb`/`elasticsearch`/`oracle`/`redis`/`all`（默认 `all`） |

### MySQL

| 工具 | 必填参数 | 可选参数 | 说明 |
|------|---------|---------|------|
| `mysql_query` | `sql` | `datasource` | 执行查询，仅允许 `SELECT`/`SHOW`/`DESCRIBE`/`EXPLAIN` |
| `mysql_execute` | `sql` | `datasource` | 执行写入，拒绝 `SELECT`；只读数据源会被拒绝 |
| `mysql_table_schema` | `table_name` | `include_indexes`, `include_primary_key`, `datasource` | 表结构 |
| `mysql_list_tables` | — | `datasource` | 表列表 |
| `mysql_database_info` | — | `datasource` | 数据库信息 |

### SQL Server

| 工具 | 必填参数 | 可选参数 | 说明 |
|------|---------|---------|------|
| `sqlserver_query` | `sql` | `datasource` | 执行查询 |
| `sqlserver_execute` | `sql` | `datasource` | 执行写入；只读数据源会被拒绝 |
| `sqlserver_table_schema` | `table_name` | `include_indexes`, `include_primary_key`, `datasource` | 表结构 |
| `sqlserver_list_tables` | — | `include_schemas`, `datasource` | 表列表 |
| `sqlserver_database_info` | — | `datasource` | 数据库信息 |
| `sqlserver_stored_procedure` | `procedure_name` | `parameters`, `datasource` | 执行存储过程 |

### MongoDB

| 工具 | 必填参数 | 可选参数 | 说明 |
|------|---------|---------|------|
| `mongo_find` | `collection` | `filter`, `projection`, `sort`, `limit`, `database`, `datasource` | 查询文档 |
| `mongo_insert` | `collection`, `document` | `database`, `datasource` | 插入文档；只读数据源会被拒绝 |
| `mongo_update` | `collection`, `filter`, `update` | `multi`, `database`, `datasource` | 更新文档；只读数据源会被拒绝 |
| `mongo_delete` | `collection`, `filter` | `multi`, `database`, `datasource` | 删除文档；只读数据源会被拒绝 |
| `mongo_aggregate` | `collection`, `pipeline` | `database`, `datasource` | 聚合查询 |
| `mongo_list_collections` | — | `database`, `datasource` | 集合列表 |
| `mongo_database_info` | — | `database`, `collection`, `datasource` | 数据库信息 |

### Elasticsearch

| 工具 | 必填参数 | 可选参数 | 说明 |
|------|---------|---------|------|
| `es_search` | `index`, `query` | `size`, `from`, `datasource` | 搜索文档 |
| `es_get_document` | `index`, `id` | `datasource` | 获取单个文档 |
| `es_index_document` | `index`, `document` | `id`, `datasource` | 索引文档；只读数据源会被拒绝 |
| `es_update_document` | `index`, `id`, `document` | `datasource` | 更新文档；只读数据源会被拒绝 |
| `es_delete_document` | `index`, `id` | `datasource` | 删除文档；只读数据源会被拒绝 |
| `es_list_indices` | — | `datasource` | 索引列表 |
| `es_index_info` | `index` | `datasource` | 索引信息 |
| `es_create_index` | `index` | `mappings`, `settings`, `datasource` | 创建索引；只读数据源会被拒绝 |
| `es_cluster_info` | — | `include_health`, `datasource` | 集群信息 |

### Oracle

| 工具 | 必填参数 | 可选参数 | 说明 |
|------|---------|---------|------|
| `oracle_query` | `sql` | `datasource` | 执行查询，支持 `SELECT`/`DESCRIBE`/`EXPLAIN`/`WITH`(CTE) |
| `oracle_execute` | `sql` | `datasource` | 执行写入（INSERT/UPDATE/DELETE/MERGE/DDL）；只读数据源会被拒绝 |
| `oracle_table_schema` | `table_name` | `include_indexes`, `include_primary_key`, `datasource` | 表结构，支持 `OWNER.TABLE_NAME` |
| `oracle_list_tables` | — | `owner`, `datasource` | 表列表，过滤系统表 |
| `oracle_database_info` | — | `datasource` | 数据库信息 |
| `oracle_stored_procedure` | `procedure_name` | `parameters`, `out_parameters`, `datasource` | 执行存储过程/函数，支持包调用 |

### Redis

| 工具 | 必填参数 | 可选参数 | 说明 |
|------|---------|---------|------|
| `redis_get` | `key` | `datasource` | 获取字符串值 |
| `redis_set` | `key`, `value` | `datasource` | 设置字符串值；只读数据源会被拒绝 |
| `redis_delete` | `key` | `datasource` | 删除键（逗号分隔可删多个）；只读数据源会被拒绝 |
| `redis_keys` | `pattern` | `datasource` | 按键模式列举（大库慎用，优先 `redis_scan`） |
| `redis_scan` | `cursor` | `pattern`, `count`, `datasource` | 游标增量扫描 |
| `redis_ttl` | `key` | `datasource` | 剩余过期时间（秒），-1 无过期，-2 不存在 |
| `redis_type` | `key` | `datasource` | 键类型 |
| `redis_incr` | `key` | `datasource` | 自增 1；只读数据源会被拒绝 |
| `redis_decr` | `key` | `datasource` | 自减 1；只读数据源会被拒绝 |
| `redis_hash_get` | `key`, `field` | `datasource` | 读取哈希单字段 |
| `redis_hash_all` | `key` | `datasource` | 读取哈希全部字段 |
| `redis_list_range` | `key` | `start`, `stop`, `datasource` | 列表区间（`start=0`, `stop=-1` 取全部） |
| `redis_set_members` | `key` | `datasource` | 集合成员 |
| `redis_info` | — | `section`, `datasource` | 服务信息，`section` 可选 server/clients/memory/stats/cpu/replication/keyspace |

---

## 数据源管理界面（Web UI）

HTTP 模式下访问 `http://localhost:<MCP_PORT>/admin`，支持：

- **查看 / 测试**：列出各库数据源，逐库「连接测试」并实时返回延迟与错误
- **配置修改**：新增 / 编辑 / 删除数据源（多数据源格式），已启用类型**即时热生效**（无需重启）
- **写入保护**：修改类操作需要管理口令。设置非空 `MCP_ADMIN_TOKEN`（OS 环境变量或 `mcp-config.env`）；未设置时页面为只读 + 可测试连接

> ⚠️ **保存会重写整个 `mcp-config.env`**：文件中的所有注释会丢失，且界面未暴露的字段（`MAX_POOL_SIZE`、`MIN_IDLE`、`IDLE_TIMEOUT`、`CONNECTION_TIMEOUT`、Redis `TIMEOUT`/`MAX_TOTAL`/`MAX_IDLE`、Mongo 连接池参数等）**不会被写回**。若依赖这些手写参数，请先备份文件，或避免用界面保存。

> 未启用（`*_ENABLED=false`）的库类型：改动会保存到文件，需设为启用并重启后生效。

管理接口（前端调用，写操作需请求头 `X-Admin-Token` 与 `mcp.admin-token` 一致）：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/admin/api/state` | 全部类型与数据源配置（`writable` 标识是否可写；密码 / API Key 不回显明文） |
| `POST` | `/admin/api/datasource` | 新增或更新数据源（body：`type`、`fields`、可选 `default`） |
| `DELETE` | `/admin/api/datasource?type=&name=` | 删除数据源 |
| `POST` | `/admin/api/test` | 用给定字段做一次性连接测试，不注册 |

---

## 使用示例

### 指定数据源

```json
{
  "name": "mysql_query",
  "arguments": {
    "datasource": "analytics",
    "sql": "SELECT * FROM reports LIMIT 10"
  }
}
```

### 使用默认数据源

```json
{
  "name": "mysql_query",
  "arguments": {
    "sql": "SELECT * FROM users LIMIT 10"
  }
}
```

### MongoDB 查询

```json
{
  "name": "mongo_find",
  "arguments": {
    "database": "mydb",
    "collection": "users",
    "filter": {"status": "active"},
    "limit": 10
  }
}
```

### Elasticsearch 搜索

```json
{
  "name": "es_search",
  "arguments": {
    "index": "logs",
    "query": {"match": {"level": "error"}},
    "size": 20
  }
}
```

### Oracle 查询

```json
{
  "name": "oracle_query",
  "arguments": {
    "datasource": "primary",
    "sql": "SELECT * FROM USERS WHERE STATUS = 'ACTIVE'"
  }
}
```

### Oracle 存储过程

```json
{
  "name": "oracle_stored_procedure",
  "arguments": {
    "procedure_name": "GET_USER_BY_ID",
    "parameters": {"p_user_id": 123},
    "out_parameters": ["p_result"]
  }
}
```

### Redis 操作

```json
{
  "name": "redis_scan",
  "arguments": {
    "cursor": "0",
    "pattern": "user:*",
    "count": 100
  }
}
```

### 数据源发现

```json
{
  "name": "list_datasources",
  "arguments": {
    "database_type": "mysql"
  }
}
```

---

## 构建与系统要求

```bash
./mvnw clean package -DskipTests    # 产物：target/turnright-database-mcp-server-0.0.1-SNAPSHOT.jar
```

- Java 11+（Spring Boot 2.7.18）
- 数据库服务：MySQL 8.0+、SQL Server 2019+、MongoDB 4.4+、Elasticsearch 8.x、Redis 5+、Oracle（需手动提供 JDBC 驱动）
