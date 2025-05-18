package org.example.diplomwork.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovementHistory {
    private String direction;
    private Integer speed;
    private Long timestamp;
    private Position position;
    private Float angle;
    private Double distanceTravelled;
}
