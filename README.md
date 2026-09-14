# Turnright Database MCP Server

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-11%2B-orange.svg)](#requirements)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-6DB33F.svg)](#requirements)
[![MCP](https://img.shields.io/badge/MCP-2024--11--05%20%7C%202025--03--26-8A2BE2.svg)](#transports)
[![GitHub stars](https://img.shields.io/github/stars/11122604/light-database-mcp-server?style=social)](https://github.com/11122604/light-database-mcp-server)

**English** | [中文](README.zh-CN.md)

A multi-database [Model Context Protocol](https://modelcontextprotocol.io) server that gives AI tools — Claude Code, Claude Desktop, or any MCP-compatible client — a unified interface to **MySQL, SQL Server, MongoDB, Elasticsearch, Oracle and Redis**, with **multi-datasource** support for every engine.
![Uploading 企业微信截图_17893718801180.png…]()



## Features

- **Six engines, one protocol** — 48 MCP tools covering queries, schema inspection, writes and administration.
- **Multi-datasource** — register several instances per engine, select one per call with the `datasource` argument, or fall back to a per-engine default.
- **Read-only by default** — every datasource is read-only unless you explicitly set `READONLY=false`; write tools are rejected before touching the database.
- **Two transports** — stdio for locally-launched clients, Streamable HTTP (plus legacy SSE) for remote access.
- **Auto-registered tools** — a tool appears in `tools/list` only when its engine is enabled, so the surface adapts to your configuration.
- **Web admin UI** — browse, test and hot-reload datasources without restarting the server.

## Requirements

| Component | Version |
|-----------|---------|
| Java | 11 or newer |
| Build | Maven 3.6+ (wrapper included) — built with Spring Boot 2.7.18 |
| Databases | MySQL 8.0+, SQL Server 2019+, MongoDB 4.4+, Elasticsearch 8.x, Redis 5+, Oracle (JDBC driver must be supplied manually) |

## Quick Start

### 1. Clone and build

```bash
git clone https://github.com/11122604/light-database-mcp-server.git
cd light-database-mcp-server
./mvnw clean package -DskipTests
```

Output: `target/turnright-database-mcp-server-0.0.1-SNAPSHOT.jar`

### 2. Configure a datasource

```bash
cp .env.example mcp-config.env
```

Configuration uses the **multi-datasource format only** — one `<DB>_ENABLED` switch plus an indexed `<DB>_DATASOURCES_N_<FIELD>` array. The legacy single-datasource keys (`DATABASE_URL`, `MONGODB_URI`, …) have been removed.

```bash
MYSQL_ENABLED=true
MYSQL_DATASOURCES_0_NAME=default
MYSQL_DATASOURCES_0_URL=jdbc:mysql://localhost:3306/mydb
MYSQL_DATASOURCES_0_USERNAME=root
MYSQL_DATASOURCES_0_PASSWORD=secret
MYSQL_DATASOURCES_0_DESCRIPTION=Primary MySQL
MYSQL_DATASOURCES_0_READONLY=true
```

> `mcp-config.env` stores credentials and is listed in `.gitignore` — never commit it. Every datasource needs an explicit `NAME`; name it `default` when you only configure one.

The full field list per engine, OS-environment-variable form, and multi-datasource examples are in [USAGE.md](USAGE.md#环境变量配置).

### 3. Run

```bash
./start.sh                       # transport and port follow mcp-config.env
./start.sh http                  # force HTTP
./start.sh stdio                 # force stdio
./start.sh 9001                  # override port only
./start.sh http 9000 ./my.env    # explicit mode, port and config file
```

On Windows use `start.bat [http|stdio] [port]` with the same argument rules.

The script looks for the JAR in `target/` first, then falls back to a deployment JAR placed next to it.

### 4. Connect your MCP client

**stdio** — the client launches the process:

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

**HTTP** — connect to the Streamable HTTP endpoint:

```json
{
  "mcpServers": {
    "turnright-database": {
      "url": "http://localhost:9000/mcp"
    }
  }
}
```

> The port follows `MCP_PORT` in `mcp-config.env` (defaults to 8080; the bundled sample uses 9000). Legacy SSE clients can use `/mcp/sse` instead.

> Keep credentials in `mcp-config.env` or in fully-qualified environment variables such as `DATABASE_MYSQL_DATASOURCES_0_URL`. Do not pass the removed single-datasource variables through your MCP client's `env` block.

## Transports

| Mode | Description |
|------|-------------|
| `stdio` | Standard MCP input/output for locally-launched clients. No HTTP port is opened. |
| `http` | Starts a web server exposing Streamable HTTP (recommended) and the legacy SSE endpoints. |

HTTP endpoints:

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/mcp` | Streamable HTTP JSON-RPC endpoint; returns SSE when `Accept: text/event-stream` |
| `GET` | `/mcp` | Capability discovery |
| `GET` | `/mcp/sse` | Legacy HTTP+SSE connection |
| `POST` | `/mcp/message?sessionId=` | Legacy HTTP+SSE message endpoint |
| `GET` | `/mcp/health` | Health check |
| `GET` | `/admin` | Datasource admin UI (HTTP mode only) |

Resolution order (highest first):

```
-p/--port command-line argument
  > System property (-Dmcp.transport / -Dmcp.port)
  > OS environment variables (MCP_TRANSPORT / MCP_PORT)
  > mcp-config.env file
  > defaults (stdio / 8080)
```

> `start.sh` and `start.bat` export the keys from `mcp-config.env` as environment variables, **overwriting** any same-named variable already exported in your shell. With the launch scripts the config file therefore wins over a pre-set `export`; use an explicit command-line argument to override temporarily.

## Supported Databases

| Database | Tools | Multi-datasource |
|----------|:-----:|:----------------:|
| MySQL | 5 | ✅ |
| SQL Server | 6 | ✅ |
| MongoDB | 7 | ✅ |
| Elasticsearch | 9 | ✅ |
| Oracle | 6 | ✅ |
| Redis | 14 | ✅ |

## Available Tools

48 tools in total, including the common `list_datasources`. Tools are conditionally registered by Spring: when an engine is disabled (`<DB>_ENABLED=false`), its tools do not appear in `tools/list`.

> **Exception:** the six Oracle tools are always registered and return a "not configured" error when Oracle is disabled. This is a known deviation from the rule above.

- **Common** — `list_datasources`
- **MySQL** — `mysql_query`, `mysql_execute`, `mysql_table_schema`, `mysql_list_tables`, `mysql_database_info`
- **SQL Server** — `sqlserver_query`, `sqlserver_execute`, `sqlserver_table_schema`, `sqlserver_list_tables`, `sqlserver_database_info`, `sqlserver_stored_procedure`
- **MongoDB** — `mongo_find`, `mongo_insert`, `mongo_update`, `mongo_delete`, `mongo_aggregate`, `mongo_list_collections`, `mongo_database_info`
- **Elasticsearch** — `es_search`, `es_get_document`, `es_index_document`, `es_update_document`, `es_delete_document`, `es_list_indices`, `es_index_info`, `es_create_index`, `es_cluster_info`
- **Oracle** — `oracle_query`, `oracle_execute`, `oracle_table_schema`, `oracle_list_tables`, `oracle_database_info`, `oracle_stored_procedure`
- **Redis** — `redis_get`, `redis_set`, `redis_delete`, `redis_keys`, `redis_scan`, `redis_ttl`, `redis_type`, `redis_incr`, `redis_decr`, `redis_hash_get`, `redis_hash_all`, `redis_list_range`, `redis_set_members`, `redis_info`

Every tool accepts an optional `datasource` argument. Full parameter lists, required fields and call examples: [USAGE.md](USAGE.md#mcp-工具) (Chinese).

## Multi-Datasource Usage

Pass `datasource` to target a specific instance; omit it to use the engine's default (set by `<DB>_DEFAULT_DATASOURCE`, otherwise the first configured datasource).

```json
{
  "name": "mysql_query",
  "arguments": {
    "datasource": "analytics",
    "sql": "SELECT * FROM reports LIMIT 10"
  }
}
```

## Admin UI

Available in HTTP mode at `http://localhost:<MCP_PORT>/admin`.

- **Browse and test** — list datasources per engine and run a live connection test with latency feedback.
- **Edit** — create, update or delete datasources; changes to enabled engines take effect immediately without a restart.
- **Write protection** — modifications require an admin token. Set a non-empty `MCP_ADMIN_TOKEN` (environment variable or `mcp-config.env`); without it the page is read-only and can only test connections.

> ⚠️ **Saving rewrites the entire `mcp-config.env`.** All comments are lost, and fields the UI does not expose — `MAX_POOL_SIZE`, `MIN_IDLE`, `IDLE_TIMEOUT`, `CONNECTION_TIMEOUT`, Redis `TIMEOUT`/`MAX_TOTAL`, and similar — are **not written back**. Back up the file first if you rely on those hand-written values, or use the UI only for temporary changes.

> Changes to a disabled engine (`<DB>_ENABLED=false`) are saved to the file but require enabling the engine and restarting the server.

REST API (used by the UI; write calls require the `X-Admin-Token` header):

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/admin/api/state` | All engines and datasource configs (passwords are not echoed) |
| `POST` | `/admin/api/datasource` | Create or update a datasource; hot-applies for enabled engines |
| `DELETE` | `/admin/api/datasource?type=&name=` | Delete a datasource |
| `POST` | `/admin/api/test` | One-off connection test with the given fields (no registration) |

## Documentation

> The reference documentation below is currently written in Chinese.

- [README.zh-CN.md](README.zh-CN.md) — Chinese README
- [USAGE.md](USAGE.md) — usage guide: configuration options, all tool parameters, examples
- [docs/RUNBOOK.md](docs/RUNBOOK.md) — operations manual: deployment, endpoints, monitoring, troubleshooting
- [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) — contributing guide: dev environment, tests, project layout
- [docs/oracle-setup.md](docs/oracle-setup.md) — installing the Oracle JDBC driver
- [.env.example](.env.example) — configuration template (copy to `mcp-config.env`)

## Building and Testing

```bash
./mvnw clean package              # full build with tests
./mvnw clean package -DskipTests  # skip tests
./mvnw test                       # tests + JaCoCo coverage report
```

JaCoCo enforces a **minimum of 80% instruction coverage**; the build fails below the threshold. The report is written to `target/site/jacoco/index.html`.

## Contributing

Contributions are welcome. See [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) for the development environment, build commands, test setup, project layout, and how to add a new tool.

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, …).

## Security

- Keep `READONLY=true` on production datasources; grant database users the minimum required privileges.
- Never commit `mcp-config.env` — it is git-ignored by default, and only `.env.example` belongs in the repository.
- Set a strong `MCP_ADMIN_TOKEN` before exposing the admin UI beyond localhost.

## License

Licensed under the [Apache License 2.0](LICENSE).
