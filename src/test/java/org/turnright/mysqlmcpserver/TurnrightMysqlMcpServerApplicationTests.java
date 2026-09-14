package org.turnright.mysqlmcpserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.turnright.mysqlmcpserver.mcp.McpServer;

@SpringBootTest
@ActiveProfiles("test")
class TurnrightMysqlMcpServerApplicationTests {

    @MockBean
    private McpServer mcpServer;

    @Test
    void contextLoads() {
        // Verify that the Spring context loads successfully
    }
}