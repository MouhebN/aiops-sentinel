package com.aiops.backend.auth;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserService(
            AppUserRepository repository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(int page, int size, String search, Role role, Boolean enabled) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "fullName"));
        return repository.findAll(filter(search, role, enabled), pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        ensureEmailAvailable(normalizedEmail, null);

        AppUser user = new AppUser();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEnabled(request.enabled());

        AppUser saved = repository.save(user);
        auditLogService.log(
                AuditAction.USER_CREATED,
                "USER",
                saved.getId().toString(),
                "Created user " + saved.getEmail() + " with role " + saved.getRole()
        );
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        AppUser user = find(id);
        Role previousRole = user.getRole();
        boolean previousEnabled = user.isEnabled();

        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setEnabled(request.enabled());

        if (!request.enabled()) {
            preventDisablingCurrentUser(user);
        }

        auditLogService.log(
                AuditAction.USER_UPDATED,
                "USER",
                user.getId().toString(),
                "Updated user " + user.getEmail()
        );
        if (previousRole != user.getRole()) {
            auditLogService.log(
                    AuditAction.USER_ROLE_CHANGED,
                    "USER",
                    user.getId().toString(),
                    "Changed role for " + user.getEmail() + " from " + previousRole + " to " + user.getRole()
            );
        }
        if (previousEnabled != user.isEnabled()) {
            auditLogService.log(
                    user.isEnabled() ? AuditAction.USER_ENABLED : AuditAction.USER_DISABLED,
                    "USER",
                    user.getId().toString(),
                    (user.isEnabled() ? "Enabled user " : "Disabled user ") + user.getEmail()
            );
        }

        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse enable(Long id) {
        AppUser user = find(id);
        user.setEnabled(true);
        auditLogService.log(
                AuditAction.USER_ENABLED,
                "USER",
                user.getId().toString(),
                "Enabled user " + user.getEmail()
        );
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse disable(Long id) {
        AppUser user = find(id);
        preventDisablingCurrentUser(user);
        user.setEnabled(false);
        auditLogService.log(
                AuditAction.USER_DISABLED,
                "USER",
                user.getId().toString(),
                "Disabled user " + user.getEmail()
        );
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse resetPassword(Long id, ResetPasswordRequest request) {
        AppUser user = find(id);
        user.setPassword(passwordEncoder.encode(request.password()));
        auditLogService.log(
                AuditAction.USER_PASSWORD_RESET,
                "USER",
                user.getId().toString(),
                "Reset password for " + user.getEmail()
        );
        return UserResponse.from(user);
    }

    private AppUser find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void ensureEmailAvailable(String email, Long currentUserId) {
        repository.findByEmailIgnoreCase(email).ifPresent(existing -> {
            if (currentUserId == null || !existing.getId().equals(currentUserId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
            }
        });
    }

    private void preventDisablingCurrentUser(AppUser targetUser) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUser currentUser
                && currentUser.getId() != null
                && currentUser.getId().equals(targetUser.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot disable your own account");
        }
    }

    private Specification<AppUser> filter(String search, Role role, Boolean enabled) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("fullName")), like),
                        builder.like(builder.lower(root.get("email")), like)
                ));
            }
            if (role != null) {
                predicates.add(builder.equal(root.get("role"), role));
            }
            if (enabled != null) {
                predicates.add(builder.equal(root.get("enabled"), enabled));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
