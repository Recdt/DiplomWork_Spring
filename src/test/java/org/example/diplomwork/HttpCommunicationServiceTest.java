package org.example.diplomwork;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.diplomwork.dto.esp32.ESP32InfoResponseDto;
import org.example.diplomwork.dto.esp32.ESP32ResponseDto;
import org.example.diplomwork.dto.esp32.ESP32StatusResponseDto;
import org.example.diplomwork.dto.move.MoveRequestDto;
import org.example.diplomwork.entities.CommunicationProtocol;
import org.example.diplomwork.service.impl.HttpCommunicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpCommunicationServiceTest {

    @Mock
    private RestTemplate mockRestTemplate;

    @Mock
    private ObjectMapper mockObjectMapper;

    private HttpCommunicationService httpCommunicationService;
    private MoveRequestDto moveRequest;

    @BeforeEach
    void setUp() throws Exception {
        httpCommunicationService = new HttpCommunicationService();

        // Inject mocks using reflection
        setPrivateField(httpCommunicationService, "restTemplate", mockRestTemplate);
        setPrivateField(httpCommunicationService, "objectMapper", mockObjectMapper);

        moveRequest = new MoveRequestDto("forward", 100, null, CommunicationProtocol.HTTP);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void sendMoveCommand_WithValidRequest_ShouldReturnResponse() throws JsonProcessingException {
        ESP32ResponseDto expectedResponse = new ESP32ResponseDto();
        expectedResponse.setStatus("ok");
        expectedResponse.setMessage("Moving forward");

        String jsonBody = "{\"direction\":\"forward\",\"speed\":100}";
        ResponseEntity<ESP32ResponseDto> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(mockObjectMapper.writeValueAsString(any())).thenReturn(jsonBody);
        when(mockRestTemplate.exchange(
                eq("http://192.168.0.70/move"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(ESP32ResponseDto.class)
        )).thenReturn(responseEntity);

        ESP32ResponseDto result = httpCommunicationService.sendMoveCommand(moveRequest);

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertEquals("Moving forward", result.getMessage());
        verify(mockObjectMapper).writeValueAsString(any());
        verify(mockRestTemplate).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(ESP32ResponseDto.class));
    }

    @Test
    void sendMoveCommand_WithAngle_ShouldIncludeAngleInRequest() throws JsonProcessingException {
        MoveRequestDto requestWithAngle = new MoveRequestDto("forward", 100, 45.0, CommunicationProtocol.HTTP);
        ESP32ResponseDto expectedResponse = new ESP32ResponseDto();
        expectedResponse.setStatus("ok");

        String jsonBody = "{\"direction\":\"forward\",\"speed\":100,\"angle\":45.0}";
        ResponseEntity<ESP32ResponseDto> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(mockObjectMapper.writeValueAsString(any())).thenReturn(jsonBody);
        when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(ESP32ResponseDto.class)))
                .thenReturn(responseEntity);

        ESP32ResponseDto result = httpCommunicationService.sendMoveCommand(requestWithAngle);

        assertNotNull(result);
        verify(mockObjectMapper).writeValueAsString(any());
    }

    @Test
    void sendMoveCommand_WithJsonProcessingException_ShouldThrowRuntimeException() throws JsonProcessingException {
        when(mockObjectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("JSON error") {});

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            httpCommunicationService.sendMoveCommand(moveRequest);
        });

        assertTrue(exception.getMessage().contains("Failed to serialize request body"));
        verify(mockRestTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    }

    @Test
    void sendMoveCommand_WithRestClientException_ShouldThrowRuntimeException() throws JsonProcessingException {
        String jsonBody = "{\"direction\":\"forward\",\"speed\":100}";

        when(mockObjectMapper.writeValueAsString(any())).thenReturn(jsonBody);
        when(mockRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(ESP32ResponseDto.class)))
                .thenThrow(new RestClientException("Connection failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            httpCommunicationService.sendMoveCommand(moveRequest);
        });

        assertTrue(exception.getMessage().contains("Failed to communicate with platform via HTTP"));
    }

    @Test
    void sendStopCommand_WithValidResponse_ShouldReturnResponse() {
        ESP32ResponseDto expectedResponse = new ESP32ResponseDto();
        expectedResponse.setStatus("ok");
        ResponseEntity<ESP32ResponseDto> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(mockRestTemplate.getForEntity("http://192.168.0.70/stop", ESP32ResponseDto.class))
                .thenReturn(responseEntity);

        ESP32ResponseDto result = httpCommunicationService.sendStopCommand();

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        verify(mockRestTemplate).getForEntity("http://192.168.0.70/stop", ESP32ResponseDto.class);
    }

    @Test
    void sendStopCommand_WithException_ShouldThrowRuntimeException() {
        when(mockRestTemplate.getForEntity("http://192.168.0.70/stop", ESP32ResponseDto.class))
                .thenThrow(new RestClientException("Connection failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            httpCommunicationService.sendStopCommand();
        });

        assertTrue(exception.getMessage().contains("Failed to stop platform via HTTP"));
    }

    @Test
    void getStatus_WithValidResponse_ShouldReturnStatus() {
        ESP32StatusResponseDto expectedResponse = new ESP32StatusResponseDto();
        expectedResponse.setCurrentSpeed(100);
        expectedResponse.setCurrentDirection("forward");
        expectedResponse.setIsMoving(true);
        ResponseEntity<ESP32StatusResponseDto> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(mockRestTemplate.getForEntity("http://192.168.0.70/status", ESP32StatusResponseDto.class))
                .thenReturn(responseEntity);

        ESP32StatusResponseDto result = httpCommunicationService.getStatus();

        assertNotNull(result);
        assertEquals(100, result.getCurrentSpeed());
        assertEquals("forward", result.getCurrentDirection());
        assertTrue(result.getIsMoving());
        verify(mockRestTemplate).getForEntity("http://192.168.0.70/status", ESP32StatusResponseDto.class);
    }

    @Test
    void getStatus_WithException_ShouldThrowRuntimeException() {
        when(mockRestTemplate.getForEntity("http://192.168.0.70/status", ESP32StatusResponseDto.class))
                .thenThrow(new RestClientException("Connection failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            httpCommunicationService.getStatus();
        });

        assertTrue(exception.getMessage().contains("Failed to get ESP32 status via HTTP"));
    }

    @Test
    void getInfo_WithValidResponse_ShouldReturnInfo() {
        ESP32InfoResponseDto expectedResponse = new ESP32InfoResponseDto();
        expectedResponse.setStatus("ok");
        expectedResponse.setVersion("2.0.0");
        ResponseEntity<ESP32InfoResponseDto> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(mockRestTemplate.getForEntity("http://192.168.0.70/", ESP32InfoResponseDto.class))
                .thenReturn(responseEntity);

        ESP32InfoResponseDto result = httpCommunicationService.getInfo();

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertEquals("2.0.0", result.getVersion());
        verify(mockRestTemplate).getForEntity("http://192.168.0.70/", ESP32InfoResponseDto.class);
    }

    @Test
    void getInfo_WithException_ShouldThrowRuntimeException() {
        when(mockRestTemplate.getForEntity("http://192.168.0.70/", ESP32InfoResponseDto.class))
                .thenThrow(new RestClientException("Connection failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            httpCommunicationService.getInfo();
        });

        assertTrue(exception.getMessage().contains("Failed to get ESP32 info via HTTP"));
    }

    @Test
    void connect_ShouldDoNothing() {
        assertDoesNotThrow(() -> httpCommunicationService.connect());
    }

    @Test
    void disconnect_ShouldDoNothing() {
        assertDoesNotThrow(() -> httpCommunicationService.disconnect());
    }

    @Test
    void isConnected_ShouldAlwaysReturnTrue() {
        assertTrue(httpCommunicationService.isConnected());
    }

    @Test
    void sendMoveCommand_ShouldSetCorrectHeaders() throws JsonProcessingException {
        ESP32ResponseDto expectedResponse = new ESP32ResponseDto();
        expectedResponse.setStatus("ok");
        String jsonBody = "{\"direction\":\"forward\",\"speed\":100}";
        ResponseEntity<ESP32ResponseDto> responseEntity = new ResponseEntity<>(expectedResponse, HttpStatus.OK);

        when(mockObjectMapper.writeValueAsString(any())).thenReturn(jsonBody);
        when(mockRestTemplate.exchange(
                eq("http://192.168.0.70/move"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(ESP32ResponseDto.class)
        )).thenReturn(responseEntity);

        httpCommunicationService.sendMoveCommand(moveRequest);

        verify(mockRestTemplate).exchange(
                eq("http://192.168.0.70/move"),
                eq(HttpMethod.POST),
                argThat(entity -> {
                    HttpEntity<String> httpEntity = (HttpEntity<String>) entity;
                    return httpEntity.getHeaders().getContentType().equals(MediaType.APPLICATION_JSON) &&
                            httpEntity.getBody().equals(jsonBody);
                }),
                eq(ESP32ResponseDto.class)
        );
    }

    @Test
    void constructor_ShouldInitializeCorrectly() {
        HttpCommunicationService service = new HttpCommunicationService();

        assertNotNull(service);
        assertTrue(service.isConnected());
    }
}
