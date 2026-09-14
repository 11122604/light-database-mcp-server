package org.turnright.mysqlmcpserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mcp")
public class McpTransportProperties {
    /**
     * Transport mode: 'stdio' or 'http'
     */
    private String transport = "stdio";

    /**
     * HTTP port for SSE transport (default: 8080)
     */
    private int port = 8080;

    /**
     * SSE endpoint path (default: /sse)
     */
    private String ssePath = "/sse";

    /**
     * Message endpoint path (default: /message)
     */
    private String messagePath = "/message";
}