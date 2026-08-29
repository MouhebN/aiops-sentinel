package com.aiops.backend.netflow;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/netflow/sources")
public class NetFlowSourceController {

    private final NetFlowSourceService service;

    public NetFlowSourceController(NetFlowSourceService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OPERATOR','ROLE_VIEWER')")
    public List<NetFlowSourceResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OPERATOR')")
    public NetFlowSourceResponse create(@Valid @RequestBody SaveNetFlowSourceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OPERATOR')")
    public NetFlowSourceResponse update(@PathVariable Long id, @Valid @RequestBody SaveNetFlowSourceRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/enable")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OPERATOR')")
    public NetFlowSourceResponse enable(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean enabled) {
        return enabled ? service.enable(id) : service.disable(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OPERATOR')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
