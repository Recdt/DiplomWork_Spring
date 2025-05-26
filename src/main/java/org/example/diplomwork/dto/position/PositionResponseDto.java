package org.example.diplomwork.dto.position;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionResponseDto {
    private Double x;
    private Double y;
    private Double distanceTravelled;
    private Double angle;
}
