package org.example.diplomwork.dto.esp32;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ESP32InfoResponseDto {
    private String status;
    private String platformName;
    private String version;
    private String ip;
    private String[] endpoints;
    private Capabilities capabilities;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Capabilities {
        private String directions;
        private String speedRange;
    }
}
