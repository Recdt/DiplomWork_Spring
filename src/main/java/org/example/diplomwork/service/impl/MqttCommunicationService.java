package org.example.diplomwork.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.example.diplomwork.dto.esp32.ESP32InfoResponseDto;
import org.example.diplomwork.dto.esp32.ESP32ResponseDto;
import org.example.diplomwork.dto.esp32.ESP32StatusResponseDto;
import org.example.diplomwork.dto.move.MoveRequestDto;
import org.example.diplomwork.service.CommunicationService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class MqttCommunicationService implements CommunicationService {
    private String mqttBroker = "tcp://broker.mqtt.cool:1883";
    private String clientId = "spring-platform-controller";
    private String commandTopic = "esp32/command";
    private String responseTopic = "esp32/response";
    private int connectionTimeout = 10;
    private int keepAlive = 20;
    private boolean autoReconnect = true;

    private final ObjectMapper objectMapper;
    private MqttClient mqttClient;
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests;
    private final AtomicLong messageIdCounter;
    private volatile boolean connected = false;

    public MqttCommunicationService() {
        this.objectMapper = new ObjectMapper();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.messageIdCounter = new AtomicLong();
    }

    @Override
    public ESP32ResponseDto sendMoveCommand(MoveRequestDto moveRequest) {
        try {
            Map<String, Object> command = new HashMap<>();
            command.put("command", "move");
            command.put("direction", moveRequest.direction());
            command.put("speed", moveRequest.speed());
            if (moveRequest.angle() != null) {
                command.put("angle", moveRequest.angle());
            }

            String response = sendAndWaitForResponse(command);
            return objectMapper.readValue(response, ESP32ResponseDto.class);

        } catch (Exception e) {
            log.error("Failed to send move command", e);
            ESP32ResponseDto errorResponse = new ESP32ResponseDto();
            errorResponse.setStatus("error");
            errorResponse.setMessage("MQTT error: " + e.getMessage());
            return errorResponse;
        }
    }

    @Override
    public ESP32ResponseDto sendStopCommand() {
        try {
            Map<String, Object> command = new HashMap<>();
            command.put("command", "stop");

            String response = sendAndWaitForResponse(command);
            return objectMapper.readValue(response, ESP32ResponseDto.class);

        } catch (Exception e) {
            log.error("Failed to send stop command", e);
            ESP32ResponseDto errorResponse = new ESP32ResponseDto();
            errorResponse.setStatus("error");
            errorResponse.setMessage("MQTT error: " + e.getMessage());
            return errorResponse;
        }
    }

    @Override
    public ESP32StatusResponseDto getStatus() {
        try {
            Map<String, Object> command = new HashMap<>();
            command.put("command", "status");

            String response = sendAndWaitForResponse(command);
            return objectMapper.readValue(response, ESP32StatusResponseDto.class);

        } catch (Exception e) {
            log.error("Failed to get status", e);
            ESP32StatusResponseDto errorResponse = new ESP32StatusResponseDto();
            errorResponse.setStatus("error");
            return errorResponse;
        }
    }

    @Override
    public ESP32InfoResponseDto getInfo() {
        try {
            Map<String, Object> command = new HashMap<>();
            command.put("command", "info");

            String response = sendAndWaitForResponse(command);
            return objectMapper.readValue(response, ESP32InfoResponseDto.class);

        } catch (Exception e) {
            log.error("Failed to get info", e);
            ESP32InfoResponseDto errorResponse = new ESP32InfoResponseDto();
            errorResponse.setStatus("error");
            return errorResponse;
        }
    }

    @Override
    public void connect() {
        if (connected && mqttClient != null && mqttClient.isConnected()) {
            log.info("MQTT already connected");
            return;
        }

        try {
            String uniqueClientId = clientId + "-" + UUID.randomUUID();
            mqttClient = new MqttClient(mqttBroker, uniqueClientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(autoReconnect);
            options.setConnectionTimeout(connectionTimeout);
            options.setKeepAliveInterval(keepAlive);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.error("MQTT connection lost", cause);
                    connected = false;
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    handleIncomingMessage(new String(message.getPayload()));
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    log.debug("MQTT message delivered");
                }
            });

            log.info("Connecting to MQTT broker: {}", mqttBroker);
            mqttClient.connect(options);

            // Subscribe to response topic
            mqttClient.subscribe(responseTopic, 1);
            connected = true;

            log.info("Successfully connected to MQTT broker and subscribed to: {}", responseTopic);

        } catch (Exception e) {
            connected = false;
            log.error("Failed to connect to MQTT broker", e);
            throw new RuntimeException("Failed to connect to MQTT broker: " + e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("Disconnected from MQTT broker");
            } catch (Exception e) {
                log.error("Error disconnecting MQTT", e);
            }
        }
        connected = false;
        // Clear pending requests
        pendingRequests.forEach((id, future) ->
                future.completeExceptionally(new RuntimeException("MQTT disconnected")));
        pendingRequests.clear();
    }

    @Override
    public boolean isConnected() {
        return connected && mqttClient != null && mqttClient.isConnected();
    }

    private String sendAndWaitForResponse(Map<String, Object> command) throws Exception {
        if (!isConnected()) {
            connect();
        }

        String messageId = String.valueOf(messageIdCounter.incrementAndGet());
        command.put("id", messageId);

        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put(messageId, future);

        try {
            String message = objectMapper.writeValueAsString(command);
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(1);
            mqttMessage.setRetained(false);

            mqttClient.publish(commandTopic, mqttMessage);
            log.debug("MQTT published to {} : {}", commandTopic, message);

            // Wait for response with timeout
            return future.get(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            pendingRequests.remove(messageId);
            throw new RuntimeException("MQTT command failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleIncomingMessage(String message) {
        try {
            log.info("MQTT received raw message: {}", message);  // Додано для debug
            Map<String, Object> response = objectMapper.readValue(message, Map.class);

            Object idObj = response.get("id");
            if (idObj != null) {
                String id = String.valueOf(idObj);
                log.info("MQTT response contains ID: {}", id);  // Додано для debug
                CompletableFuture<String> future = pendingRequests.remove(id);
                if (future != null) {
                    future.complete(message);
                    log.info("Completed request for ID: {}", id);
                } else {
                    log.warn("No pending request found for message ID: {}", id);
                }
            } else {
                log.warn("Received MQTT message without ID: {}", message);
                // Якщо немає ID, але є очікуючий запит - завершуємо його
                if (!pendingRequests.isEmpty()) {
                    String firstKey = pendingRequests.keySet().iterator().next();
                    CompletableFuture<String> future = pendingRequests.remove(firstKey);
                    if (future != null && !future.isDone()) {
                        log.warn("Completing pending request {} with response without ID", firstKey);
                        future.complete(message);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error handling incoming MQTT message", e);
        }
    }

    @PostConstruct
    public void init() {
        log.info("Initializing MQTT Communication Service with broker: {}", mqttBroker);
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up MQTT Communication Service");
        disconnect();
    }
}