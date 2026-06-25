package com.example.backend.watch.controller;

import com.example.backend.watch.dto.PresenceMessage;
import com.example.backend.watch.service.WatchPresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class PresenceController {

    private final WatchPresenceService watchPresenceService;

    @MessageMapping("/rooms/{shareCode}/presence")
    public void presence(
            @DestinationVariable String shareCode,
            @Valid PresenceMessage message
    ) {
        watchPresenceService.update(shareCode, message);
    }
}
