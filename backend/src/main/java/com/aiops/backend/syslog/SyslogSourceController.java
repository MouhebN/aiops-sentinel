package com.aiops.backend.syslog;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/syslog-sources")
public class SyslogSourceController {

    private final SyslogSourceService service;

    public SyslogSourceController(SyslogSourceService service) {
        this.service = service;
    }

    @GetMapping
    public List<SyslogSourceResponse> list() {
        return service.list();
    }

    @GetMapping("/enabled")
    public List<SyslogSourceResponse> listEnabled() {
        return service.listEnabled();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SyslogSourceResponse create(@Valid @RequestBody SaveSyslogSourceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SyslogSourceResponse update(
            @PathVariable Long id,
            @Valid @RequestBody SaveSyslogSourceRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
