package org.example.diplomwork.dto.position;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PositionResponseDto {
    private Integer x;
    private Integer y;
    private Double distanceTravelled;
    private Float angle;
}
