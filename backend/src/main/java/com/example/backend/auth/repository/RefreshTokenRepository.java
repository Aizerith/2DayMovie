package com.example.backend.auth.repository;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUserAndRevokedFalse(AppUser user);

    long deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
