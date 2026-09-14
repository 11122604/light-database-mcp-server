package org.turnright.mysqlmcpserver.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 Error
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonRpcError {
    private int code;
    private String message;
    private Object data;

    public static JsonRpcError invalidRequest(String message) {
        return JsonRpcError.builder()
            .code(-32600)
            .message("Invalid Request: " + message)
            .build();
    }

    public static JsonRpcError methodNotFound(String method) {
        return JsonRpcError.builder()
            .code(-32601)
            .message("Method not found: " + method)
            .build();
    }

    public static JsonRpcError invalidParams(String message) {
        return JsonRpcError.builder()
            .code(-32602)
            .message("Invalid params: " + message)
            .build();
    }

    public static JsonRpcError internalError(String message) {
        return JsonRpcError.builder()
            .code(-32603)
            .message("Internal error: " + message)
            .build();
    }

    public static JsonRpcError toolError(String message) {
        return JsonRpcError.builder()
            .code(-32000)
            .message("Tool execution error: " + message)
            .build();
    }
}