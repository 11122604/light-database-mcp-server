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

@Slf4j
@Component
@ConditionalOnBean(name = "redisService")
@RequiredArgsConstructor
public class RedisSetTool implements McpToolHandler {

    @Autowired(required = false)
    private final RedisService redisService;

    @Override
    public String getName() { return "redis_set"; }

    @Override
    public String getDescription() {
        return "Set a Redis string key to a value. This is a WRITE operation and requires readOnly=false.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "key", Map.of("type", "string", "description", "Redis key"),
                "value", Map.of("type", "string", "description", "Value to store"),
                "datasource", Map.of("type", "string", "description", "Datasource name (optional)")
            ),
            "required", List.of("key", "value")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());
        try {
            String key = (String) arguments.get("key");
            String value = (String) arguments.get("value");
            if (key == null || key.isEmpty()) return McpToolResult.error("Missing required parameter: key");
            if (value == null) return McpToolResult.error("Missing required parameter: value");
            String datasource = (String) arguments.get("datasource");
            if (redisService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource is in read-only mode. Write operations are not allowed.");
            }
            String response = redisService.set(datasource, key, value);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("key", key);
            result.put("result", response);
            return McpToolResult.success(new ObjectMapper().writeValueAsString(result));
        } catch (Exception e) {
            log.error("redis_set failed: {}", e.getMessage(), e);
            return McpToolResult.error("redis_set failed: " + e.getMessage());
        }
    }
}
