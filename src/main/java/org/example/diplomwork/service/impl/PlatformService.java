package org.example.diplomwork.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.diplomwork.dto.esp32.ESP32InfoResponseDto;
import org.example.diplomwork.dto.esp32.ESP32ResponseDto;
import org.example.diplomwork.dto.esp32.ESP32StatusResponseDto;
import org.example.diplomwork.dto.move.MoveRequestDto;
import org.example.diplomwork.dto.platform.PlatformResponseDto;
import org.example.diplomwork.dto.platform.PlatformUpdateDto;
import org.example.diplomwork.dto.position.PositionResponseDto;
import org.example.diplomwork.entities.CommunicationProtocol;
import org.example.diplomwork.entities.MovementHistory;
import org.example.diplomwork.entities.Position;
import org.example.diplomwork.service.CommunicationService;
import org.example.diplomwork.util.websocket.WebSocketBroadcastService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PlatformService {
    private final HttpCommunicationService httpService;
    private final WebSocketCommunicationService webSocketService;
    private final MqttCommunicationService mqttService;
    private final WebSocketBroadcastService wsBroadcastService;

    private final List<MovementHistory> movementHistory;
    private final Position currentPosition;
    private Double currentAngle = 0.0;
    private Double totalDistance = 0.0;
    private Double wheelRadius = 0.03;

    private long lastMovementTime = 0;
    private final double maxRPM = 200.0;
    private final double movementTimeInterval = 0.1;

    private CommunicationProtocol currentProtocol = CommunicationProtocol.HTTP;
    private CommunicationService currentService;

    public PlatformService(HttpCommunicationService httpService,
                           WebSocketCommunicationService webSocketService,
                           MqttCommunicationService mqttService,
                           WebSocketBroadcastService wsBroadcastService) {
        this.httpService = httpService;
        this.webSocketService = webSocketService;
        this.mqttService = mqttService;
        this.wsBroadcastService = wsBroadcastService;
        this.movementHistory = new ArrayList<>();
        this.currentPosition = new Position(0., 0.);
        this.currentService = httpService;
        this.lastMovementTime = System.currentTimeMillis();
    }

    public PlatformResponseDto movePlatform(MoveRequestDto moveRequest) {
        switchProtocolIfNeeded(moveRequest.protocol());

        try {
            ESP32ResponseDto esp32Response = currentService.sendMoveCommand(moveRequest);

            if (esp32Response != null && "ok".equals(esp32Response.getStatus())) {
                updatePositionWithResponse(moveRequest, esp32Response);
                PlatformUpdateDto updateDto = createPlatformUpdate(moveRequest, esp32Response);
                wsBroadcastService.broadcastPlatformUpdate(updateDto);
                saveMovementHistory(moveRequest);
                return createSuccessResponse(moveRequest);
            } else {
                handleErrorResponse(esp32Response);
                throw new RuntimeException("ESP32 error: " +
                        (esp32Response != null ? esp32Response.getMessage() : "Unknown error"));
            }

        } catch (Exception e) {
            log.error("Error sending request to ESP32: ", e);
            wsBroadcastService.broadcastError("Failed to communicate: " + e.getMessage());
            throw new RuntimeException("Failed to communicate with platform: " + e.getMessage(), e);
        }
    }

    public PlatformResponseDto stopPlatform() {
        try {
            ESP32ResponseDto esp32Response = currentService.sendStopCommand();

            if (esp32Response != null && "ok".equals(esp32Response.getStatus())) {
                PlatformUpdateDto stopUpdate = new PlatformUpdateDto();
                stopUpdate.setType("POSITION_UPDATE");
                stopUpdate.setPosition(currentPosition);
                stopUpdate.setAngle(currentAngle);
                stopUpdate.setDirection("stop");
                stopUpdate.setSpeed(0);
                stopUpdate.setIsMoving(false);
                stopUpdate.setDistanceTraveled(totalDistance);
                stopUpdate.setTimestamp(System.currentTimeMillis());

                wsBroadcastService.broadcastPlatformUpdate(stopUpdate);

                return createStopResponse();
            } else {
                handleErrorResponse(esp32Response);
                throw new RuntimeException("ESP32 error: " +
                        (esp32Response != null ? esp32Response.getMessage() : "Unknown error"));
            }

        } catch (Exception e) {
            log.error("Failed to stop platform: ", e);
            wsBroadcastService.broadcastError("Failed to stop: " + e.getMessage());
            throw new RuntimeException("Failed to stop platform: " + e.getMessage());
        }
    }

    public ESP32StatusResponseDto getESP32Status() {
        try {
            ESP32StatusResponseDto statusResponse = currentService.getStatus();

            if (statusResponse == null) {
                throw new RuntimeException("Failed to get ESP32 status: null response");
            }

            PlatformUpdateDto statusUpdate = new PlatformUpdateDto();
            statusUpdate.setType("STATUS_UPDATE");
            statusUpdate.setPosition(currentPosition);
            statusUpdate.setAngle(statusResponse.getCurrentAngle() != null ?
                    statusResponse.getCurrentAngle() : currentAngle);
            statusUpdate.setSpeed(statusResponse.getCurrentSpeed());
            statusUpdate.setDirection(statusResponse.getCurrentDirection());
            statusUpdate.setIsMoving(statusResponse.getIsMoving());
            statusUpdate.setTimestamp(System.currentTimeMillis());

            wsBroadcastService.broadcastPlatformUpdate(statusUpdate);

            return statusResponse;
        } catch (Exception e) {
            log.error("Failed to get ESP32 status: ", e);
            wsBroadcastService.broadcastError("Status check failed: " + e.getMessage());
            throw new RuntimeException("Failed to get ESP32 status: " + e.getMessage());
        }
    }

    private PlatformUpdateDto createPlatformUpdate(MoveRequestDto request, ESP32ResponseDto response) {
        PlatformUpdateDto update = new PlatformUpdateDto();
        update.setType("POSITION_UPDATE");
        update.setPosition(currentPosition);
        update.setAngle(currentAngle);
        update.setDirection(request.direction());
        update.setSpeed(request.speed());
        update.setIsMoving(true);
        update.setDistanceTraveled(totalDistance);
        update.setTimestamp(System.currentTimeMillis());

        if (response.getAngle() != null) {
            update.setAngle(response.getAngle());
        }

        return update;
    }

    private void updatePositionWithResponse(MoveRequestDto request, ESP32ResponseDto response) {
        if (response.getAngle() != null) {
            this.currentAngle = response.getAngle();
        } else if (request.angle() != null) {
            this.currentAngle = request.angle();
        }

        double distance = calculateRealisticDistance(request.speed());
        updatePositionBasedOnDirection(request.direction(), distance);
        totalDistance += Math.abs(distance);

        log.debug("Movement - Direction: {}, Speed: {}, Distance: {:.4f}m, Total: {:.4f}m",
                request.direction(), request.speed(), distance, totalDistance);
    }

    private double calculateRealisticDistance(int motorSpeed) {
        long currentTime = System.currentTimeMillis();
        double deltaTime = (currentTime - lastMovementTime) / 1000.0;
        lastMovementTime = currentTime;
        if (deltaTime > 1.0 || deltaTime <= 0) {
            deltaTime = movementTimeInterval;
        }
        double speedPercentage = Math.max(0, Math.min(255, motorSpeed)) / 255.0;
        double currentRPM = maxRPM * speedPercentage;
        double rotations = (currentRPM / 60.0) * deltaTime;
        double wheelCircumference = 2 * Math.PI * wheelRadius;

        double distance = rotations * wheelCircumference;

        log.debug("Speed: {}%, RPM: {:.2f}, Time: {:.3f}s, Rotations: {:.4f}, Distance: {:.4f}m",
                speedPercentage * 100, currentRPM, deltaTime, rotations, distance);

        return distance;
    }

    private void updatePositionBasedOnDirection(String direction, double distance) {
        switch (direction) {
            case "forward":
                double deltaX = distance * Math.cos(Math.toRadians(currentAngle));
                double deltaY = distance * Math.sin(Math.toRadians(currentAngle));
                currentPosition.setX(currentPosition.getX() + deltaX);
                currentPosition.setY(currentPosition.getY() + deltaY);
                log.debug("Forward movement - DeltaX: {:.4f}, DeltaY: {:.4f}, New pos: ({:.4f}, {:.4f})",
                        deltaX, deltaY, currentPosition.getX(), currentPosition.getY());
                break;

            case "backward":
                double backDeltaX = -distance * Math.cos(Math.toRadians(currentAngle));
                double backDeltaY = -distance * Math.sin(Math.toRadians(currentAngle));
                currentPosition.setX(currentPosition.getX() + backDeltaX);
                currentPosition.setY(currentPosition.getY() + backDeltaY);
                log.debug("Backward movement - DeltaX: {:.4f}, DeltaY: {:.4f}, New pos: ({:.4f}, {:.4f})",
                        backDeltaX, backDeltaY, currentPosition.getX(), currentPosition.getY());
                break;

            case "left":
                double leftTurnAngle = calculateTurnAngle(distance);
                currentAngle -= leftTurnAngle;
                if (currentAngle < 0) currentAngle += 360;
                log.debug("Left turn - Angle change: {:.2f}°, New angle: {:.2f}°",
                        leftTurnAngle, currentAngle);
                break;

            case "right":
                double rightTurnAngle = calculateTurnAngle(distance);
                currentAngle += rightTurnAngle;
                if (currentAngle >= 360) currentAngle -= 360;
                log.debug("Right turn - Angle change: {:.2f}°, New angle: {:.2f}°",
                        rightTurnAngle, currentAngle);
                break;
        }
    }

    private double calculateTurnAngle(double wheelDistance) {
        double wheelbase = 0.2;
        double angleRadians = wheelDistance / (wheelbase / 2);
        double angleDegrees = Math.toDegrees(angleRadians);
        return Math.min(angleDegrees, 10.0);
    }

    public PlatformResponseDto resetPosition() {
        currentPosition.setX(0.);
        currentPosition.setY(0.);
        currentAngle = 0.0;
        totalDistance = 0.0;
        lastMovementTime = System.currentTimeMillis();

        log.info("Position reset to origin");

        PlatformResponseDto response = new PlatformResponseDto();
        response.setPosition(new Position(0., 0.));
        response.setAngle(0.0);
        response.setDistanceTravelled(0.0);

        return response;
    }

    public ESP32InfoResponseDto getESP32Info() {
        try {
            ESP32InfoResponseDto infoResponse = currentService.getInfo();

            if (infoResponse == null) {
                throw new RuntimeException("Failed to get ESP32 info: null response");
            }

            if ("error".equals(infoResponse.getStatus())) {
                throw new RuntimeException("ESP32 info error: " + infoResponse);
            }

            return infoResponse;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get ESP32 info: " + e.getMessage());
        }
    }

    public PositionResponseDto getCurrentPosition() {
        return new PositionResponseDto(
                currentPosition.getX(),
                currentPosition.getY(),
                totalDistance,
                currentAngle
        );
    }

    public List<MovementHistory> getMovementHistory() {
        return movementHistory.stream()
                .map(history -> {
                    MovementHistory newHistory = new MovementHistory(
                            history.getDirection(),
                            history.getSpeed(),
                            history.getTimestamp(),
                            history.getPosition(),
                            history.getAngle(),
                            history.getDistanceTravelled()
                    );
                    newHistory.setAngle(history.getAngle());
                    return newHistory;
                })
                .collect(Collectors.toList());
    }

    public void setWheelRadius(Double radius) {
        if (radius < 0.01 || radius > 0.1) {
            throw new IllegalArgumentException("Wheel radius must be between 0.01m and 0.1m");
        }

        double oldRadius = this.wheelRadius;
        this.wheelRadius = radius;

        log.info("Wheel radius updated from {}m to {}m", oldRadius, radius);
        log.info("Wheel circumference: {:.4f}m", 2 * Math.PI * radius);

        PlatformUpdateDto configUpdate = new PlatformUpdateDto();
        configUpdate.setType("CONFIG_UPDATE");
        configUpdate.setMessage(String.format("Wheel radius updated to %.3fm (circumference: %.4fm)",
                radius, 2 * Math.PI * radius));
        configUpdate.setTimestamp(System.currentTimeMillis());

        wsBroadcastService.broadcastPlatformUpdate(configUpdate);
    }

    private void handleErrorResponse(ESP32ResponseDto response) {
        String errorMsg = response != null ? response.getMessage() : "Unknown error";
        log.error("ESP32 error response: {}", errorMsg);
        wsBroadcastService.broadcastError("ESP32 error: " + errorMsg);
    }

    private void switchProtocolIfNeeded(CommunicationProtocol protocol) {
        if (protocol != currentProtocol) {
            log.info("Switching from {} to {} protocol", currentProtocol, protocol);

            wsBroadcastService.broadcastStatusUpdate(
                    String.format("Switching to %s protocol", protocol));

            if (currentService != null && currentService.isConnected()) {
                try {
                    currentService.disconnect();
                } catch (Exception e) {
                    log.warn("Error disconnecting from {}: {}", currentProtocol, e.getMessage());
                }
            }

            switch (protocol) {
                case HTTP:
                    currentService = httpService;
                    break;
                case WEBSOCKET:
                    currentService = webSocketService;
                    break;
                case MQTT:
                    currentService = mqttService;
                    break;
            }

            try {
                if (!currentService.isConnected()) {
                    currentService.connect();
                }

                wsBroadcastService.broadcastStatusUpdate(
                        String.format("Connected via %s", protocol));

            } catch (Exception e) {
                log.error("Error connecting to {}: {}", protocol, e.getMessage());
                wsBroadcastService.broadcastError(
                        String.format("Failed to connect via %s, falling back to HTTP", protocol));

                currentService = httpService;
                currentProtocol = CommunicationProtocol.HTTP;
                throw new RuntimeException("Failed to connect to " + protocol + ", using HTTP fallback");
            }

            currentProtocol = protocol;
        }
    }

    private PlatformResponseDto createSuccessResponse(MoveRequestDto request) {
        PlatformResponseDto response = new PlatformResponseDto(
                "success",
                currentPosition,
                request.direction()
        );
        response.setAngle(currentAngle);
        response.setDistanceTravelled(totalDistance);
        return response;
    }

    private PlatformResponseDto createStopResponse() {
        PlatformResponseDto response = new PlatformResponseDto();
        response.setPosition(currentPosition);
        response.setAngle(currentAngle);
        response.setDistanceTravelled(totalDistance);
        return response;
    }

    private void saveMovementHistory(MoveRequestDto request) {
        MovementHistory historyEntry = new MovementHistory(
                request.direction(),
                request.speed(),
                System.currentTimeMillis(),
                new Position(currentPosition.getX(), currentPosition.getY()),
                currentAngle,
                totalDistance
        );
        movementHistory.add(historyEntry);
    }
}