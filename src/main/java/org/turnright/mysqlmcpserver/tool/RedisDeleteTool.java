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
public class RedisDeleteTool implements McpToolHandler {
    @Autowired(required = false)
    private final RedisService redisService;

    @Override public String getName() { return "redis_delete"; }
    @Override public String getDescription() { return "Delete one or more Redis keys. WRITE operation, requires readOnly=false."; }

    @Override public Map<String, Object> getInputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "key", Map.of("type", "string", "description", "Redis key to delete (comma-separated for multiple)"),
                "datasource", Map.of("type", "string", "description", "Datasource name (optional)")
            ),
            "required", List.of("key"));
    }

    @Override public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());
        try {
            String key = (String) arguments.get("key");
            if (key == null || key.isEmpty()) return McpToolResult.error("Missing required parameter: key");
            String datasource = (String) arguments.get("datasource");
            if (redisService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource is in read-only mode. Write operations are not allowed.");
            }
            String[] keys = key.split(",");
            Long deleted = redisService.delete(datasource, keys);
            Map<String, Object> result = Map.of("deleted", deleted);
            return McpToolResult.success(new ObjectMapper().writeValueAsString(result));
        } catch (Exception e) { return McpToolResult.error("redis_delete failed: " + e.getMessage()); }
    }
}
