package org.turnright.mysqlmcpserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JacksonConfig 回归测试
 * 验证生产 ObjectMapper 能序列化 JDBC 返回的 java.time 类型，
 * 防止 LocalDateTime 未注册 JavaTimeModule 导致查询工具报错
 * @author luliang
 */
class JacksonConfigTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new JacksonConfig().objectMapper();
    }

    @Test
    void objectMapper_shouldSerializeLocalDateTime() throws Exception {
        // 模拟 DatabaseService 返回的行数据（JDBC 将 DATETIME 读取为 LocalDateTime）
        Map<String, Object> row = Map.of("id", 1, "created_at", LocalDateTime.of(2026, 9, 4, 14, 10, 30));
        Map<String, Object> payload = Map.of("rows", List.of(row));

        String json = assertDoesNotThrow(() -> objectMapper.writeValueAsString(payload));

        // 输出 ISO-8601 字符串而非时间戳数组
        assertTrue(json.contains("2026-09-04T14:10:30"), "应输出 ISO 时间字符串,实际: " + json);
        assertFalse(json.contains("14,10,30"), "不应输出时间戳数组,实际: " + json);
    }

    @Test
    void objectMapper_shouldSerializeLocalDate() throws Exception {
        Map<String, Object> row = Map.of("day", LocalDate.of(2026, 9, 4));

        String json = objectMapper.writeValueAsString(row);

        assertTrue(json.contains("2026-09-04"), "应输出 ISO 日期,实际: " + json);
    }
}
