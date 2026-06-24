package com.example.backend.auth.repository;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.entity.PasswordResetToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    List<PasswordResetToken> findByUserAndUsedAtIsNull(AppUser user);
}
