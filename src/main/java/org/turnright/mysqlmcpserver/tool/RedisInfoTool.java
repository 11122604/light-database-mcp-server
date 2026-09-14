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
public class RedisInfoTool implements McpToolHandler {
    @Autowired(required = false) private final RedisService redisService;
    @Override public String getName() { return "redis_info"; }
    @Override public String getDescription() { return "Get Redis server information and statistics (version, memory, clients, CPU, etc.). Optional section parameter for specific info category."; }
    @Override public Map<String, Object> getInputSchema() {
        return Map.of("type", "object",
            "properties", Map.of(
                "section", Map.of("type", "string", "description", "Info section: server, clients, memory, stats, cpu, replication, keyspace (optional, returns all if not specified)"),
                "datasource", Map.of("type", "string", "description", "Datasource name (optional)")
            ));
    }
    @Override public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());
        try {
            String section = (String) arguments.get("section");
            String datasource = (String) arguments.get("datasource");
            String info = redisService.info(datasource, section);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("section", section != null ? section : "all");
            result.put("info", info);
            return McpToolResult.success(new ObjectMapper().writeValueAsString(result));
        } catch (Exception e) { return McpToolResult.error("redis_info failed: " + e.getMessage()); }
    }
}
