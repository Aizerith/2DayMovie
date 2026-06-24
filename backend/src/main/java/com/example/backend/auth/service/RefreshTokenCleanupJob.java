package com.example.backend.auth.service;

import com.example.backend.auth.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    @Scheduled(
            fixedDelayString = "${app.auth.expired-token-cleanup-fixed-delay-ms:86400000}",
            initialDelayString = "${app.auth.expired-token-cleanup-fixed-delay-ms:86400000}"
    )
    public void deleteExpiredRefreshTokens() {
        long deletedCount = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        if (deletedCount > 0) {
            log.info("Deleted {} expired refresh token(s)", deletedCount);
        }
    }
}
