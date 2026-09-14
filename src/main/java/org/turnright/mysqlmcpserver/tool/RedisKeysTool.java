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
public class RedisKeysTool implements McpToolHandler {
    @Autowired(required = false) private final RedisService redisService;
    @Override public String getName() { return "redis_keys"; }
    @Override public String getDescription() { return "Find all Redis keys matching a pattern (e.g., 'user:*', 'cache:*'). Note: KEYS can be slow on large databases; prefer redis_scan for production use."; }
    @Override public Map<String, Object> getInputSchema() {
        return Map.of("type", "object",
            "properties", Map.of("pattern", Map.of("type", "string", "description", "Key pattern to match (e.g., 'user:*')"),
                "datasource", Map.of("type", "string", "description", "Datasource name (optional)")),
            "required", List.of("pattern"));
    }
    @Override public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());
        try {
            String pattern = (String) arguments.getOrDefault("pattern", "*");
            String datasource = (String) arguments.get("datasource");
            Set<String> keys = redisService.keys(datasource, pattern);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pattern", pattern); result.put("count", keys.size()); result.put("keys", keys);
            return McpToolResult.success(new ObjectMapper().writeValueAsString(result));
        } catch (Exception e) { return McpToolResult.error("redis_keys failed: " + e.getMessage()); }
    }
}
