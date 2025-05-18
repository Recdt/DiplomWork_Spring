package org.example.diplomwork.util.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.diplomwork.dto.platform.PlatformUpdateDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastPlatformUpdate(PlatformUpdateDto update) {
        log.debug("Broadcasting platform update: {}", update);
        messagingTemplate.convertAndSend("/topic/updates", update);
    }

    public void broadcastError(String error) {
        PlatformUpdateDto errorUpdate = new PlatformUpdateDto();
        errorUpdate.setType("ERROR");
        errorUpdate.setMessage(error);
        errorUpdate.setTimestamp(System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/errors", errorUpdate);
    }

    public void broadcastStatusUpdate(String status) {
        PlatformUpdateDto statusUpdate = new PlatformUpdateDto();
        statusUpdate.setType("STATUS_UPDATE");
        statusUpdate.setMessage(status);
        statusUpdate.setTimestamp(System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/status", statusUpdate);
    }
}