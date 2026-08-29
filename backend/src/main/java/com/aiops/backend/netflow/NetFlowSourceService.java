package com.aiops.backend.netflow;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class NetFlowSourceService {
    private static final String DEFAULT_SOURCE_NAME = "Default nfdump collector";
    private static final String DOCKER_DEFAULT_DATA_DIR = "/data/netflow";

    private final NetFlowSourceRepository repository;
    private final NetFlowProperties properties;
    private final AuditLogService auditLogService;

    public NetFlowSourceService(
            NetFlowSourceRepository repository,
            NetFlowProperties properties,
            AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<NetFlowSourceResponse> list() {
        return repository.findAll().stream().map(NetFlowSourceResponse::from).toList();
    }

    @Transactional
    public NetFlowSourceResponse create(SaveNetFlowSourceRequest request) {
        NetFlowSource source = new NetFlowSource(
                request.name().trim(),
                request.dataDirectory().trim(),
                request.collectorPort(),
                request.enabled() == null || request.enabled()
        );
        source.update(request);
        NetFlowSource saved = repository.save(source);
        auditLogService.log(
                AuditAction.NETFLOW_SOURCE_CREATED,
                "NETFLOW_SOURCE",
                saved.getId().toString(),
                "Created NetFlow source " + saved.getName()
        );
        return NetFlowSourceResponse.from(saved);
    }

    @Transactional
    public NetFlowSourceResponse update(Long id, SaveNetFlowSourceRequest request) {
        NetFlowSource source = find(id);
        source.update(request);
        auditLogService.log(
                AuditAction.NETFLOW_SOURCE_UPDATED,
                "NETFLOW_SOURCE",
                source.getId().toString(),
                "Updated NetFlow source " + source.getName()
        );
        return NetFlowSourceResponse.from(source);
    }

    @Transactional
    public NetFlowSourceResponse enable(Long id) {
        NetFlowSource source = find(id);
        source.setEnabled(true);
        auditLogService.log(
                AuditAction.NETFLOW_SOURCE_UPDATED,
                "NETFLOW_SOURCE",
                source.getId().toString(),
                "Enabled NetFlow source " + source.getName()
        );
        return NetFlowSourceResponse.from(source);
    }

    @Transactional
    public NetFlowSourceResponse disable(Long id) {
        NetFlowSource source = find(id);
        source.setEnabled(false);
        auditLogService.log(
                AuditAction.NETFLOW_SOURCE_UPDATED,
                "NETFLOW_SOURCE",
                source.getId().toString(),
                "Disabled NetFlow source " + source.getName()
        );
        return NetFlowSourceResponse.from(source);
    }

    @Transactional
    public void delete(Long id) {
        NetFlowSource source = find(id);
        repository.delete(source);
        auditLogService.log(
                AuditAction.NETFLOW_SOURCE_UPDATED,
                "NETFLOW_SOURCE",
                id.toString(),
                "Deleted NetFlow source " + source.getName()
        );
    }

    public NetFlowSource find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NetFlow source not found"));
    }

    @Transactional
    public void ensureDefaultSource() {
        List<NetFlowSource> sources = repository.findAll();
        if (!sources.isEmpty()) {
            if (sources.size() == 1
                    && DEFAULT_SOURCE_NAME.equals(sources.getFirst().getName())
                    && DOCKER_DEFAULT_DATA_DIR.equals(sources.getFirst().getDataDirectory())
                    && !DOCKER_DEFAULT_DATA_DIR.equals(properties.getDataDir())) {
                sources.getFirst().applyConfiguredDefaults(
                        properties.getDataDir(),
                        properties.getCollectorPort(),
                        properties.isEnabled()
                );
            }
            return;
        }
        NetFlowSource source = new NetFlowSource(
                DEFAULT_SOURCE_NAME,
                properties.getDataDir(),
                properties.getCollectorPort(),
                properties.isEnabled()
        );
        repository.save(source);
    }
}
