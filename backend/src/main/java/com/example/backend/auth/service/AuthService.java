package com.example.backend.auth.service;

import com.example.backend.auth.dto.AuthResponse;
import com.example.backend.auth.dto.ForgotPasswordRequest;
import com.example.backend.auth.dto.LoginRequest;
import com.example.backend.auth.dto.RegisterRequest;
import com.example.backend.auth.dto.ResendVerificationRequest;
import com.example.backend.auth.dto.ResetPasswordRequest;
import com.example.backend.auth.dto.UserResponse;
import com.example.backend.auth.dto.VerifyEmailRequest;
import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.entity.PasswordResetToken;
import com.example.backend.auth.entity.RefreshToken;
import com.example.backend.auth.exception.InvalidCredentialsException;
import com.example.backend.auth.exception.InvalidPasswordResetTokenException;
import com.example.backend.auth.exception.InvalidRefreshTokenException;
import com.example.backend.auth.repository.AppUserRepository;
import com.example.backend.auth.repository.PasswordResetTokenRepository;
import com.example.backend.auth.repository.RefreshTokenRepository;
import com.example.backend.auth.security.JwtService;
import com.example.backend.common.config.AppProperties;
import com.example.backend.common.mail.MailMessage;
import com.example.backend.common.mail.MailService;
import com.example.backend.user.exception.UserConflictException;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password())
            );
        } catch (Exception exception) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!user.isEmailVerified()) {
            throw new InvalidCredentialsException("Email address is not verified");
        }

        return issueTokens(user);
    }

    @Transactional
    public void register(RegisterRequest request) {
        appUserRepository.findByEmailIgnoreCase(request.email()).ifPresent(existingUser -> {
            throw new UserConflictException("A user with this email already exists");
        });

        AppUser user = new AppUser();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("USER");
        user.setEnabled(true);
        user.setEmailVerified(false);

        AppUser savedUser = appUserRepository.save(user);
        emailVerificationService.sendVerificationEmail(savedUser);
    }

    @Transactional
    public AuthResponse refresh(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired or has been revoked");
        }

        refreshToken.setRevoked(true);
        AppUser user = refreshToken.getUser();

        return issueTokens(user);
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        refreshTokenRepository.findByToken(token).ifPresent(refreshToken -> refreshToken.setRevoked(true));
    }

    public UserResponse getCurrentUser(Authentication authentication) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new InvalidCredentialsException("Authenticated user was not found"));
        return toUserResponse(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        appUserRepository.findByEmailIgnoreCase(request.email())
                .filter(AppUser::isEnabled)
                .ifPresent(this::createPasswordResetTokenAndSendMail);
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        emailVerificationService.resendVerification(request.email());
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        emailVerificationService.verifyEmail(request.token());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Password reset token is invalid"));

        if (!passwordResetToken.isUsable(LocalDateTime.now())) {
            throw new InvalidPasswordResetTokenException("Password reset token has expired or has already been used");
        }

        AppUser user = passwordResetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        passwordResetToken.setUsedAt(LocalDateTime.now());
        revokeActiveTokens(user);
    }

    private AuthResponse issueTokens(AppUser user) {
        revokeActiveTokens(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = UUID.randomUUID().toString();

        RefreshToken refreshTokenEntity = new RefreshToken();
        refreshTokenEntity.setUser(user);
        refreshTokenEntity.setToken(refreshToken);
        refreshTokenEntity.setExpiresAt(LocalDateTime.now().plusDays(appProperties.getJwt().getRefreshTokenExpirationDays()));
        refreshTokenEntity.setRevoked(false);
        refreshTokenRepository.save(refreshTokenEntity);

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds(),
                toUserResponse(user)
        );
    }

    private void revokeActiveTokens(AppUser user) {
        refreshTokenRepository.findByUserAndRevokedFalse(user)
                .forEach(token -> token.setRevoked(true));
    }

    private void createPasswordResetTokenAndSendMail(AppUser user) {
        passwordResetTokenRepository.findByUserAndUsedAtIsNull(user)
                .forEach(token -> token.setUsedAt(LocalDateTime.now()));

        String tokenValue = UUID.randomUUID().toString();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(tokenValue);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(appProperties.getAuth().getPasswordResetTokenExpirationMinutes()));
        passwordResetTokenRepository.save(token);

        String resetLink = appProperties.getFrontend().getBaseUrl() + "/reset-password?token=" + tokenValue;
        mailService.send(new MailMessage(
                user.getEmail(),
                "Password reset instructions",
                """
                        Hello %s,

                        A password reset was requested for your account.
                        Use the following link to choose a new password:
                        %s

                        If you did not request this, you can ignore this email.
                        """.formatted(user.getFirstName(), resetLink)
        ));
    }

    private UserResponse toUserResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isEmailVerified()
        );
    }
}
