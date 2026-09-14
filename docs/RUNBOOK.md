# 运维手册

## 部署

### 构建

```bash
./mvnw clean package -DskipTests
```

产物: `target/turnright-database-mcp-server-0.0.1-SNAPSHOT.jar`

### 运行

```bash
# 启动脚本（推荐）
./start.sh http 9000          # Linux/Mac
start.bat http 9000           # Windows

# 直接运行 JAR
java -Dmcp.transport=http -jar target/turnright-database-mcp-server-0.0.1-SNAPSHOT.jar -p 9000
```

### HTTP 端点

| 端点 | 说明 | 示例 |
|------|------|------|
| `/mcp` | **Streamable HTTP**（推荐，2025-03-26+）：POST JSON-RPC，`Accept` 含 `text/event-stream` 返回 SSE 否则 JSON | `POST http://localhost:9000/mcp` |
| `/mcp` | Streamable HTTP 能力探测 | `GET http://localhost:9000/mcp` |
| `/mcp/sse` | 旧版 HTTP+SSE：SSE 连接（legacy，仍兼容） | `GET http://localhost:9000/mcp/sse` |
| `/mcp/message` | 旧版 HTTP+SSE：JSON-RPC 消息（legacy） | `POST http://localhost:9000/mcp/message?sessionId=xxx` |
| `/mcp/health` | 健康检查 | `GET http://localhost:9000/mcp/health` |

> Streamable HTTP 与旧 SSE 传输并存。协议版本按客户端请求协商：支持 `2024-11-05` 与 `2025-03-26`，服务端在 initialize 响应中回显协商结果，并通过 `Mcp-Session-Id` 响应头维持会话。

### 传输模式

