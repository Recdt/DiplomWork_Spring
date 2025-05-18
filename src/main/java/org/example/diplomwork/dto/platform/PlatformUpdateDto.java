package org.example.diplomwork.dto.platform;

import lombok.Data;
import org.example.diplomwork.entities.Position;

@Data
public class PlatformUpdateDto {
    private String type;
    private Position position;
    private Float angle;
    private Integer speed;
    private String direction;
    private Boolean isMoving;
    private Long timestamp;
    private Double distanceTraveled;
    private String message;
    private String protocol;
}
