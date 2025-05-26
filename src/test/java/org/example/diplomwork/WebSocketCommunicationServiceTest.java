package org.example.diplomwork;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.diplomwork.dto.esp32.ESP32InfoResponseDto;
import org.example.diplomwork.dto.esp32.ESP32ResponseDto;
import org.example.diplomwork.dto.esp32.ESP32StatusResponseDto;
import org.example.diplomwork.dto.move.MoveRequestDto;
import org.example.diplomwork.entities.CommunicationProtocol;
import org.example.diplomwork.service.impl.WebSocketCommunicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketCommunicationServiceTest {

    @Mock
    private StandardWebSocketClient mockClient;

    @Mock
    private WebSocketSession mockSession;

    @Mock
    private ObjectMapper mockMapper;

    private WebSocketCommunicationService webSocketService;
    private MoveRequestDto moveRequest;

    @BeforeEach
    void setUp() throws Exception {
        webSocketService = new WebSocketCommunicationService();

        setPrivateField(webSocketService, "client", mockClient);
        setPrivateField(webSocketService, "mapper", mockMapper);
        setPrivateField(webSocketService, "session", mockSession);
        setPrivateField(webSocketService, "connected", true);

        moveRequest = new MoveRequestDto("forward", 100, null, CommunicationProtocol.WEBSOCKET);
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
        when(mockSession.isOpen()).thenReturn(true);
        when(mockMapper.writeValueAsString(any(Map.class))).thenThrow(new RuntimeException("JSON error"));

        ESP32ResponseDto result = webSocketService.sendMoveCommand(moveRequest);

        assertNotNull(result);
        assertEquals("error", result.getStatus());
        assertTrue(result.getMessage().contains("WebSocket error"));
    }

    @Test
    void sendStopCommand_WithValidRequest_ShouldReturnResponse() throws Exception {
        ESP32ResponseDto expectedResponse = new ESP32ResponseDto();
        expectedResponse.setStatus("ok");
        expectedResponse.setMessage("Stopped");

        WebSocketCommunicationService spyService = spy(webSocketService);
        doReturn(expectedResponse).when(spyService).sendCommand(any(Map.class), eq(ESP32ResponseDto.class));

        ESP32ResponseDto result = spyService.sendStopCommand();

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertEquals("Stopped", result.getMessage());
    }

    @Test
    void sendStopCommand_WithException_ShouldReturnErrorResponse() throws Exception {
        when(mockSession.isOpen()).thenReturn(true);
        when(mockMapper.writeValueAsString(any(Map.class))).thenThrow(new RuntimeException("Connection error"));

        ESP32ResponseDto result = webSocketService.sendStopCommand();

        assertNotNull(result);
        assertEquals("error", result.getStatus());
        assertTrue(result.getMessage().contains("WebSocket error"));
    }

    @Test
    void getStatus_WithValidResponse_ShouldReturnStatus() throws Exception {
        ESP32StatusResponseDto expectedResponse = new ESP32StatusResponseDto();
        expectedResponse.setCurrentSpeed(100);
        expectedResponse.setCurrentDirection("forward");
        expectedResponse.setIsMoving(true);
        expectedResponse.setStatus("ok");

        WebSocketCommunicationService spyService = spy(webSocketService);
        doReturn(expectedResponse).when(spyService).sendCommand(any(Map.class), eq(ESP32StatusResponseDto.class));

        ESP32StatusResponseDto result = spyService.getStatus();

        assertNotNull(result);
        assertEquals(100, result.getCurrentSpeed());
        assertEquals("forward", result.getCurrentDirection());
        assertTrue(result.getIsMoving());
        assertEquals("ok", result.getStatus());
    }

    @Test
    void getStatus_WithException_ShouldReturnErrorResponse() throws Exception {
        when(mockSession.isOpen()).thenReturn(true);
        when(mockMapper.writeValueAsString(any(Map.class))).thenThrow(new RuntimeException("Connection error"));

        ESP32StatusResponseDto result = webSocketService.getStatus();

        assertNotNull(result);
        assertEquals("error", result.getStatus());
    }

    @Test
    void getInfo_WithValidResponse_ShouldReturnInfo() throws Exception {
        ESP32InfoResponseDto expectedResponse = new ESP32InfoResponseDto();
        expectedResponse.setStatus("ok");
        expectedResponse.setVersion("1.0.0");

        WebSocketCommunicationService spyService = spy(webSocketService);
        doReturn(expectedResponse).when(spyService).sendCommand(any(Map.class), eq(ESP32InfoResponseDto.class));

        ESP32InfoResponseDto result = spyService.getInfo();

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertEquals("1.0.0", result.getVersion());
    }

    @Test
    void getInfo_WithException_ShouldReturnErrorResponse() throws Exception {
        when(mockSession.isOpen()).thenReturn(true);
        when(mockMapper.writeValueAsString(any(Map.class))).thenThrow(new RuntimeException("Connection error"));

        ESP32InfoResponseDto result = webSocketService.getInfo();

        assertNotNull(result);
        assertEquals("error", result.getStatus());
    }

    @Test
    void connect_WhenAlreadyConnected_ShouldNotReconnect() throws Exception {
        when(mockSession.isOpen()).thenReturn(true);
        setPrivateField(webSocketService, "connected", true);

        webSocketService.connect();

        assertTrue(webSocketService.isConnected());
        verify(mockClient, never()).execute(any(WebSocketHandler.class), anyString());
    }

    @Test
    void connect_WithException_ShouldThrowRuntimeException() throws Exception {
        setPrivateField(webSocketService, "connected", false);
        setPrivateField(webSocketService, "session", null);

        when(mockClient.execute(any(WebSocketHandler.class), anyString()))
                .thenThrow(new RuntimeException("Connection failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            webSocketService.connect();
        });

        assertTrue(exception.getMessage().contains("WebSocket connection failed"));
        assertFalse(webSocketService.isConnected());
    }

    @Test
    void disconnect_WhenConnected_ShouldCloseConnection() throws Exception {
        when(mockSession.isOpen()).thenReturn(true);

        webSocketService.disconnect();

        verify(mockSession).close();
        assertFalse(webSocketService.isConnected());
    }

    @Test
    void disconnect_WhenNotConnected_ShouldNotThrowException() throws Exception {
        setPrivateField(webSocketService, "session", null);

        assertDoesNotThrow(() -> webSocketService.disconnect());
        assertFalse(webSocketService.isConnected());
    }

    @Test
    void disconnect_WithException_ShouldNotThrowException() throws Exception {
        when(mockSession.isOpen()).thenReturn(true);
        doThrow(new RuntimeException("Close failed")).when(mockSession).close();

        assertDoesNotThrow(() -> webSocketService.disconnect());
        assertFalse(webSocketService.isConnected());
    }

    @Test
    void isConnected_WhenConnectedAndSessionOpen_ShouldReturnTrue() throws Exception {
        when(mockSession.isOpen()).thenReturn(true);
        setPrivateField(webSocketService, "connected", true);

        assertTrue(webSocketService.isConnected());
    }

    @Test
    void isConnected_WhenNotConnected_ShouldReturnFalse() throws Exception {
        setPrivateField(webSocketService, "connected", false);

        assertFalse(webSocketService.isConnected());
    }

    @Test
    void isConnected_WhenSessionIsNull_ShouldReturnFalse() throws Exception {
        setPrivateField(webSocketService, "session", null);
        setPrivateField(webSocketService, "connected", true);

        assertFalse(webSocketService.isConnected());
    }

    @Test
    void isConnected_WhenSessionClosed_ShouldReturnFalse() throws Exception {
        when(mockSession.isOpen()).thenReturn(false);
        setPrivateField(webSocketService, "connected", true);

        assertFalse(webSocketService.isConnected());
    }

    @Test
    void handleResponse_WithValidResponse_ShouldCompleteFuture() throws Exception {
        String responseJson = "{\"status\":\"ok\",\"id\":\"1\"}";

        ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests =
                getPrivateField(webSocketService, "pendingRequests");

        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put("1", future);

        when(mockMapper.readValue(eq(responseJson), eq(Map.class)))
                .thenReturn(Map.of("status", "ok", "id", "1"));

        Method handleResponseMethod = WebSocketCommunicationService.class.getDeclaredMethod("handleResponse", String.class);
        handleResponseMethod.setAccessible(true);
        handleResponseMethod.invoke(webSocketService, responseJson);

        assertTrue(future.isDone());
        assertEquals(responseJson, future.get());
        assertFalse(pendingRequests.containsKey("1"));
    }

    @Test
    void handleResponse_WithoutId_ShouldNotCompleteFuture() throws Exception {
        String responseJson = "{\"status\":\"ok\"}";

        ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests =
                getPrivateField(webSocketService, "pendingRequests");

        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put("1", future);

        when(mockMapper.readValue(eq(responseJson), eq(Map.class)))
                .thenReturn(Map.of("status", "ok"));

        Method handleResponseMethod = WebSocketCommunicationService.class.getDeclaredMethod("handleResponse", String.class);
        handleResponseMethod.setAccessible(true);
        handleResponseMethod.invoke(webSocketService, responseJson);

        assertFalse(future.isDone());
        assertTrue(pendingRequests.containsKey("1"));
    }

    private void setupWebSocketResponse(String messageId, String responseJson) throws Exception {
        ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests =
                getPrivateField(webSocketService, "pendingRequests");
        AtomicLong idCounter = getPrivateField(webSocketService, "idCounter");

        idCounter.set(Long.parseLong(messageId) - 1);

        doAnswer(invocation -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            pendingRequests.put(messageId, future);
            future.complete(responseJson);
            return null;
        }).when(mockSession).sendMessage(any(TextMessage.class));
    }
}