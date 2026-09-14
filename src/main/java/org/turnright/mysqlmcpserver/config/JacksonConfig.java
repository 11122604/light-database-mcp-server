package org.turnright.mysqlmcpserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置
 * JDBC 驱动将 DATETIME/TIMESTAMP 列读取为 java.time.LocalDateTime，
 * 需注册 JavaTimeModule 才能正常序列化
 * @author luliang
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 支持 java.time.LocalDateTime 等 JSR-310 类型
        mapper.registerModule(new JavaTimeModule());
        // 日期时间输出为 ISO-8601 字符串（如 2026-09-04T14:10:30），而非时间戳数组
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}