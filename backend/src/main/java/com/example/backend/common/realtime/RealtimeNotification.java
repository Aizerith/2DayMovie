package com.example.backend.common.realtime;

import java.time.Instant;
import java.util.Map;

public record RealtimeNotification(
        String type,
        String message,
        Map<String, Object> payload,
        Instant createdAt
) {
}
