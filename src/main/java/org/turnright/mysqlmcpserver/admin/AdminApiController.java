package org.turnright.mysqlmcpserver.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 管理界面 REST API：数据源查看/增删改/连接测试。
 * 修改类操作（新增/保存/删除）需要 X-Admin-Token 与 mcp.admin-token 一致；
 * 未配置 MCP_ADMIN_TOKEN 时修改被禁用（state.writable=false），查看与测试仍可用。
 */
@Slf4j
@RestController
@ConditionalOnProperty(name = "mcp.transport", havingValue = "http")
@RequestMapping("/admin/api")
public class AdminApiController {

    @Autowired
    private AdminConfigManager manager;

    @GetMapping("/state")
    public Map<String, Object> state() {
        return manager.buildState();
    }

    @PostMapping("/datasource")
    public Map<String, Object> upsert(@RequestBody Map<String, Object> body,
                                      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireWrite(token);
        String type = String.valueOf(body.getOrDefault("type", ""));
        Map<String, Object> fields = asMap(body.get("fields"));
        if (fields.containsKey("name") && String.valueOf(fields.get("name")).trim().isEmpty()
            && body.get("name") != null) {
            fields.put("name", body.get("name"));
        }
        return manager.upsert(type, fields);
    }

    @DeleteMapping("/datasource")
    public Map<String, Object> delete(@RequestParam String type, @RequestParam String name,
                                      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireWrite(token);
        return manager.delete(type, name);
    }

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.getOrDefault("type", ""));
        return manager.test(type, asMap(body.get("fields")));
    }

    private void requireWrite(String token) {
        if (!manager.checkToken(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "需要有效的 X-Admin-Token（配置 MCP_ADMIN_TOKEN 后写入才可用）");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new java.util.LinkedHashMap<>();
    }
}
