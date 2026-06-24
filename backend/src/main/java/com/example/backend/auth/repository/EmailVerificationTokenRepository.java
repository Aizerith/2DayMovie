package com.example.backend.auth.repository;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.entity.EmailVerificationToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    List<EmailVerificationToken> findByUserAndUsedAtIsNull(AppUser user);
}
