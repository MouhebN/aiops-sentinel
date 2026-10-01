package com.aiops.backend.audit;

import com.aiops.backend.auth.AppUser;
import com.aiops.backend.auth.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

@Service
public class AuditLogService {

    private static final String SYSTEM_USERNAME = "SYSTEM";
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;
    private final AuditLogService self;
    private final boolean trustedProxyEnabled;

    public AuditLogService(
            AuditLogRepository repository,
            @Lazy AuditLogService self,
            @Value("${app.audit.trusted-proxy-enabled:false}") boolean trustedProxyEnabled
    ) {
        this.repository = repository;
        this.self = self;
        this.trustedProxyEnabled = trustedProxyEnabled;
    }

    @Transactional
    public void log(AuditAction action, String targetType, String targetId, String details) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            persist(SYSTEM_USERNAME, null, action, targetType, targetId, details);
            return;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AppUser user) {
            persist(user.getEmail(), user.getRole(), action, targetType, targetId, details);
            return;
        }

        persist(authentication.getName(), null, action, targetType, targetId, details);
    }

    @Transactional
    public void logAsUser(String username, Role role, AuditAction action, String targetType, String targetId, String details) {
        persist(username, role, action, targetType, targetId, details);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(
            int page,
            int size,
            String username,
            AuditAction action,
            Instant from,
            Instant to
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(filter(username, action, from, to), pageable)
                .map(AuditLogResponse::from);
    }

    public void logClientAction(AuditAction action, String targetType, String targetId, String details) {
        if (action != AuditAction.AI_ANALYSIS_REQUESTED && action != AuditAction.REPORT_EXPORTED) {
            throw new IllegalArgumentException("Client audit action is not allowed: " + action);
        }
        self.log(action, targetType, targetId, details);
    }

    public void logWarning(String message, Exception exception) {
        log.warn(message, exception);
    }

    private void persist(String username, Role role, AuditAction action, String targetType, String targetId, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUsername(username == null || username.isBlank() ? SYSTEM_USERNAME : username);
        auditLog.setUserRole(role);
        auditLog.setAction(action);
        auditLog.setTargetType(clean(targetType));
        auditLog.setTargetId(clean(targetId));
        auditLog.setDetails(clean(details));
        auditLog.setIpAddress(resolveIpAddress());
        repository.save(auditLog);
    }

    private String resolveIpAddress() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }

        HttpServletRequest request = servletAttributes.getRequest();
        if (trustedProxyEnabled) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return clean(request.getRemoteAddr());
    }

    private Specification<AuditLog> filter(
            String username,
            AuditAction action,
            Instant from,
            Instant to
    ) {
        return (root, query, builder) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (username != null && !username.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("username")),
                        "%" + username.trim().toLowerCase() + "%"
                ));
            }
            if (action != null) {
                predicates.add(builder.equal(root.get("action"), action));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
