package org.example.diplomwork.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.diplomwork.dto.esp32.ESP32InfoResponseDto;
import org.example.diplomwork.dto.esp32.ESP32StatusResponseDto;
import org.example.diplomwork.dto.move.MoveRequestDto;
import org.example.diplomwork.dto.platform.PlatformResponseDto;
import org.example.diplomwork.dto.position.PositionResponseDto;
import org.example.diplomwork.entities.MovementHistory;
import org.example.diplomwork.service.impl.PlatformService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Platform Controller", description = "API for ESP32 platform control")
public class PlatformController {
    private final PlatformService platformService;

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @PostMapping("/move")
    @Operation(
            summary = "Move platform",
            description = "Sends command to move platform in specified direction with given speed"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Command executed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public PlatformResponseDto movePlatform(
            @Parameter(description = "Platform movement parameters", required = true)
            @Valid @RequestBody MoveRequestDto moveRequest
    ) {
        return platformService.movePlatform(moveRequest);
    }

    @GetMapping("/stop")
    @Operation(
            summary = "Stop platform",
            description = "Sends command to stop platform movement"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Platform stopped successfully"),
            @ApiResponse(responseCode = "500", description = "Error stopping platform")
    })
    public PlatformResponseDto stopPlatform() {
        return platformService.stopPlatform();
    }

    @GetMapping("/position")
    @Operation(
            summary = "Get current position",
            description = "Returns current platform coordinates"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Position retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Error retrieving position")
    })
    public PositionResponseDto getCurrentPosition() {
        return platformService.getCurrentPosition();
    }

    @GetMapping("/history")
    @Operation(
            summary = "Get movement history",
            description = "Returns list of all executed platform movements"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Error retrieving history")
    })
    public List<MovementHistory> getMovementHistory() {
        return platformService.getMovementHistory();
    }

    @PostMapping("/reset")
    @Operation(
            summary = "Reset position",
            description = "Resets current platform position to initial coordinates (0,0)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Position reset successfully"),
            @ApiResponse(responseCode = "500", description = "Error resetting position")
    })
    public PlatformResponseDto resetPosition() {
        return platformService.resetPosition();
    }

    @GetMapping("/esp32/status")
    @Operation(
            summary = "Get ESP32 status",
            description = "Returns current status and state of ESP32 module"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
            @ApiResponse(responseCode = "503", description = "ESP32 unavailable"),
            @ApiResponse(responseCode = "500", description = "Error retrieving status")
    })
    public ESP32StatusResponseDto getESP32Status() {
        return platformService.getESP32Status();
    }

    @GetMapping("/esp32/info")
    @Operation(
            summary = "Get ESP32 info",
            description = "Returns technical information about ESP32 module"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Information retrieved successfully"),
            @ApiResponse(responseCode = "503", description = "ESP32 unavailable"),
            @ApiResponse(responseCode = "500", description = "Error retrieving information")
    })
    public ESP32InfoResponseDto getESP32Info() {
        return platformService.getESP32Info();
    }

    @PatchMapping("/radius/update")
    @Operation(
            summary = "Update wheel radius",
            description = "Updates wheel radius for more accurate movement calculations"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Radius updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid radius value"),
            @ApiResponse(responseCode = "500", description = "Error updating radius")
    })
    public ResponseEntity<Void> updateWheelRadius(
            @Parameter(description = "New wheel radius in mm", required = true, example = "32.5")
            @RequestParam Double radius
    ) {
        platformService.setWheelRadius(radius);
        return ResponseEntity.ok().build();
    }
}