stdio 不监听 HTTP 端口；http 同时暴露 Streamable HTTP（`/mcp`）与旧版 SSE（`/mcp/sse` + `/mcp/message`）。模式/端口的命令行参数、环境变量与优先级规则见 [USAGE.md 快速开始](../USAGE.md#快速开始)。

## Docker

> 仓库当前**未内置 Dockerfile**。以下命令假设你已自行编写镜像定义（例如以 `eclipse-temurin:11-jre` 为基础镜像，复制构建产物 JAR 与 `mcp-config.env`）。

```bash
# 构建
docker build -t turnright-database-mcp-server .

# HTTP 模式（推荐：挂载 mcp-config.env）
docker run -d -p 9000:9000 \
  -e MCP_TRANSPORT=http \
  -v /path/to/mcp-config.env:/app/mcp-config.env \
  turnright-database-mcp-server

# 或使用 OS 环境变量（需完整属性名，relaxed binding）
docker run -d -p 9000:9000 \
  -e MCP_TRANSPORT=http \
  -e MCP_PORT=9000 \
  -e DATABASE_MYSQL_DATASOURCES_0_URL=jdbc:mysql://host:3306/db \
  -e DATABASE_MYSQL_DATASOURCES_0_USERNAME=user \
  -e DATABASE_MYSQL_DATASOURCES_0_PASSWORD=pass \
  turnright-database-mcp-server
```

## 配置文件

`mcp-config.env` 由 Java 直接读取（无需 Shell 解析，`&`、`!` 等特殊字符可原样写入），统一使用多数据源格式。字段清单、完整示例与优先级规则见 [USAGE.md 环境变量配置](../USAGE.md#环境变量配置)。

## 环境变量参考

### MCP 传输

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MCP_TRANSPORT` | `stdio` | 传输模式（http/stdio） |
| `MCP_PORT` | `8080` | HTTP 端口 |

### 数据库

六种库均由 `<DB>_ENABLED` 总开关 + `<DB>_DATASOURCES_N_<FIELD>` 数据源数组组成（`N` 从 0 开始）。各库的可用字段、默认值与 OS 环境变量的完整属性名见 [USAGE.md 环境变量配置](../USAGE.md#环境变量配置)。

> Oracle 驱动需手动安装，详见 [oracle-setup.md](oracle-setup.md)。

## 健康检查

```bash
curl http://localhost:9000/mcp/health
```

响应:
```json
{
  "status": "ok",
  "active_sessions": 0,
  "server_name": "turnright-database-mcp-server",
  "version": "0.0.1"
}
```

## 监控

### 数据源状态

调用 `list_datasources` 工具：

```json
{
  "success": true,
  "datasources": {
    "mysql": {
      "available_datasources": ["default"],
      "default_datasource": "default",
      "datasource_descriptions": {
        "default": "MySQL业务数据库"
      }
    },
    "mongodb": {
      "available_datasources": ["default"],
      "default_datasource": "default",
      "datasource_descriptions": {
        "default": "MongoDB业务数据库"
      }
    },
    "oracle": {
      "status": "not_configured"
    }
  }
}
```

> 未启用的库返回 `{"status": "not_configured"}`；全部未配置时响应额外包含 `hint` 字段。

### 可用工具

工具按数据源启用状态动态注册，运行时可用 `list_datasources` 实时查看。完整清单见 [README 的 MCP 工具](../README.md#mcp-工具)，参数说明见 [USAGE.md 工具参数表](../USAGE.md#mcp-工具)。

## 日志

### 级别配置

```bash
# 调试模式
java -Dlogging.level.org.turnright=DEBUG -jar app.jar
```

### 关键日志

| 日志 | 级别 | 说明 |
|------|------|------|
| `MCP Server initialized (HTTP mode)` | INFO | HTTP 模式启动 |
| `Port set to: 9000` | INFO | 端口解析成功（仅当解析出端口时打印） |
| `Registered MySQL datasource: default (...)` | INFO | 数据源注册成功（日志前缀随库名不同） |
| 工具返回 `SAFETY: Datasource 'xxx' is in read-only mode...` | — | 在只读数据源上调用写入工具被拒绝（返回给客户端的错误文本，非服务端日志） |

## 常见问题

### 问题：启动时显示 stdio 模式但配置为 http

**原因**: 配置文件属性未正确映射

**解决**: 确保使用最新版本，`mcp-config.env` 中的 `MCP_TRANSPORT=http` 会被自动映射到 Spring Boot 属性

### 问题：JDBC URL 被截断

**症状**: URL 中 `&` 后的内容丢失

**解决**: Java 直接读取配置文件，无需 Shell 解析。确保使用 `mcp-config.env` 文件。

### 问题：密码中的 `!` 符号丢失

**解决**: 使用 `mcp-config.env` 配置文件，Java 直接读取

### 问题：端口不生效

**解决**: 
- 命令行: `./start.sh http 9000`
- 配置文件: `MCP_PORT=9000`

### 问题：健康检查返回 404

**原因**: stdio 模式下无 HTTP 端点

**解决**: 使用 HTTP 模式启动

### 问题：数据库连接失败

检查:
1. JDBC URL 格式
2. 网络连通性
3. 用户权限（至少 SELECT）
4. SSL/TLS 设置

### 问题：写入操作被拒绝

**症状**: `SAFETY: Datasource is in read-only mode`

**解决**: 将对应数据源设为 `DATABASE_DATASOURCES_0_READONLY=false`（谨慎），或改配其他可写数据源

### 问题：Oracle 驱动缺失

详见 [oracle-setup.md](oracle-setup.md)

## 回滚

```bash
# 停止服务
taskkill /F /IM java.exe      # Windows（cmd / PowerShell；Git Bash 下需写成 //F）
kill <pid>                    # Linux

# 恢复旧版本
java -jar turnright-database-mcp-server-old.jar
```

## 安全建议

1. **生产环境保持 readOnly=true**
2. 数据库用户仅授予必要权限（SELECT）
3. 使用 SSL 连接
4. 定期审计 `list_datasources` 输出
5. 监控异常写入请求