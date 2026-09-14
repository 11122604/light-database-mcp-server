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
public class RedisListRangeTool implements McpToolHandler {
    @Autowired(required = false) private final RedisService redisService;
    @Override public String getName() { return "redis_list_range"; }
    @Override public String getDescription() { return "Get a range of elements from a Redis list. Use start=0 and stop=-1 to get all elements."; }
    @Override public Map<String, Object> getInputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "key", Map.of("type", "string", "description", "Redis list key"),
                "start", Map.of("type", "integer", "description", "Start index (0-based, default 0)"),
                "stop", Map.of("type", "integer", "description", "Stop index (inclusive, -1 for last, default -1)"),
                "datasource", Map.of("type", "string", "description", "Datasource name (optional)")
            ),
            "required", List.of("key"));
    }
    @Override public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());
        try {
            String key = (String) arguments.get("key");
            if (key == null || key.isEmpty()) return McpToolResult.error("Missing required parameter: key");
            long start = arguments.containsKey("start") ? ((Number) arguments.get("start")).longValue() : 0;
            long stop = arguments.containsKey("stop") ? ((Number) arguments.get("stop")).longValue() : -1;
            String datasource = (String) arguments.get("datasource");
            List<String> elements = redisService.lrange(datasource, key, start, stop);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("key", key); result.put("count", elements.size()); result.put("elements", elements);
            return McpToolResult.success(new ObjectMapper().writeValueAsString(result));
        } catch (Exception e) { return McpToolResult.error("redis_list_range failed: " + e.getMessage()); }
    }
}
