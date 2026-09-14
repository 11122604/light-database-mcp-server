package org.turnright.mysqlmcpserver.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP Content Block
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpContent {
    private String type;
    private String text;

    public static McpContent text(String content) {
        return McpContent.builder()
            .type("text")
            .text(content)
            .build();
    }
}