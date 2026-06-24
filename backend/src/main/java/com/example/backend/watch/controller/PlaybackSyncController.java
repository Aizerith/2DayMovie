package com.example.backend.watch.controller;

import com.example.backend.watch.dto.PlaybackSyncMessage;
import com.example.backend.watch.service.WatchRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class PlaybackSyncController {

    private final WatchRoomService watchRoomService;

    @MessageMapping("/rooms/{shareCode}/sync")
    @SendTo("/topic/rooms/{shareCode}")
    public PlaybackSyncMessage synchronize(
            @DestinationVariable String shareCode,
            @Valid PlaybackSyncMessage message
    ) {
        return watchRoomService.synchronize(shareCode, message);
    }
}
