package com.aiops.backend.auth;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthDataSeeder {

    @Bean
    @Order(1)
    public ApplicationRunner seedUsers(AppUserRepository repository, PasswordEncoder passwordEncoder) {
        return args -> {
            seedUser(repository, passwordEncoder, "Bank Chief", "admin@aiops.local", "admin123", Role.ADMIN);
            seedUser(repository, passwordEncoder, "Supervision Operator", "operator@aiops.local", "operator123", Role.OPERATOR);
            seedUser(repository, passwordEncoder, "Read Only Viewer", "viewer@aiops.local", "viewer123", Role.VIEWER);
        };
    }

    private void seedUser(
            AppUserRepository repository,
            PasswordEncoder passwordEncoder,
            String fullName,
            String email,
            String rawPassword,
            Role role
    ) {
        AppUser user = repository.findByEmailIgnoreCase(email).orElseGet(AppUser::new);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);
        repository.save(user);
    }
}
