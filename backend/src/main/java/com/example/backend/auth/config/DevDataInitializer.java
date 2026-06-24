package com.example.backend.auth.config;

import com.example.backend.auth.entity.AppUser;
import com.example.backend.auth.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class DevDataInitializer {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    @Profile("dev")
    CommandLineRunner seedDevUser() {
        return args -> {
            seedUser("admin@local.dev", "Admin", "Local", "Admin123!", "ADMIN");
            seedUser("manager@local.dev", "Manager", "Local", "Manager123!", "MANAGER");
        };
    }

    private void seedUser(String email, String firstName, String lastName, String password, String role) {
        appUserRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    AppUser user = new AppUser();
                    user.setEmail(email);
                    user.setFirstName(firstName);
                    user.setLastName(lastName);
                    user.setPasswordHash(passwordEncoder.encode(password));
                    user.setRole(role);
                    user.setEnabled(true);
                    user.setEmailVerified(true);
                    return appUserRepository.save(user);
                });
    }
}
