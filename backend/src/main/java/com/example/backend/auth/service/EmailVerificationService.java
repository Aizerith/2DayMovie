package com.example.backend.auth.service;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.entity.EmailVerificationToken;
import com.example.backend.auth.exception.InvalidEmailVerificationTokenException;
import com.example.backend.auth.repository.AppUserRepository;
import com.example.backend.auth.repository.EmailVerificationTokenRepository;
import com.example.backend.common.config.AppProperties;
import com.example.backend.common.mail.MailMessage;
import com.example.backend.common.mail.MailService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private final AppUserRepository appUserRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final AppProperties appProperties;
    private final MailService mailService;

    public EmailVerificationService(
            AppUserRepository appUserRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            AppProperties appProperties,
            MailService mailService
    ) {
        this.appUserRepository = appUserRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.appProperties = appProperties;
        this.mailService = mailService;
    }

    @Transactional
    public void resendVerification(String email) {
        appUserRepository.findByEmailIgnoreCase(email)
                .filter(AppUser::isEnabled)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendVerificationEmail);
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidEmailVerificationTokenException("Email verification token is invalid"));

        if (!verificationToken.isUsable(LocalDateTime.now())) {
            throw new InvalidEmailVerificationTokenException("Email verification token has expired or has already been used");
        }

        verificationToken.getUser().setEmailVerified(true);
        verificationToken.setUsedAt(LocalDateTime.now());
    }

    @Transactional
    public void sendVerificationEmail(AppUser user) {
        if (!user.isEnabled() || user.isEmailVerified()) {
            return;
        }

        emailVerificationTokenRepository.findByUserAndUsedAtIsNull(user)
                .forEach(token -> token.setUsedAt(LocalDateTime.now()));

        String tokenValue = UUID.randomUUID().toString();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(tokenValue);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(appProperties.getAuth().getEmailVerificationTokenExpirationMinutes()));
        emailVerificationTokenRepository.save(token);

        String verificationLink = appProperties.getFrontend().getBaseUrl() + "/verify-email?token=" + tokenValue;
        mailService.send(new MailMessage(
                user.getEmail(),
                "Verify your email address",
                """
                        Hello %s,

                        Welcome to Boilerplate.
                        Please verify your email address with the following link:
                        %s

                        If you did not expect this email, you can ignore it.
                        """.formatted(user.getFirstName(), verificationLink)
        ));
    }
}
