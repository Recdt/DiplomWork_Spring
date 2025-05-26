package org.example.diplomwork.dto.move;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.diplomwork.entities.CommunicationProtocol;

public record MoveRequestDto(
        @NotBlank(message = "Direction is required")
        String direction,
        @NotNull(message = "Speed is required")
        @Min(value = 0, message = "Speed must be between 0 and 255")
        @Max(value = 255, message = "Speed must be between 0 and 255")
        Integer speed,
        Double angle,
        @NotNull(message = "Communication protocol is required")
        CommunicationProtocol protocol
){}
