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
public class RedisTtlTool implements McpToolHandler {
    @Autowired(required = false) private final RedisService redisService;
    @Override public String getName() { return "redis_ttl"; }
    @Override public String getDescription() { return "Get the remaining time-to-live for a Redis key in seconds. Returns -1 if no expiry, -2 if key does not exist."; }
    @Override public Map<String, Object> getInputSchema() {
        return Map.of("type", "object",
            "properties", Map.of("key", Map.of("type", "string", "description", "Redis key"),
                "datasource", Map.of("type", "string", "description", "Datasource name (optional)")),
            "required", List.of("key"));
    }
    @Override public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());
        try {
            String key = (String) arguments.get("key");
            if (key == null || key.isEmpty()) return McpToolResult.error("Missing required parameter: key");
            String datasource = (String) arguments.get("datasource");
            Long ttl = redisService.ttl(datasource, key);
            return McpToolResult.success(new ObjectMapper().writeValueAsString(Map.of("key", key, "ttl_seconds", ttl)));
        } catch (Exception e) { return McpToolResult.error("redis_ttl failed: " + e.getMessage()); }
    }
}
