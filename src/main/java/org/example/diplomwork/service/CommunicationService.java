package org.example.diplomwork.service;

import org.example.diplomwork.dto.esp32.ESP32InfoResponseDto;
import org.example.diplomwork.dto.esp32.ESP32ResponseDto;
import org.example.diplomwork.dto.esp32.ESP32StatusResponseDto;
import org.example.diplomwork.dto.move.MoveRequestDto;

public interface CommunicationService {
    ESP32ResponseDto sendMoveCommand(MoveRequestDto moveRequest);
    ESP32ResponseDto sendStopCommand();
    ESP32StatusResponseDto getStatus();
    ESP32InfoResponseDto getInfo();
    void connect();
    void disconnect();
    boolean isConnected();
}
