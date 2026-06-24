package com.example.backend.auth.controller;

import com.example.backend.auth.dto.AuthResponse;
import com.example.backend.auth.dto.ForgotPasswordRequest;
import com.example.backend.auth.dto.LoginRequest;
import com.example.backend.auth.dto.RegisterRequest;
import com.example.backend.auth.dto.ResendVerificationRequest;
import com.example.backend.auth.dto.RefreshTokenRequest;
import com.example.backend.auth.dto.ResetPasswordRequest;
import com.example.backend.auth.dto.UserResponse;
import com.example.backend.auth.dto.VerifyEmailRequest;
import com.example.backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentification JWT, session courante et renouvellement des tokens")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "S inscrire", description = "Cree un compte public de role USER puis envoie un email de verification.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Inscription prise en compte"),
            @ApiResponse(responseCode = "400", description = "Payload invalide"),
            @ApiResponse(responseCode = "409", description = "Email deja utilise")
    })
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    @Operation(summary = "Se connecter", description = "Authentifie un utilisateur et retourne un access token JWT ainsi qu un refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Connexion reussie"),
            @ApiResponse(responseCode = "401", description = "Identifiants invalides")
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rafraichir la session", description = "Genere un nouveau couple access token / refresh token a partir d un refresh token valide.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session rafraichie"),
            @ApiResponse(responseCode = "401", description = "Refresh token invalide ou expire")
    })
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Se deconnecter", description = "Invalide le refresh token envoye et termine la session.")
    @ApiResponse(responseCode = "204", description = "Deconnexion reussie")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Demander un reset password", description = "Genere un token de reinitialisation et simule l envoi d un email si le compte existe.")
    @ApiResponse(responseCode = "204", description = "Demande prise en compte")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Renvoyer l email de verification", description = "Renvoie un email de verification si le compte existe, est actif et non verifie.")
    @ApiResponse(responseCode = "204", description = "Demande prise en compte")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verifier une adresse email", description = "Valide un token de verification d email puis marque le compte comme verifie.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Email verifie"),
            @ApiResponse(responseCode = "400", description = "Token invalide ou expire")
    })
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reinitialiser le mot de passe", description = "Valide un token de reset password puis remplace le mot de passe du compte.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Mot de passe reinitialise"),
            @ApiResponse(responseCode = "400", description = "Token invalide ou expire")
    })
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Recuperer l utilisateur courant", description = "Retourne le profil associe au JWT courant.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil courant retourne"),
            @ApiResponse(responseCode = "401", description = "JWT manquant ou invalide")
    })
    public UserResponse me(Authentication authentication) {
        return authService.getCurrentUser(authentication);
    }
}
