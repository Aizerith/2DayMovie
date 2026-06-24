package com.example.backend.common.realtime;

import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeNotificationService {

    private static final String USER_NOTIFICATIONS_DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyUser(String email, String type, String message, Map<String, Object> payload) {
        if (email == null || email.isBlank()) {
            return;
        }

        RealtimeNotification notification = new RealtimeNotification(
                type,
                message,
                payload,
                Instant.now()
        );

        messagingTemplate.convertAndSendToUser(email, USER_NOTIFICATIONS_DESTINATION, notification);
    }
}
