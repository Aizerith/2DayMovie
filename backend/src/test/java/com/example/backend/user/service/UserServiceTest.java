package com.example.backend.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.repository.AppUserRepository;
import com.example.backend.auth.service.EmailVerificationService;
import com.example.backend.user.dto.CreateUserRequest;
import com.example.backend.user.dto.UpdateUserRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private UserService userService;

    @Test
    void createPersistsUserAndSendsVerificationEmail() {
        when(passwordEncoder.encode("Secret123!")).thenReturn("encoded-password");
        when(appUserRepository.findByEmailIgnoreCase("user@local.dev")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser savedUser = invocation.getArgument(0);
            savedUser.setId(10L);
            return savedUser;
        });

        var response = userService.create(
                new CreateUserRequest("user@local.dev", "Jane", "Doe", "Secret123!", "USER", true)
        );

        assertEquals(10L, response.id());
        assertEquals("user@local.dev", response.email());
        assertEquals("USER", response.role());
        assertFalse(response.emailVerified());
        verify(emailVerificationService).sendVerificationEmail(any(AppUser.class));
    }

    @Test
    void updateResetsVerificationAndSendsEmailWhenAddressChanges() {
        AppUser user = buildUser(20L, "old@local.dev", true, true);

        when(appUserRepository.findById(20L)).thenReturn(Optional.of(user));
        when(appUserRepository.findByEmailIgnoreCase("new@local.dev")).thenReturn(Optional.empty());

        var response = userService.update(
                20L,
                new UpdateUserRequest("new@local.dev", "Jane", "Doe", "", "USER", true)
        );

        assertEquals("new@local.dev", response.email());
        assertFalse(response.emailVerified());
        verify(emailVerificationService).sendVerificationEmail(user);
    }

    @Test
    void updateSendsVerificationWhenDisabledUnverifiedUserGetsReenabled() {
        AppUser user = buildUser(21L, "user@local.dev", false, false);

        when(appUserRepository.findById(21L)).thenReturn(Optional.of(user));
        when(appUserRepository.findByEmailIgnoreCase("user@local.dev")).thenReturn(Optional.of(user));

        var response = userService.update(
                21L,
                new UpdateUserRequest("user@local.dev", "Jane", "Doe", "", "USER", true)
        );

        assertEquals("user@local.dev", response.email());
        assertFalse(response.emailVerified());
        verify(emailVerificationService).sendVerificationEmail(user);
    }

    @Test
    void updateRejectsWeakPassword() {
        AppUser user = buildUser(22L, "user@local.dev", true, true);

        when(appUserRepository.findById(22L)).thenReturn(Optional.of(user));
        when(appUserRepository.findByEmailIgnoreCase("user@local.dev")).thenReturn(Optional.of(user));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.update(
                        22L,
                        new UpdateUserRequest("user@local.dev", "Jane", "Doe", "weak", "ADMIN", true)
                )
        );

        assertEquals(
                "Password must contain upper, lower, digit and special character without spaces",
                exception.getMessage()
        );
    }

    private AppUser buildUser(Long id, String email, boolean enabled, boolean emailVerified) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setRole("USER");
        user.setEnabled(enabled);
        user.setEmailVerified(emailVerified);
        user.setPasswordHash("encoded-password");
        return user;
    }
}
