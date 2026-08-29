package com.aiops.backend.auth;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final AuditLogService auditLogService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AppUserDetailsService userDetailsService,
            AuditLogService auditLogService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException ex) {
            auditLogService.log(AuditAction.LOGIN_FAILED, "AUTH", request.email(), "Invalid login attempt");
            throw ex;
        }

        AppUser user = userDetailsService.loadUserByUsername(request.email());
        auditLogService.logAsUser(
                user.getEmail(),
                user.getRole(),
                AuditAction.LOGIN_SUCCESS,
                "AUTH",
                user.getId() == null ? null : user.getId().toString(),
                "User logged in successfully"
        );
        return new AuthResponse(jwtService.generateToken(user), CurrentUserResponse.from(user));
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AppUser user) {
        return CurrentUserResponse.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AppUser user) {
        if (user == null) {
            return;
        }
        try {
            auditLogService.logAsUser(
                    user.getEmail(),
                    user.getRole(),
                    AuditAction.LOGOUT,
                    "AUTH",
                    user.getId() == null ? null : user.getId().toString(),
                    "User logged out"
            );
        } catch (Exception exception) {
            log.warn("Audit logging failed during logout for {}", user.getEmail(), exception);
        }
    }
}
