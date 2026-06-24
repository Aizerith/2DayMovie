package com.example.backend.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.backend.auth.dto.AuthResponse;
import com.example.backend.auth.dto.ForgotPasswordRequest;
import com.example.backend.auth.dto.LoginRequest;
import com.example.backend.auth.dto.RegisterRequest;
import com.example.backend.auth.dto.RefreshTokenRequest;
import com.example.backend.auth.dto.ResendVerificationRequest;
import com.example.backend.auth.dto.ResetPasswordRequest;
import com.example.backend.auth.dto.UserResponse;
import com.example.backend.auth.dto.VerifyEmailRequest;
import com.example.backend.auth.security.JwtAuthenticationFilter;
import com.example.backend.auth.service.AuthService;
import com.example.backend.common.exception.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void loginReturnsTokens() throws Exception {
        AuthResponse response = new AuthResponse(
                "access-token",
                "refresh-token",
                "Bearer",
                900L,
                new UserResponse(1L, "admin@local.dev", "Admin", "Local", "ADMIN", true)
        );

        when(authService.login(new LoginRequest("admin@local.dev", "Admin123!"))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin@local.dev", "Admin123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void registerReturnsNoContent() throws Exception {
        RegisterRequest request = new RegisterRequest("new.user@local.dev", "New", "User", "Password123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).register(request);
    }

    @Test
    void refreshReturnsNewTokens() throws Exception {
        AuthResponse response = new AuthResponse(
                "new-access-token",
                "new-refresh-token",
                "Bearer",
                900L,
                new UserResponse(1L, "admin@local.dev", "Admin", "Local", "ADMIN", true)
        );

        when(authService.refresh("refresh-token")).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    void logoutReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("refresh-token"))))
                .andExpect(status().isNoContent());

        verify(authService).logout("refresh-token");
    }

    @Test
    void forgotPasswordReturnsNoContent() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("admin@local.dev");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).forgotPassword(request);
    }

    @Test
    void resendVerificationReturnsNoContent() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest("admin@local.dev");

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).resendVerification(request);
    }

    @Test
    void resetPasswordReturnsNoContent() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest("valid-token", "NewPassword123!");

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).resetPassword(request);
    }

    @Test
    void verifyEmailReturnsNoContent() throws Exception {
        VerifyEmailRequest request = new VerifyEmailRequest("verification-token");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).verifyEmail(request);
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        var auth = UsernamePasswordAuthenticationToken.authenticated("admin@local.dev", null, java.util.List.of());
        when(authService.getCurrentUser(any())).thenReturn(new UserResponse(1L, "admin@local.dev", "Admin", "Local", "ADMIN", true));

        mockMvc.perform(get("/api/auth/me").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@local.dev"));
    }

    @Test
    void loginRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void registerRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"firstName\":\"\",\"lastName\":\"\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void forgotPasswordRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void resendVerificationRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void resetPasswordRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void verifyEmailRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(APPLICATION_JSON)
                        .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
