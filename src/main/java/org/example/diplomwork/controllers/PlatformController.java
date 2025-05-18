package org.example.diplomwork.controllers;

import jakarta.validation.Valid;
import org.example.diplomwork.dto.esp32.ESP32InfoResponseDto;
import org.example.diplomwork.dto.esp32.ESP32StatusResponseDto;
import org.example.diplomwork.dto.move.MoveRequestDto;
import org.example.diplomwork.dto.platform.PlatformResponseDto;
import org.example.diplomwork.dto.position.PositionResponseDto;
import org.example.diplomwork.entities.MovementHistory;
import org.example.diplomwork.service.impl.PlatformService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1")
public class PlatformController {
    private final PlatformService platformService;

    public PlatformController(PlatformService carService) {
        this.platformService = carService;
    }

    @PostMapping("/move")
    public PlatformResponseDto movePlatform(@Valid @RequestBody MoveRequestDto moveRequest) {
        return platformService.movePlatform(moveRequest);
    }

    @GetMapping("/stop")
    public PlatformResponseDto stopPlatform() {
        return platformService.stopPlatform();
    }

    @GetMapping("/position")
    public PositionResponseDto getCurrentPosition() {
        return platformService.getCurrentPosition();
    }

    @GetMapping("/history")
    public List<MovementHistory> getMovementHistory() {
        return platformService.getMovementHistory();
    }

    @PostMapping("/reset")
    public PlatformResponseDto resetPosition() {
        return platformService.resetPosition();
    }

    @GetMapping("/esp32/status")
    public ESP32StatusResponseDto getESP32Status() {
        return platformService.getESP32Status();
    }

    @GetMapping("/esp32/info")
    public ESP32InfoResponseDto getESP32Info() {
        return platformService.getESP32Info();
    }

    @PatchMapping("/radius/update")
    public ResponseEntity<Void> updateWheelRadius(Double radius) {
        platformService.setWheelRadius(radius);
        return ResponseEntity.ok().build();
    }
}
