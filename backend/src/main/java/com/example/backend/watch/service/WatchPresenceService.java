package com.example.backend.watch.service;

import com.example.backend.watch.dto.PresenceMessage;
import com.example.backend.watch.dto.PresenceParticipantResponse;
import com.example.backend.watch.repository.WatchRoomRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WatchPresenceService {

    private static final long STALE_AFTER_MILLIS = 45_000;

    private final WatchRoomRepository watchRoomRepository;
    private final PasswordEncoder passwordEncoder;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, Map<String, ParticipantPresence>> rooms = new ConcurrentHashMap<>();

    public WatchPresenceService(
            WatchRoomRepository watchRoomRepository,
            PasswordEncoder passwordEncoder,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.watchRoomRepository = watchRoomRepository;
        this.passwordEncoder = passwordEncoder;
        this.messagingTemplate = messagingTemplate;
    }

    public PresenceMessage update(String shareCode, PresenceMessage message) {
        verifyPin(shareCode, message.pin());

        String event = StringUtils.hasText(message.event()) ? message.event() : "heartbeat";
        Map<String, ParticipantPresence> roomPresence = rooms.computeIfAbsent(
                shareCode.toUpperCase(),
                ignored -> new ConcurrentHashMap<>()
        );

        if ("leave".equals(event)) {
            roomPresence.remove(message.clientId());
        } else {
            long now = System.currentTimeMillis();
            roomPresence.compute(message.clientId(), (clientId, existing) -> new ParticipantPresence(
                    clientId,
                    displayName(message.displayName(), existing),
                    avatar(message.avatar(), existing),
                    existing == null ? now : existing.joinedAt(),
                    now
            ));
        }

        PresenceMessage response = snapshot(shareCode, event);
        messagingTemplate.convertAndSend("/topic/rooms/" + shareCode + "/presence", response);
        return response;
    }

    public void clearRoom(String shareCode) {
        rooms.remove(shareCode.toUpperCase());
        messagingTemplate.convertAndSend("/topic/rooms/" + shareCode + "/presence", snapshot(shareCode, "closed"));
    }

    @Scheduled(fixedDelay = 15_000)
    void pruneStaleParticipants() {
        long cutoff = System.currentTimeMillis() - STALE_AFTER_MILLIS;
        rooms.forEach((shareCode, participants) -> {
            boolean removed = participants.entrySet().removeIf(entry -> entry.getValue().lastSeenAt() < cutoff);
            if (removed) {
                messagingTemplate.convertAndSend("/topic/rooms/" + shareCode + "/presence", snapshot(shareCode, "prune"));
            }
        });
    }

    private void verifyPin(String shareCode, String pin) {
        var room = watchRoomRepository.findByShareCodeIgnoreCase(shareCode)
                .orElseThrow(() -> new IllegalArgumentException("Salon introuvable"));

        if (!passwordEncoder.matches(pin, room.getPinHash())) {
            throw new AccessDeniedException("Code PIN invalide");
        }
    }

    private PresenceMessage snapshot(String shareCode, String event) {
        List<PresenceParticipantResponse> participants = rooms
                .getOrDefault(shareCode.toUpperCase(), Map.of())
                .values()
                .stream()
                .sorted(Comparator.comparingLong(ParticipantPresence::joinedAt))
                .map(participant -> new PresenceParticipantResponse(
                        participant.clientId(),
                        participant.displayName(),
                        participant.avatar(),
                        participant.joinedAt()
                ))
                .toList();

        return new PresenceMessage("", "server", "", "", event, participants, System.currentTimeMillis());
    }

    private String displayName(String candidate, ParticipantPresence existing) {
        if (StringUtils.hasText(candidate)) {
            return candidate.trim();
        }
        return existing == null ? "Invite" : existing.displayName();
    }

    private String avatar(String candidate, ParticipantPresence existing) {
        if (StringUtils.hasText(candidate)) {
            return candidate.trim();
        }
        return existing == null ? "LY" : existing.avatar();
    }

    private record ParticipantPresence(
            String clientId,
            String displayName,
            String avatar,
            long joinedAt,
            long lastSeenAt
    ) {
    }
}
