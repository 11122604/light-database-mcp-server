package org.turnright.mysqlmcpserver.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * MCP Tool Result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpToolResult {
    private List<McpContent> content;
    private Boolean isError;

    public static McpToolResult success(String text) {
        return McpToolResult.builder()
            .content(List.of(McpContent.text(text)))
            .isError(false)
            .build();
    }

    public static McpToolResult error(String message) {
        return McpToolResult.builder()
            .content(List.of(McpContent.text(message)))
            .isError(true)
            .build();
    }
}