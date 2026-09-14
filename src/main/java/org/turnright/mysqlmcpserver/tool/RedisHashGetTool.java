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
public class RedisHashGetTool implements McpToolHandler {
    @Autowired(required = false) private final RedisService redisService;
    @Override public String getName() { return "redis_hash_get"; }
    @Override public String getDescription() { return "Get the value of a single field in a Redis hash."; }
    @Override public Map<String, Object> getInputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "key", Map.of("type", "string", "description", "Redis hash key"),
                "field", Map.of("type", "string", "description", "Hash field name"),
                "datasource", Map.of("type", "string", "description", "Datasource name (optional)")
            ),
            "required", List.of("key", "field"));
    }
    @Override public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());
        try {
            String key = (String) arguments.get("key");
            String field = (String) arguments.get("field");
            if (key == null || key.isEmpty()) return McpToolResult.error("Missing required parameter: key");
            if (field == null || field.isEmpty()) return McpToolResult.error("Missing required parameter: field");
            String datasource = (String) arguments.get("datasource");
            String value = redisService.hget(datasource, key, field);
            return McpToolResult.success(new ObjectMapper().writeValueAsString(Map.of("key", key, "field", field, "value", value)));
        } catch (Exception e) { return McpToolResult.error("redis_hash_get failed: " + e.getMessage()); }
    }
}
