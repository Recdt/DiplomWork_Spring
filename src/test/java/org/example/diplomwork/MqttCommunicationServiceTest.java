package org.example.diplomwork;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.example.diplomwork.dto.esp32.ESP32InfoResponseDto;
import org.example.diplomwork.dto.esp32.ESP32ResponseDto;
import org.example.diplomwork.dto.esp32.ESP32StatusResponseDto;
import org.example.diplomwork.dto.move.MoveRequestDto;
import org.example.diplomwork.entities.CommunicationProtocol;
import org.example.diplomwork.service.impl.MqttCommunicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttCommunicationServiceTest {

    @Mock
    private MqttClient mockMqttClient;

    @Mock
    private ObjectMapper mockObjectMapper;

    private MqttCommunicationService mqttCommunicationService;
    private MoveRequestDto moveRequest;

    @BeforeEach
    void setUp() throws Exception {
        mqttCommunicationService = new MqttCommunicationService();

        setPrivateField(mqttCommunicationService, "objectMapper", mockObjectMapper);
        setPrivateField(mqttCommunicationService, "mqttClient", mockMqttClient);
        setPrivateField(mqttCommunicationService, "connected", true);

        moveRequest = new MoveRequestDto("forward", 100, null, CommunicationProtocol.MQTT);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private <T> T getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    @Test
    void sendMoveCommand_WithException_ShouldReturnErrorResponse() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);
        when(mockObjectMapper.writeValueAsString(any(Map.class)))
                .thenThrow(new RuntimeException("JSON error"));

        ESP32ResponseDto result = mqttCommunicationService.sendMoveCommand(moveRequest);

        assertNotNull(result);
        assertEquals("error", result.getStatus());
        assertTrue(result.getMessage().contains("MQTT error"));
    }

    @Test
    void sendMoveCommand_WithValidRequest_ShouldReturnResponse() throws Exception {
        ESP32ResponseDto expectedResponse = new ESP32ResponseDto();
        expectedResponse.setStatus("ok");
        expectedResponse.setMessage("Moving forward");

        String responseJson = "{\"status\":\"ok\",\"message\":\"Moving forward\",\"id\":\"1\"}";

        when(mockObjectMapper.readValue(responseJson, ESP32ResponseDto.class)).thenReturn(expectedResponse);

        MqttCommunicationService spyService = spy(mqttCommunicationService);
        doReturn(responseJson).when(spyService).sendAndWaitForResponse(any(Map.class));

        ESP32ResponseDto result = spyService.sendMoveCommand(moveRequest);

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertEquals("Moving forward", result.getMessage());
    }

    @Test
    void getStatus_WithValidResponse_ShouldReturnStatus() throws Exception {
        ESP32StatusResponseDto expectedResponse = new ESP32StatusResponseDto();
        expectedResponse.setCurrentSpeed(100);
        expectedResponse.setCurrentDirection("forward");
        expectedResponse.setIsMoving(true);
        expectedResponse.setStatus("ok");

        String responseJson = "{\"currentSpeed\":100,\"currentDirection\":\"forward\",\"isMoving\":true,\"status\":\"ok\",\"id\":\"1\"}";

        when(mockObjectMapper.readValue(responseJson, ESP32StatusResponseDto.class)).thenReturn(expectedResponse);

        MqttCommunicationService spyService = spy(mqttCommunicationService);
        doReturn(responseJson).when(spyService).sendAndWaitForResponse(any(Map.class));

        ESP32StatusResponseDto result = spyService.getStatus();

        assertNotNull(result);
        assertEquals(100, result.getCurrentSpeed());
        assertEquals("forward", result.getCurrentDirection());
        assertTrue(result.getIsMoving());
        assertEquals("ok", result.getStatus());
    }

    @Test
    void getStatus_WithException_ShouldReturnErrorResponse() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);
        when(mockObjectMapper.writeValueAsString(any(Map.class)))
                .thenThrow(new RuntimeException("Connection error"));

        ESP32StatusResponseDto result = mqttCommunicationService.getStatus();

        assertNotNull(result);
        assertEquals("error", result.getStatus());
    }

    @Test
    void getInfo_WithValidResponse_ShouldReturnInfo() throws Exception {
        ESP32InfoResponseDto expectedResponse = new ESP32InfoResponseDto();
        expectedResponse.setStatus("ok");
        expectedResponse.setVersion("1.0.0");

        String responseJson = "{\"status\":\"ok\",\"version\":\"1.0.0\",\"id\":\"1\"}";

        when(mockObjectMapper.readValue(responseJson, ESP32InfoResponseDto.class)).thenReturn(expectedResponse);

        MqttCommunicationService spyService = spy(mqttCommunicationService);
        doReturn(responseJson).when(spyService).sendAndWaitForResponse(any(Map.class));

        ESP32InfoResponseDto result = spyService.getInfo();

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertEquals("1.0.0", result.getVersion());
    }

    @Test
    void getInfo_WithException_ShouldReturnErrorResponse() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);
        when(mockObjectMapper.writeValueAsString(any(Map.class)))
                .thenThrow(new RuntimeException("Connection error"));

        ESP32InfoResponseDto result = mqttCommunicationService.getInfo();

        assertNotNull(result);
        assertEquals("error", result.getStatus());
    }

    @Test
    void connect_WhenNotConnected_ShouldEstablishConnection() throws Exception {
        setPrivateField(mqttCommunicationService, "connected", false);
        setPrivateField(mqttCommunicationService, "mqttClient", null);

        try (MockedConstruction<MqttClient> mockedConstruction = mockConstruction(MqttClient.class,
                (mock, context) -> {
                    when(mock.isConnected()).thenReturn(true);
                })) {

            mqttCommunicationService.connect();

            assertTrue(mqttCommunicationService.isConnected());
            verify(mockedConstruction.constructed().get(0)).connect(any(MqttConnectOptions.class));
            verify(mockedConstruction.constructed().get(0)).subscribe(eq("esp32/response"), eq(1));
        }
    }

    @Test
    void connect_WhenAlreadyConnected_ShouldNotReconnect() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);
        setPrivateField(mqttCommunicationService, "connected", true);

        mqttCommunicationService.connect();

        assertTrue(mqttCommunicationService.isConnected());
        verify(mockMqttClient, never()).connect(any(MqttConnectOptions.class));
    }

    @Test
    void disconnect_WhenConnected_ShouldDisconnect() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);

        mqttCommunicationService.disconnect();

        verify(mockMqttClient).disconnect();
        verify(mockMqttClient).close();
        assertFalse(mqttCommunicationService.isConnected());
    }

    @Test
    void disconnect_WhenNotConnected_ShouldNotThrowException() throws Exception {
        setPrivateField(mqttCommunicationService, "mqttClient", null);

        assertDoesNotThrow(() -> mqttCommunicationService.disconnect());
    }

    @Test
    void disconnect_WithException_ShouldNotThrowException() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(mockMqttClient).disconnect();

        assertDoesNotThrow(() -> mqttCommunicationService.disconnect());
        assertFalse(mqttCommunicationService.isConnected());
    }

    @Test
    void isConnected_WhenConnected_ShouldReturnTrue() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);
        setPrivateField(mqttCommunicationService, "connected", true);

        assertTrue(mqttCommunicationService.isConnected());
    }

    @Test
    void isConnected_WhenNotConnected_ShouldReturnFalse() throws Exception {
        setPrivateField(mqttCommunicationService, "connected", false);

        assertFalse(mqttCommunicationService.isConnected());
    }

    @Test
    void isConnected_WhenMqttClientIsNull_ShouldReturnFalse() throws Exception {
        setPrivateField(mqttCommunicationService, "mqttClient", null);
        setPrivateField(mqttCommunicationService, "connected", true);

        assertFalse(mqttCommunicationService.isConnected());
    }

    @Test
    void init_ShouldLogInitialization() {
        assertDoesNotThrow(() -> mqttCommunicationService.init());
    }

    @Test
    void cleanup_ShouldDisconnect() throws Exception {
        when(mockMqttClient.isConnected()).thenReturn(true);

        mqttCommunicationService.cleanup();

        verify(mockMqttClient).disconnect();
        verify(mockMqttClient).close();
    }

    @Test
    void sendStopCommand_WithValidRequest_ShouldReturnResponse() throws Exception {
        ESP32ResponseDto expectedResponse = new ESP32ResponseDto();
        expectedResponse.setStatus("ok");
        expectedResponse.setMessage("Stopped");

        String responseJson = "{\"status\":\"ok\",\"message\":\"Stopped\",\"id\":\"1\"}";

        when(mockObjectMapper.readValue(responseJson, ESP32ResponseDto.class)).thenReturn(expectedResponse);

        MqttCommunicationService spyService = spy(mqttCommunicationService);
        doReturn(responseJson).when(spyService).sendAndWaitForResponse(any(Map.class));

        ESP32ResponseDto result = spyService.sendStopCommand();

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertEquals("Stopped", result.getMessage());
    }

    private void setupMqttResponseViaMqttCallback(String responseJson, String messageId) throws Exception {
        ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests =
                getPrivateField(mqttCommunicationService, "pendingRequests");
        AtomicLong messageIdCounter = getPrivateField(mqttCommunicationService, "messageIdCounter");
        messageIdCounter.set(Long.parseLong(messageId) - 1);

        doAnswer(invocation -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            pendingRequests.put(messageId, future);

            new Thread(() -> {
                try {
                    Thread.sleep(10);
                    simulateHandleIncomingMessage(responseJson);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }).start();

            return null;
        }).when(mockMqttClient).publish(eq("esp32/command"), any(MqttMessage.class));
    }

    private void simulateHandleIncomingMessage(String message) throws Exception {
        Method handleMethod = MqttCommunicationService.class.getDeclaredMethod("handleIncomingMessage", String.class);
        handleMethod.setAccessible(true);
        handleMethod.invoke(mqttCommunicationService, message);
    }
}