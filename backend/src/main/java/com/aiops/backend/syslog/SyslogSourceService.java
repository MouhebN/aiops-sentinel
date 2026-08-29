package com.aiops.backend.syslog;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SyslogSourceService {

    private final SyslogSourceRepository repository;
    private final AuditLogService auditLogService;

    public SyslogSourceService(SyslogSourceRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<SyslogSourceResponse> list() {
        return repository.findAllByOrderByNameAsc()
                .stream()
                .map(SyslogSourceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SyslogSourceResponse> listEnabled() {
        return repository.findByEnabledTrueOrderByNameAsc()
                .stream()
                .map(SyslogSourceResponse::from)
                .toList();
    }

    @Transactional
    public SyslogSourceResponse create(SaveSyslogSourceRequest request) {
        SyslogSource saved = repository.save(new SyslogSource(request));
        auditLogService.log(
                AuditAction.SYSLOG_SOURCE_CREATED,
                "SYSLOG_SOURCE",
                saved.getId().toString(),
                "Created syslog source " + saved.getName()
        );
        return SyslogSourceResponse.from(saved);
    }

    @Transactional
    public SyslogSourceResponse update(Long id, SaveSyslogSourceRequest request) {
        SyslogSource source = find(id);
        source.update(request);
        auditLogService.log(
                AuditAction.SYSLOG_SOURCE_UPDATED,
                "SYSLOG_SOURCE",
                source.getId().toString(),
                "Updated syslog source " + source.getName()
        );
        return SyslogSourceResponse.from(source);
    }

    @Transactional
    public void delete(Long id) {
        SyslogSource source = find(id);
        auditLogService.log(
                AuditAction.SYSLOG_SOURCE_DELETED,
                "SYSLOG_SOURCE",
                source.getId().toString(),
                "Deleted syslog source " + source.getName()
        );
        repository.delete(source);
    }

    private SyslogSource find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Syslog source not found"));
    }
}
