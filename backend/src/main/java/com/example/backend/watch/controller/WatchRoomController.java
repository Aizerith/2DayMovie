package com.example.backend.watch.controller;

import com.example.backend.watch.dto.AccessWatchRoomRequest;
import com.example.backend.watch.dto.CloseWatchRoomResponse;
import com.example.backend.watch.dto.CompleteWatchRoomRequest;
import com.example.backend.watch.dto.CreateWatchRoomRequest;
import com.example.backend.watch.dto.CreateWatchRoomResponse;
import com.example.backend.watch.dto.WatchRoomAccessResponse;
import com.example.backend.watch.service.WatchRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class WatchRoomController {

    private final WatchRoomService watchRoomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateWatchRoomResponse create(@Valid @RequestBody CreateWatchRoomRequest request) {
        return watchRoomService.create(request);
    }

    @PostMapping("/{shareCode}/complete")
    public WatchRoomAccessResponse complete(
            @PathVariable String shareCode,
            @Valid @RequestBody CompleteWatchRoomRequest request
    ) {
        return watchRoomService.complete(shareCode, request);
    }

    @PostMapping("/{shareCode}/access")
    public WatchRoomAccessResponse access(
            @PathVariable String shareCode,
            @Valid @RequestBody AccessWatchRoomRequest request
    ) {
        return watchRoomService.access(shareCode, request);
    }

    @PostMapping("/{shareCode}/close")
    public CloseWatchRoomResponse close(
            @PathVariable String shareCode,
            @Valid @RequestBody AccessWatchRoomRequest request
    ) {
        watchRoomService.close(shareCode, request);
        return new CloseWatchRoomResponse(shareCode, true);
    }
}
