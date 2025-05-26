package org.example.diplomwork.dto.esp32;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ESP32ResponseDto {
    private String status;
    private String message;
    private String direction;
    private Integer speed;
    private Double angle;
    private Long timestamp;
    private Integer operationId;
    private Boolean isMoving;
}
