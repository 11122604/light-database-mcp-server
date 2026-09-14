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
public class RedisSetMembersTool implements McpToolHandler {
    @Autowired(required = false) private final RedisService redisService;
    @Override public String getName() { return "redis_set_members"; }
    @Override public String getDescription() { return "Get all members of a Redis set."; }
    @Override public Map<String, Object> getInputSchema() {
        return Map.of("type", "object",
            "properties", Map.of("key", Map.of("type", "string", "description", "Redis set key"),
                "datasource", Map.of("type", "string", "description", "Datasource name (optional)")),
            "required", List.of("key"));
    }
    @Override public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());
        try {
            String key = (String) arguments.get("key");
            if (key == null || key.isEmpty()) return McpToolResult.error("Missing required parameter: key");
            String datasource = (String) arguments.get("datasource");
            Set<String> members = redisService.smembers(datasource, key);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("key", key); result.put("count", members.size()); result.put("members", members);
            return McpToolResult.success(new ObjectMapper().writeValueAsString(result));
        } catch (Exception e) { return McpToolResult.error("redis_set_members failed: " + e.getMessage()); }
    }
}
