package org.turnright.mysqlmcpserver.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.RedisService;

import java.util.*;

@Slf4j @Component
@ConditionalOnBean(name = "redisService")
@RequiredArgsConstructor
public class RedisScanTool implements McpToolHandler {
    @Autowired(required = false) private final RedisService redisService;
    @Override public String getName() { return "redis_scan"; }
    @Override public String getDescription() { return "Iteratively scan Redis keys using a cursor. Safer than redis_keys for large databases. Returns cursor and matched keys."; }
    @Override public Map<String, Object> getInputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "cursor", Map.of("type", "string", "description", "Cursor position (start with '0')"),
                "pattern", Map.of("type", "string", "description", "Optional key pattern to match"),
                "count", Map.of("type", "integer", "description", "Number of keys per iteration (default 10)"),
                "datasource", Map.of("type", "string", "description", "Datasource name (optional)")
            ),
            "required", List.of("cursor"));
    }
    @Override public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());
        try {
            String cursor = (String) arguments.getOrDefault("cursor", "0");
            int count = arguments.containsKey("count") ? ((Number) arguments.get("count")).intValue() : 10;
            String datasource = (String) arguments.get("datasource");
            Map<String, Object> scanResult = redisService.scan(datasource, cursor, count);
            scanResult.put("count", ((List<?>) scanResult.get("keys")).size());
            return McpToolResult.success(new ObjectMapper().writeValueAsString(scanResult));
        } catch (Exception e) { return McpToolResult.error("redis_scan failed: " + e.getMessage()); }
    }
}
