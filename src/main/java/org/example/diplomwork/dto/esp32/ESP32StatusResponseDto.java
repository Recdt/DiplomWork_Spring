package org.example.diplomwork.dto.esp32;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ESP32StatusResponseDto {
    private String status;
    private String currentDirection;
    private Integer currentSpeed;
    private Float currentAngle;
    private Boolean isMoving;
    private Long uptime;
    private Long operationDuration;
    private String wifiStatus;
    private String ip;
}
