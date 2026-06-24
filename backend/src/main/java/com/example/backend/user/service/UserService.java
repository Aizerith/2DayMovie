package com.example.backend.user.service;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.service.EmailVerificationService;
import com.example.backend.auth.repository.AppUserRepository;
import com.example.backend.common.dto.PagedResponse;
import com.example.backend.user.dto.CreateUserRequest;
import com.example.backend.user.dto.UpdateUserRequest;
import com.example.backend.user.dto.AssignableUserResponse;
import com.example.backend.user.dto.UserAdminResponse;
import com.example.backend.user.exception.UserConflictException;
import com.example.backend.user.exception.UserNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    @Transactional(readOnly = true)
    public PagedResponse<UserAdminResponse> findPage(Pageable pageable) {
        return PagedResponse.from(appUserRepository.findAll(pageable).map(this::toResponse));
    }

    public List<UserAdminResponse> findAll() {
        return appUserRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AssignableUserResponse> findAssignableUsers() {
        return appUserRepository.findAllByEnabledTrueOrderByFirstNameAscLastNameAsc().stream()
                .map(user -> new AssignableUserResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getFirstName() + " " + user.getLastName(),
                        user.getRole()
                ))
                .toList();
    }

    @Transactional
    public UserAdminResponse create(CreateUserRequest request) {
        ensureEmailAvailable(request.email(), null);

        AppUser user = new AppUser();
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        user.setEmailVerified(false);

        AppUser savedUser = appUserRepository.save(user);
        emailVerificationService.sendVerificationEmail(savedUser);

        return toResponse(savedUser);
    }

    @Transactional
    public UserAdminResponse update(Long id, UpdateUserRequest request) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        ensureEmailAvailable(request.email(), id);
        boolean emailChanged = !user.getEmail().equalsIgnoreCase(request.email());
        boolean wasEnabled = user.isEnabled();
        boolean shouldSendVerification = false;

        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setRole(request.role());
        user.setEnabled(request.enabled());

        if (emailChanged) {
            user.setEmailVerified(false);
            shouldSendVerification = user.isEnabled();
        } else if (!wasEnabled && user.isEnabled() && !user.isEmailVerified()) {
            shouldSendVerification = true;
        }

        if (request.password() != null && !request.password().isBlank()) {
            validatePassword(request.password());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        if (shouldSendVerification) {
            emailVerificationService.sendVerificationEmail(user);
        }

        return toResponse(user);
    }

    @Transactional
    public void delete(Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        appUserRepository.delete(user);
    }

    private void ensureEmailAvailable(String email, Long currentUserId) {
        appUserRepository.findByEmailIgnoreCase(email).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(currentUserId)) {
                throw new UserConflictException("A user with this email already exists");
            }
        });
    }

    private void validatePassword(String password) {
        if (!password.matches(com.example.backend.common.validation.ValidationPatterns.PASSWORD)) {
            throw new IllegalArgumentException("Password must contain upper, lower, digit and special character without spaces");
        }
    }

    private UserAdminResponse toResponse(AppUser user) {
        return new UserAdminResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isEnabled(),
                user.isEmailVerified()
        );
    }
}
