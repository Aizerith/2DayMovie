package com.example.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.auth.dto.AuthResponse;
import com.example.backend.auth.dto.ForgotPasswordRequest;
import com.example.backend.auth.dto.LoginRequest;
import com.example.backend.auth.dto.RegisterRequest;
import com.example.backend.auth.dto.ResendVerificationRequest;
import com.example.backend.auth.dto.ResetPasswordRequest;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MailService mailService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private Authentication authentication;

    private AuthService authService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getJwt().setRefreshTokenExpirationDays(7);
        appProperties.getJwt().setAccessTokenExpirationMinutes(15);
        appProperties.getFrontend().setBaseUrl("http://localhost:4200");
        appProperties.getAuth().setPasswordResetTokenExpirationMinutes(30);
        appProperties.getAuth().setEmailVerificationTokenExpirationMinutes(1440);

        authService = new AuthService(
                authenticationManager,
                appUserRepository,
                refreshTokenRepository,
                passwordResetTokenRepository,
                jwtService,
                appProperties,
                passwordEncoder,
                mailService,
                emailVerificationService
        );

        user = new AppUser();
        user.setId(1L);
        user.setEmail("admin@local.dev");
        user.setFirstName("Admin");
        user.setLastName("Local");
        user.setRole("ADMIN");
        user.setPasswordHash("encoded-password");
        user.setEnabled(true);
        user.setEmailVerified(true);
    }

    @Test
    void loginIssuesNewTokensAndRevokesExistingOnes() {
        RefreshToken existingToken = new RefreshToken();
        existingToken.setRevoked(false);

        when(appUserRepository.findByEmailIgnoreCase("admin@local.dev")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByUserAndRevokedFalse(user)).thenReturn(List.of(existingToken));
        when(jwtService.generateAccessToken(user)).thenReturn("generated-access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.login(new LoginRequest("admin@local.dev", "Admin123!"));

        assertEquals("generated-access-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals("admin@local.dev", response.user().email());
        assertFalse(response.refreshToken().isBlank());
        assertTrue(existingToken.isRevoked());

        ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        assertSame(user, refreshTokenCaptor.getValue().getUser());
        assertFalse(refreshTokenCaptor.getValue().isRevoked());
        assertNotNull(refreshTokenCaptor.getValue().getExpiresAt());
    }

    @Test
    void loginThrowsInvalidCredentialsWhenAuthenticationFails() {
        doThrow(new BadCredentialsException("bad credentials"))
                .when(authenticationManager)
                .authenticate(any());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("admin@local.dev", "wrong"))
        );

        assertEquals("Invalid email or password", exception.getMessage());
    }

    @Test
    void loginRejectsUnverifiedUser() {
        user.setEmailVerified(false);
        when(appUserRepository.findByEmailIgnoreCase("admin@local.dev")).thenReturn(Optional.of(user));

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("admin@local.dev", "Admin123!"))
        );

        assertEquals("Email address is not verified", exception.getMessage());
    }

    @Test
    void registerCreatesStandardUserAndSendsVerification() {
        when(appUserRepository.findByEmailIgnoreCase("new.user@local.dev")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser savedUser = invocation.getArgument(0);
            savedUser.setId(42L);
            return savedUser;
        });

        authService.register(new RegisterRequest("new.user@local.dev", "New", "User", "Password123!"));

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(userCaptor.capture());
        assertEquals("new.user@local.dev", userCaptor.getValue().getEmail());
        assertEquals("New", userCaptor.getValue().getFirstName());
        assertEquals("User", userCaptor.getValue().getLastName());
        assertEquals("encoded-password", userCaptor.getValue().getPasswordHash());
        assertEquals("USER", userCaptor.getValue().getRole());
        assertTrue(userCaptor.getValue().isEnabled());
        assertFalse(userCaptor.getValue().isEmailVerified());
        verify(emailVerificationService).sendVerificationEmail(userCaptor.getValue());
    }

    @Test
    void refreshRejectsExpiredTokens() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("expired-token");
        refreshToken.setUser(user);
        refreshToken.setRevoked(false);
        refreshToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(refreshToken));

        InvalidRefreshTokenException exception = assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh("expired-token")
        );

        assertEquals("Refresh token has expired or has been revoked", exception.getMessage());
    }

    @Test
    void getCurrentUserReturnsMappedProfile() {
        when(authentication.getName()).thenReturn("admin@local.dev");
        when(appUserRepository.findByEmailIgnoreCase("admin@local.dev")).thenReturn(Optional.of(user));

        var response = authService.getCurrentUser(authentication);

        assertEquals(1L, response.id());
        assertEquals("ADMIN", response.role());
        assertTrue(response.emailVerified());
    }

    @Test
    void forgotPasswordCreatesTokenAndSendsMailForEnabledUser() {
        when(appUserRepository.findByEmailIgnoreCase("admin@local.dev")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.forgotPassword(new ForgotPasswordRequest("admin@local.dev"));

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        assertSame(user, tokenCaptor.getValue().getUser());
        assertNotNull(tokenCaptor.getValue().getToken());
        assertTrue(tokenCaptor.getValue().getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(29)));

        ArgumentCaptor<MailMessage> messageCaptor = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailService).send(messageCaptor.capture());
        assertEquals("admin@local.dev", messageCaptor.getValue().to());
        assertTrue(messageCaptor.getValue().body().contains("/reset-password?token="));
    }

    @Test
    void forgotPasswordDoesNothingWhenUserDoesNotExist() {
        when(appUserRepository.findByEmailIgnoreCase("unknown@local.dev")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest("unknown@local.dev"));

        verify(passwordResetTokenRepository, org.mockito.Mockito.never()).save(any());
        verify(mailService, org.mockito.Mockito.never()).send(any());
    }

    @Test
    void resendVerificationDelegatesToVerificationService() {
        authService.resendVerification(new ResendVerificationRequest("admin@local.dev"));

        verify(emailVerificationService).resendVerification("admin@local.dev");
    }

    @Test
    void verifyEmailDelegatesToVerificationService() {
        authService.verifyEmail(new VerifyEmailRequest("verification-token"));

        verify(emailVerificationService).verifyEmail("verification-token");
    }

    @Test
    void resetPasswordUpdatesPasswordAndRevokesRefreshTokens() {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken("valid-token");
        resetToken.setUser(user);
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(15));

        RefreshToken activeToken = new RefreshToken();
        activeToken.setRevoked(false);

        when(passwordResetTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("encoded-new-password");
        when(refreshTokenRepository.findByUserAndRevokedFalse(user)).thenReturn(List.of(activeToken));

        authService.resetPassword(new ResetPasswordRequest("valid-token", "NewPassword123!"));

        assertEquals("encoded-new-password", user.getPasswordHash());
        assertNotNull(resetToken.getUsedAt());
        assertTrue(activeToken.isRevoked());
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken("expired-token");
        resetToken.setUser(user);
        resetToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(resetToken));

        InvalidPasswordResetTokenException exception = assertThrows(
                InvalidPasswordResetTokenException.class,
                () -> authService.resetPassword(new ResetPasswordRequest("expired-token", "NewPassword123!"))
        );

        assertEquals("Password reset token has expired or has already been used", exception.getMessage());
    }
}
