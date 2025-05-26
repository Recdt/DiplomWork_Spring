package org.example.diplomwork;

import org.example.diplomwork.dto.esp32.ESP32InfoResponseDto;
import org.example.diplomwork.dto.esp32.ESP32ResponseDto;
import org.example.diplomwork.dto.esp32.ESP32StatusResponseDto;
import org.example.diplomwork.dto.move.MoveRequestDto;
import org.example.diplomwork.dto.platform.PlatformResponseDto;
import org.example.diplomwork.dto.platform.PlatformUpdateDto;
import org.example.diplomwork.dto.position.PositionResponseDto;
import org.example.diplomwork.entities.CommunicationProtocol;
import org.example.diplomwork.entities.MovementHistory;
import org.example.diplomwork.service.impl.HttpCommunicationService;
import org.example.diplomwork.service.impl.MqttCommunicationService;
import org.example.diplomwork.service.impl.PlatformService;
import org.example.diplomwork.service.impl.WebSocketCommunicationService;
import org.example.diplomwork.util.websocket.WebSocketBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformServiceTest {

    @Mock
    private HttpCommunicationService httpService;

    @Mock
    private WebSocketCommunicationService webSocketService;

    @Mock
    private MqttCommunicationService mqttService;

    @Mock
    private WebSocketBroadcastService wsBroadcastService;

    @InjectMocks
    private PlatformService platformService;

    private MoveRequestDto moveRequest;
    private ESP32ResponseDto successResponse;
    private ESP32ResponseDto errorResponse;

    @BeforeEach
    void setUp() {
        moveRequest = new MoveRequestDto("forward", 100, null, CommunicationProtocol.HTTP);

        successResponse = new ESP32ResponseDto();
        successResponse.setStatus("ok");
        successResponse.setMessage("Movement successful");

        errorResponse = new ESP32ResponseDto();
        errorResponse.setStatus("error");
        errorResponse.setMessage("Movement failed");
    }

    @Test
    void movePlatform_WithValidRequest_ShouldReturnSuccessResponse() {
        when(httpService.sendMoveCommand(moveRequest)).thenReturn(successResponse);

        PlatformResponseDto result = platformService.movePlatform(moveRequest);

        assertNotNull(result);
        assertEquals("success", result.getStatus());
        assertEquals("forward", result.getDirection());
        assertNotNull(result.getPosition());
        verify(httpService).sendMoveCommand(moveRequest);
        verify(wsBroadcastService).broadcastPlatformUpdate(any(PlatformUpdateDto.class));
    }

    @Test
    void movePlatform_WithNullResponse_ShouldThrowException() {
        when(httpService.sendMoveCommand(moveRequest)).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            platformService.movePlatform(moveRequest);
        });

        assertTrue(exception.getMessage().contains("ESP32 error"));
        verify(wsBroadcastService, times(2)).broadcastError(anyString());
    }

    @Test
    void movePlatform_WithCommunicationException_ShouldThrowException() {
        when(httpService.sendMoveCommand(moveRequest))
                .thenThrow(new RuntimeException("Connection failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            platformService.movePlatform(moveRequest);
        });

        assertTrue(exception.getMessage().contains("Failed to communicate with platform"));
        verify(wsBroadcastService).broadcastError(anyString());
    }

    @Test
    void stopPlatform_WithSuccessResponse_ShouldReturnSuccessResponse() {
        when(httpService.sendStopCommand()).thenReturn(successResponse);

        PlatformResponseDto result = platformService.stopPlatform();

        assertNotNull(result);
        assertNotNull(result.getPosition());
        verify(httpService).sendStopCommand();
        verify(wsBroadcastService).broadcastPlatformUpdate(any(PlatformUpdateDto.class));
    }

    @Test
    void stopPlatform_WithErrorResponse_ShouldThrowException() {
        when(httpService.sendStopCommand()).thenReturn(errorResponse);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            platformService.stopPlatform();
        });

        assertTrue(exception.getMessage().contains("ESP32 error"));
        verify(wsBroadcastService, times(2)).broadcastError(anyString());
    }

    @Test
    void getESP32Status_WithNullResponse_ShouldThrowException() {
        when(httpService.getStatus()).thenReturn(null);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            platformService.getESP32Status();
        });

        assertTrue(exception.getMessage().contains("Failed to get ESP32 status"));
        verify(wsBroadcastService).broadcastError(anyString());
    }

    @Test
    void getESP32Info_WithValidResponse_ShouldReturnInfo() {
        ESP32InfoResponseDto infoResponse = new ESP32InfoResponseDto();
        infoResponse.setStatus("ok");
        infoResponse.setVersion("1.0.0");

        when(httpService.getInfo()).thenReturn(infoResponse);

        ESP32InfoResponseDto result = platformService.getESP32Info();

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertEquals("1.0.0", result.getVersion());
    }

    @Test
    void getESP32Info_WithErrorResponse_ShouldThrowException() {
        ESP32InfoResponseDto errorInfo = new ESP32InfoResponseDto();
        errorInfo.setStatus("error");

        when(httpService.getInfo()).thenReturn(errorInfo);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            platformService.getESP32Info();
        });

        assertTrue(exception.getMessage().contains("ESP32 info error"));
    }

    @Test
    void resetPosition_ShouldResetToOrigin() {
        PlatformResponseDto result = platformService.resetPosition();

        assertNotNull(result);
        assertEquals(0.0, result.getPosition().getX());
        assertEquals(0.0, result.getPosition().getY());
        assertEquals(0.0, result.getAngle());
        assertEquals(0.0, result.getDistanceTravelled());
    }

    @Test
    void getMovementHistory_InitiallyEmpty_ShouldReturnEmptyList() {
        List<MovementHistory> history = platformService.getMovementHistory();

        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    void getMovementHistory_AfterMovement_ShouldContainHistory() {
        when(httpService.sendMoveCommand(moveRequest)).thenReturn(successResponse);

        platformService.movePlatform(moveRequest);
        List<MovementHistory> history = platformService.getMovementHistory();

        assertNotNull(history);
        assertEquals(1, history.size());
        MovementHistory firstEntry = history.get(0);
        assertEquals("forward", firstEntry.getDirection());
        assertEquals(100, firstEntry.getSpeed());
    }

    @Test
    void setWheelRadius_WithValidRadius_ShouldUpdateRadius() {
        double newRadius = 0.05;

        platformService.setWheelRadius(newRadius);

        verify(wsBroadcastService).broadcastPlatformUpdate(any(PlatformUpdateDto.class));
    }

    @Test
    void setWheelRadius_WithInvalidRadius_ShouldThrowException() {
        double invalidRadius = 0.15;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            platformService.setWheelRadius(invalidRadius);
        });

        assertTrue(exception.getMessage().contains("Wheel radius must be between"));
    }

    @Test
    void setWheelRadius_WithTooSmallRadius_ShouldThrowException() {
        double tooSmallRadius = 0.005;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            platformService.setWheelRadius(tooSmallRadius);
        });

        assertTrue(exception.getMessage().contains("Wheel radius must be between"));
    }

    @Test
    void movePlatform_ShouldSwitchProtocolFromHttpToWebSocket() {
        MoveRequestDto wsRequest = new MoveRequestDto("forward", 100, null, CommunicationProtocol.WEBSOCKET);
        when(httpService.isConnected()).thenReturn(true);
        when(webSocketService.isConnected()).thenReturn(false);
        when(webSocketService.sendMoveCommand(wsRequest)).thenReturn(successResponse);

        platformService.movePlatform(wsRequest);

        verify(httpService).disconnect();
        verify(webSocketService).connect();
        verify(webSocketService).sendMoveCommand(wsRequest);
        verify(wsBroadcastService, times(2)).broadcastStatusUpdate(anyString());
    }

    @Test
    void movePlatform_ShouldSwitchProtocolFromHttpToMqtt() {
        MoveRequestDto mqttRequest = new MoveRequestDto("backward", 80, null, CommunicationProtocol.MQTT);
        when(httpService.isConnected()).thenReturn(true);
        when(mqttService.isConnected()).thenReturn(false);
        when(mqttService.sendMoveCommand(mqttRequest)).thenReturn(successResponse);

        platformService.movePlatform(mqttRequest);

        verify(httpService).disconnect();
        verify(mqttService).connect();
        verify(mqttService).sendMoveCommand(mqttRequest);
        verify(wsBroadcastService, times(2)).broadcastStatusUpdate(anyString());
    }

    @Test
    void movePlatform_WithDifferentDirections_ShouldUpdatePositionCorrectly() {
        testMovementDirection("forward");
        testMovementDirection("backward");
        testMovementDirection("left");
        testMovementDirection("right");
    }

    private void testMovementDirection(String direction) {
        MoveRequestDto request = new MoveRequestDto(direction, 100, null, CommunicationProtocol.HTTP);
        when(httpService.sendMoveCommand(request)).thenReturn(successResponse);

        PlatformResponseDto result = platformService.movePlatform(request);

        assertNotNull(result);
        assertEquals("success", result.getStatus());
        assertEquals(direction, result.getDirection());

        reset(httpService, wsBroadcastService);
    }

    @Test
    void movePlatform_WithAngleInResponse_ShouldUpdateAngle() {
        ESP32ResponseDto responseWithAngle = new ESP32ResponseDto();
        responseWithAngle.setStatus("ok");
        responseWithAngle.setAngle(90.0);

        when(httpService.sendMoveCommand(moveRequest)).thenReturn(responseWithAngle);

        PlatformResponseDto result = platformService.movePlatform(moveRequest);

        assertNotNull(result);
        assertEquals(90.0, result.getAngle());
    }

    @Test
    void movePlatform_WithAngleInRequest_ShouldUseRequestAngle() {
        MoveRequestDto requestWithAngle = new MoveRequestDto("forward", 100, 45.0, CommunicationProtocol.HTTP);
        when(httpService.sendMoveCommand(requestWithAngle)).thenReturn(successResponse);

        PlatformResponseDto result = platformService.movePlatform(requestWithAngle);

        assertNotNull(result);
        assertNotNull(result.getAngle());
    }
}