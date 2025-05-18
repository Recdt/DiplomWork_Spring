package org.example.diplomwork.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.diplomwork.dto.esp32.ESP32InfoResponseDto;
import org.example.diplomwork.dto.esp32.ESP32ResponseDto;
import org.example.diplomwork.dto.esp32.ESP32StatusResponseDto;
import org.example.diplomwork.dto.move.MoveRequestDto;
import org.example.diplomwork.service.CommunicationService;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class HttpCommunicationService implements CommunicationService {
    private String esp32Url = "http://192.168.0.70";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HttpCommunicationService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ESP32ResponseDto sendMoveCommand(MoveRequestDto moveRequest) {
        String jsonBody;
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("direction", moveRequest.direction());
        requestMap.put("speed", moveRequest.speed());

        if (moveRequest.angle() != null) {
            requestMap.put("angle", moveRequest.angle());
        }

        try {
            jsonBody = objectMapper.writeValueAsString(requestMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        try {
            ResponseEntity<ESP32ResponseDto> response = restTemplate.exchange(
                    esp32Url + "/move",
                    HttpMethod.POST,
                    entity,
                    ESP32ResponseDto.class
            );

            return response.getBody();

        } catch (Exception e) {
            System.err.println("Error sending HTTP request to ESP32: " + e.getMessage());
            throw new RuntimeException("Failed to communicate with platform via HTTP: " + e.getMessage(), e);
        }
    }

    @Override
    public ESP32ResponseDto sendStopCommand() {
        try {
            ResponseEntity<ESP32ResponseDto> response = restTemplate.getForEntity(
                    esp32Url + "/stop",
                    ESP32ResponseDto.class
            );

            return response.getBody();

        } catch (Exception e) {
            throw new RuntimeException("Failed to stop platform via HTTP: " + e.getMessage());
        }
    }

    @Override
    public ESP32StatusResponseDto getStatus() {
        try {
            ResponseEntity<ESP32StatusResponseDto> response = restTemplate.getForEntity(
                    esp32Url + "/status",
                    ESP32StatusResponseDto.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get ESP32 status via HTTP: " + e.getMessage());
        }
    }

    @Override
    public ESP32InfoResponseDto getInfo() {
        try {
            ResponseEntity<ESP32InfoResponseDto> response = restTemplate.getForEntity(
                    esp32Url + "/",
                    ESP32InfoResponseDto.class
            );

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get ESP32 info via HTTP: " + e.getMessage());
        }
    }

    @Override
    public void connect() {
        // HTTP doesn't need persistent connection
    }

    @Override
    public void disconnect() {
        // HTTP doesn't need disconnect
    }

    @Override
    public boolean isConnected() {
        // HTTP is always "connected"
        return true;
    }
}
