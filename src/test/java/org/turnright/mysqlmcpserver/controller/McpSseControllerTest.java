package org.turnright.mysqlmcpserver.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import org.turnright.mysqlmcpserver.model.JsonRpcRequest;
import org.turnright.mysqlmcpserver.model.JsonRpcResponse;
import org.turnright.mysqlmcpserver.mcp.McpServerCore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpSseControllerTest {

    @Mock
    private McpServerCore mcpServerCore;

    private McpSseController controller;
    private ObjectMapper objectMapper;
    private Map<String, Sinks.Many<ServerSentEvent<String>>> sessionEmitters;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sessionEmitters = new ConcurrentHashMap<>();
        // Create controller with mocked dependencies
        controller = new McpSseController(objectMapper, mcpServerCore);
        // Inject sessionEmitters for testing
        injectSessionEmitters(controller, sessionEmitters);
    }

    private void injectSessionEmitters(McpSseController controller, Map<String, Sinks.Many<ServerSentEvent<String>>> emitters) {
        try {
            var field = McpSseController.class.getDeclaredField("sessionEmitters");
            field.setAccessible(true);
            field.set(controller, emitters);
        } catch (Exception e) {
            // Ignore - tests may not work
        }
    }

    @Test
    void connectSse_withNullSessionId_shouldGenerateNewSessionId() {
        Flux<ServerSentEvent<String>> flux = controller.connectSse(null);

        StepVerifier.create(flux.take(2))
            .assertNext(event -> {
                assertEquals("endpoint", event.event());
                assertNotNull(event.data());
                assertTrue(event.data().contains("sessionId="));
            })
            .assertNext(event -> {
                assertEquals("server_info", event.event());
                assertNotNull(event.data());
            })
            .thenCancel()
            .verify();
    }

    @Test
    void connectSse_withProvidedSessionId_shouldUseProvidedId() {
        String sessionId = "test-session-123";

        Flux<ServerSentEvent<String>> flux = controller.connectSse(sessionId);

        StepVerifier.create(flux.take(1))
            .assertNext(event -> {
                assertEquals("endpoint", event.event());
                assertTrue(event.data().contains(sessionId));
            })
            .thenCancel()
            .verify();
    }

    @Test
    void handleMessage_withValidRequest_shouldSendResponse() throws Exception {
        // Setup: Create a session first
        String sessionId = "test-session-456";
        Sinks.Many<ServerSentEvent<String>> emitter = Sinks.many().multicast().onBackpressureBuffer();
        sessionEmitters.put(sessionId, emitter);

        // Mock response
        JsonRpcResponse mockResponse = JsonRpcResponse.builder()
            .jsonrpc("2.0")
            .id(1)
            .result(Map.of("status", "ok"))
            .build();
        when(mcpServerCore.processRequest(any())).thenReturn(mockResponse);

        // Create request
        String requestJson = objectMapper.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", 1,
            "method", "ping"
        ));

        // Execute
        controller.handleMessage(sessionId, requestJson, 100L);

        // Verify response was sent
        StepVerifier.create(emitter.asFlux().take(1))
            .assertNext(event -> {
                assertEquals("message", event.event());
                assertNotNull(event.data());
            })
            .thenCancel()
            .verify();

        verify(mcpServerCore).processRequest(any(JsonRpcRequest.class));
    }

    @Test
    void handleMessage_withUnknownSession_shouldNotProcess() throws Exception {
        String requestJson = objectMapper.writeValueAsString(Map.of(
            "jsonrpc", "2.0",
            "id", 1,
            "method", "ping"
        ));

        controller.handleMessage("unknown-session", requestJson, 100L);

        verify(mcpServerCore, never()).processRequest(any());
    }

    @Test
    void health_shouldReturnValidStatus() {
        Map<String, Object> health = controller.health();

        assertEquals("ok", health.get("status"));
        assertTrue(health.containsKey("active_sessions"));
        assertTrue(health.containsKey("server_name"));
        assertTrue(health.containsKey("version"));
    }
}