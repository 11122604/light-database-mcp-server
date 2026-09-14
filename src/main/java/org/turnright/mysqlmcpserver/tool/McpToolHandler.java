package org.turnright.mysqlmcpserver.tool;

import org.turnright.mysqlmcpserver.model.McpToolResult;

import java.util.Map;

/**
 * MCP Tool Handler Interface
 */
public interface McpToolHandler {
    String getName();
    String getDescription();
    Map<String, Object> getInputSchema();
    McpToolResult execute(Map<String, Object> arguments);
}