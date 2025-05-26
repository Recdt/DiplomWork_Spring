package org.example.diplomwork.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.diplomwork.dto.esp32.ESP32InfoResponseDto;
import org.example.diplomwork.dto.esp32.ESP32ResponseDto;
import org.example.diplomwork.dto.esp32.ESP32StatusResponseDto;
import org.example.diplomwork.dto.move.MoveRequestDto;
import org.example.diplomwork.service.CommunicationService;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WebSocketCommunicationService implements CommunicationService {
    private String wsUrl = "ws://192.168.0.70:81";

    private final ObjectMapper mapper;
    private WebSocketSession session;
    private final Map<String, CompletableFuture<String>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong();
    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private volatile boolean connected = false;

    public WebSocketCommunicationService() {
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public ESP32ResponseDto sendMoveCommand(MoveRequestDto moveRequest) {
        try {
            Map<String, Object> cmd = new HashMap<>();
            cmd.put("command", "move");
            cmd.put("direction", moveRequest.direction());
            cmd.put("speed", moveRequest.speed());
            if (moveRequest.angle() != null) {
                cmd.put("angle", moveRequest.angle());
            }

            return sendCommand(cmd, ESP32ResponseDto.class);
        } catch (Exception e) {
            ESP32ResponseDto errorResponse = new ESP32ResponseDto();
            errorResponse.setStatus("error");
            errorResponse.setMessage("WebSocket error: " + e.getMessage());
            return errorResponse;
        }
    }

    @Override
    public ESP32ResponseDto sendStopCommand() {
        try {
            Map<String, Object> cmd = new HashMap<>();
            cmd.put("command", "stop");
            return sendCommand(cmd, ESP32ResponseDto.class);
        } catch (Exception e) {
            ESP32ResponseDto errorResponse = new ESP32ResponseDto();
            errorResponse.setStatus("error");
            errorResponse.setMessage("WebSocket error: " + e.getMessage());
            return errorResponse;
        }
    }

    @Override
    public ESP32StatusResponseDto getStatus() {
        try {
            Map<String, Object> cmd = new HashMap<>();
            cmd.put("command", "status");
            return sendCommand(cmd, ESP32StatusResponseDto.class);
        } catch (Exception e) {
            ESP32StatusResponseDto errorResponse = new ESP32StatusResponseDto();
            errorResponse.setStatus("error");
            return errorResponse;
        }
    }

    @Override
    public ESP32InfoResponseDto getInfo() {
        try {
            Map<String, Object> cmd = new HashMap<>();
            cmd.put("command", "info");
            return sendCommand(cmd, ESP32InfoResponseDto.class);
        } catch (Exception e) {
            ESP32InfoResponseDto errorResponse = new ESP32InfoResponseDto();
            errorResponse.setStatus("error");
            return errorResponse;
        }
    }

    @Override
    public void connect() {
        if (isConnected()) return;

        try {
            MyWebSocketHandler handler = new MyWebSocketHandler();

            CompletableFuture<WebSocketSession> future = client.execute(handler, String.valueOf(new URI(wsUrl)));
            this.session = future.get(5, TimeUnit.SECONDS);
            this.connected = true;

            System.out.println("WebSocket connected to: " + wsUrl);

        } catch (Exception e) {
            this.connected = false;
            throw new RuntimeException("WebSocket connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
                System.out.println("WebSocket disconnected");
            } catch (Exception e) {
                System.err.println("Error closing WebSocket: " + e.getMessage());
            }
        }
        connected = false;
        pendingRequests.clear();
    }

    @Override
    public boolean isConnected() {
        return connected && session != null && session.isOpen();
    }

    public <T> T sendCommand(Map<String, Object> command, Class<T> responseType) throws Exception {
        if (!isConnected()) {
            connect();
        }

        String id = String.valueOf(idCounter.incrementAndGet());
        command.put("id", id);

        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        try {
            String json = mapper.writeValueAsString(command);
            session.sendMessage(new TextMessage(json));
            System.out.println("WebSocket sent: " + json);

            String response = future.get(5, TimeUnit.SECONDS); //TODO test maybe rewrite
            return mapper.readValue(response, responseType);

        } catch (TimeoutException e) {
            pendingRequests.remove(id);
            throw new RuntimeException("WebSocket request timeout for command: " + command.get("command"));
        } catch (Exception e) {
            pendingRequests.remove(id);
            throw new RuntimeException("WebSocket command failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleResponse(String json) {
        try {
            Map<String, Object> response = mapper.readValue(json, Map.class);
            Object idObj = response.get("id");

            if (idObj != null) {
                String id = String.valueOf(idObj);
                CompletableFuture<String> future = pendingRequests.remove(id);
                if (future != null) {
                    future.complete(json);
                } else {
                    System.err.println("No pending request found for ID: " + id);
                }
            } else {
                System.err.println("Received response without ID: " + json);
            }
        } catch (Exception e) {
            System.err.println("Error handling response: " + e.getMessage());
        }
    }

    private class MyWebSocketHandler implements WebSocketHandler {
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            System.out.println("WebSocket connection established");
        }

        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
            if (message instanceof TextMessage) {
                handleResponse(((TextMessage) message).getPayload());
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            System.err.println("WebSocket transport error: " + exception.getMessage());
            connected = false;
            for (CompletableFuture<String> future : pendingRequests.values()) {
                future.completeExceptionally(exception);
            }
            pendingRequests.clear();
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            System.out.println("WebSocket connection closed: " + status);
            connected = false;

            for (CompletableFuture<String> future : pendingRequests.values()) {
                future.completeExceptionally(new RuntimeException("WebSocket connection closed"));
            }
            pendingRequests.clear();
        }

        @Override
        public boolean supportsPartialMessages() {
            return false;
        }
    }
}
