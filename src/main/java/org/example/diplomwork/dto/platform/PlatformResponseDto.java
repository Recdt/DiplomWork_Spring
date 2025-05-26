package org.example.diplomwork.dto.platform;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.diplomwork.entities.Position;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformResponseDto {
    private String status;
    private Position position;
    private String direction;
    private Double angle;
    private Double distanceTravelled;

    public PlatformResponseDto(String status, Position position, String direction) {
        this.status = status;
        this.position = position;
        this.direction = direction;
    }
}