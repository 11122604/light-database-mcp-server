#!/bin/bash
# Turnright Database MCP Server 一键启动脚本
# 用法: ./start.sh [http|stdio] [端口]
# 说明:
#   - 不带 http|stdio 参数时，传输模式与端口跟随 mcp-config.env（或 MCP_TRANSPORT/MCP_PORT 环境变量）
#   - 显式传 http 或 stdio 会覆盖 mcp-config.env
# 示例:
#   ./start.sh              # 按 mcp-config.env（若 MCP_TRANSPORT=http 则启动 HTTP）
#   ./start.sh 9001         # 传输模式按 mcp-config.env，端口覆盖为 9001
#   ./start.sh http         # 强制 HTTP
#   ./start.sh stdio        # 强制 stdio
#   ./start.sh http 9000 ./my.env

set -e

# ============================================
# 解析参数
# ============================================
MODE=""
PORT=""
CONFIG_FILE="./mcp-config.env"

while [[ $# -gt 0 ]]; do
    case "$1" in
        http|stdio)
            MODE="$1"
            shift
            ;;
        [0-9]*)
            PORT="$1"
            shift
            ;;
        *.env|.env)
            CONFIG_FILE="$1"
            shift
            ;;
        *)
            echo "未知参数: $1"
            shift
            ;;
    esac
done

# ============================================
# 加载配置文件
# ============================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -f "$SCRIPT_DIR/$CONFIG_FILE" ]]; then
    echo "加载配置文件: $CONFIG_FILE"
    while IFS='=' read -r var_name var_value; do
        [[ -z "$var_name" || "$var_name" =~ ^[[:space:]]*# ]] && continue
        var_name="${var_name//[[:space:]]/}"
        export "$var_name"="$var_value"
    done < <(grep -v '^#' "$SCRIPT_DIR/$CONFIG_FILE" | grep -v '^$' | grep '=')
else
    echo "提示: 配置文件不存在 ($CONFIG_FILE)，使用默认配置"
fi

# ============================================
# 传输模式：显式 http/stdio 参数优先；否则 mcp-config.env 已导出 MCP_TRANSPORT；再无则 stdio
# ============================================
if [[ -n "$MODE" ]]; then
    export MCP_TRANSPORT=$MODE
fi
MCP_TRANSPORT="${MCP_TRANSPORT:-stdio}"

# 端口：命令行 > mcp-config.env 的 MCP_PORT > 默认 8080
if [[ -n "$PORT" ]]; then
    export MCP_PORT=$PORT
elif [[ -z "$MCP_PORT" ]]; then
    export MCP_PORT=8080
fi

# ============================================
# 查找 JAR：优先 target/ 构建产物，否则脚本同目录的部署 jar
# ============================================
JAR_FILE="$SCRIPT_DIR/target/turnright-database-mcp-server-0.0.1-SNAPSHOT.jar"
if [[ ! -f "$JAR_FILE" ]]; then
    DEPLOY_JAR=$(ls "$SCRIPT_DIR"/turnright-database-mcp-server-*.jar 2>/dev/null | head -n 1)
    if [[ -n "$DEPLOY_JAR" ]]; then
        JAR_FILE="$DEPLOY_JAR"
        echo "使用部署 JAR: $(basename "$DEPLOY_JAR")"
    else
        echo "JAR 文件不存在，请先构建 (./mvnw clean package -DskipTests) 或将 jar 放到本目录"
        exit 1
    fi
fi

# ============================================
# 显示配置信息
# ============================================
echo "============================================"
echo "Turnright Database MCP Server 启动"
echo "============================================"
echo "传输模式: $MCP_TRANSPORT"

if [[ "$MCP_TRANSPORT" == "http" ]]; then
    echo "HTTP 端口: $MCP_PORT"
    echo "Streamable HTTP: http://localhost:$MCP_PORT/mcp  (POST, 推荐)"
    echo "SSE 端点 (legacy): http://localhost:$MCP_PORT/mcp/sse"
    echo "消息端点 (legacy): http://localhost:$MCP_PORT/mcp/message"
    echo "健康检查: http://localhost:$MCP_PORT/mcp/health"
fi

echo ""
echo "启用的数据库:"
[[ "$MYSQL_ENABLED" == "true" ]] && echo "  - MySQL"
[[ "$SQLSERVER_ENABLED" == "true" ]] && echo "  - SQL Server"
[[ "$MONGODB_ENABLED" == "true" ]] && echo "  - MongoDB"
[[ "$ES_ENABLED" == "true" ]] && echo "  - Elasticsearch"
[[ "$ORACLE_ENABLED" == "true" ]] && echo "  - Oracle"
[[ "$REDIS_ENABLED" == "true" ]] && echo "  - Redis"
echo "============================================"
echo ""

# ============================================
# 启动服务
# ============================================
echo "启动 MCP Server..."
cd "$SCRIPT_DIR"

if [[ "$MCP_TRANSPORT" == "http" ]]; then
    java -jar "$JAR_FILE" -p "$MCP_PORT"
else
    java -jar "$JAR_FILE"
fi
