package com.aiops.backend.pcap;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import com.aiops.backend.component.ComponentIdentityResolver;
import com.aiops.backend.component.ComponentIdentityResolver.ResolvedComponentIp;
import com.aiops.backend.incident.Incident;
import com.aiops.backend.incident.IncidentComponentBinder;
import com.aiops.backend.incident.IncidentComponentBinder.TrafficEndpoints;
import com.aiops.backend.incident.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

@Service
public class PacketCaptureJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketCaptureJobService.class);
    private static final List<PacketCaptureJobStatus> ACTIVE_STATUSES = List.of(
            PacketCaptureJobStatus.PENDING,
            PacketCaptureJobStatus.RUNNING
    );

    private static final List<CaptureTrigger> AUTOMATIC_TRIGGERS = List.of(
            CaptureTrigger.AUTO_INCIDENT,
            CaptureTrigger.AUTO_ROLLING
    );

    private final IncidentRepository incidentRepository;
    private final IncidentComponentBinder incidentComponentBinder;
    private final ComponentIdentityResolver identityResolver;
    private final PacketCaptureJobRepository jobRepository;
    private final PacketCaptureProvider captureProvider;
    private final PacketCaptureAnalysisService analysisService;
    private final PcapCaptureProperties properties;
    private final AuditLogService auditLogService;
    private final Executor packetCaptureExecutor;

    public PacketCaptureJobService(
            IncidentRepository incidentRepository,
            IncidentComponentBinder incidentComponentBinder,
            ComponentIdentityResolver identityResolver,
            PacketCaptureJobRepository jobRepository,
            PacketCaptureProvider captureProvider,
            PacketCaptureAnalysisService analysisService,
            PcapCaptureProperties properties,
            AuditLogService auditLogService,
            @Qualifier("packetCaptureExecutor") Executor packetCaptureExecutor
    ) {
        this.incidentRepository = incidentRepository;
        this.incidentComponentBinder = incidentComponentBinder;
        this.identityResolver = identityResolver;
        this.jobRepository = jobRepository;
        this.captureProvider = captureProvider;
        this.analysisService = analysisService;
        this.properties = properties;
        this.auditLogService = auditLogService;
        this.packetCaptureExecutor = packetCaptureExecutor;
    }

    @Transactional(readOnly = true)
    public PacketCapturePreviewResponse preview(Long incidentId) {
        Incident incident = loadIncident(incidentId);
        CapturePlan plan = planFor(incident, null);
        boolean allowed = !incident.getStatus().isClosed();
        return new PacketCapturePreviewResponse(
                plan.sourceIp(),
                plan.destinationIp(),
                plan.destinationComponentName(),
                plan.matchedInterfaceName(),
                plan.matchedInterfaceIp(),
                plan.pointId(),
                plan.displayName(),
                plan.interfaceName(),
                properties.getCapture().getDefaultDurationSeconds(),
                allowed,
                allowed
                        ? null
                        : "Live capture is disabled for resolved incidents. Historical packet captures remain available."
        );
    }

    @Transactional
    public PacketCaptureJobResponse start(Long incidentId, StartPacketCaptureRequest request) {
        Long jobId = createAndStart(
                incidentId,
                request == null ? new StartPacketCaptureRequest(null, null) : request,
                CaptureTrigger.MANUAL,
                true
        );
        scheduleCompletion(jobId);
        return get(incidentId, jobId);
    }

    @Transactional
    public PacketCaptureJobResponse startAutomatic(Long incidentId, AutoCapturePolicy policy) {
        if (!policy.shouldCapture(loadIncident(incidentId))) {
            return null;
        }
        if (hasAutomaticJob(incidentId)) {
            return jobRepository.findFirstByIncidentIdAndTriggerInOrderByCreatedAtDesc(incidentId, AUTOMATIC_TRIGGERS)
                    .map(this::toResponse)
                    .orElse(null);
        }
        CaptureTrigger trigger = policy.triggerType();
        StartPacketCaptureRequest request = new StartPacketCaptureRequest(
                policy.postTriggerSeconds(),
                properties.getCapture().getDefaultPoint()
        );
        Long jobId = createAndStart(incidentId, request, trigger, false);
        if (jobId == null) {
            return null;
        }
        scheduleCompletion(jobId);
        return get(incidentId, jobId);
    }

    public boolean hasAutomaticJob(Long incidentId) {
        return jobRepository.existsByIncidentIdAndTriggerIn(incidentId, AUTOMATIC_TRIGGERS);
    }

    private void scheduleCompletion(Long jobId) {
        if (jobId == null) {
            return;
        }
        if (properties.getCapture().isAsync()) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        packetCaptureExecutor.execute(() -> completeCapture(jobId));
                    }
                });
            } else {
                packetCaptureExecutor.execute(() -> completeCapture(jobId));
            }
        } else {
            completeCapture(jobId);
        }
    }

    @Transactional
    public Long createAndStart(Long incidentId, StartPacketCaptureRequest request) {
        return createAndStart(incidentId, request, CaptureTrigger.MANUAL, true);
    }

    @Transactional
    public Long createAndStart(
            Long incidentId,
            StartPacketCaptureRequest request,
            CaptureTrigger trigger,
            boolean throwOnError
    ) {
        Incident incident = loadIncident(incidentId);
        if (incident.getStatus().isClosed()) {
            if (!throwOnError) {
                LOGGER.info("Skipping auto-capture for resolved incident {}", incidentId);
                return null;
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Live capture is disabled for resolved incidents"
            );
        }
        if (trigger != CaptureTrigger.MANUAL && hasAutomaticJob(incidentId)) {
            return jobRepository.findFirstByIncidentIdAndTriggerInOrderByCreatedAtDesc(incidentId, AUTOMATIC_TRIGGERS)
                    .map(PacketCaptureJob::getId)
                    .orElse(null);
        }
        CapturePlan plan;
        try {
            plan = planFor(incident, request, trigger);
        } catch (ResponseStatusException exception) {
            if (throwOnError) {
                throw exception;
            }
            LOGGER.warn("Skipping auto-capture for incident {}: {}", incidentId, exception.getReason());
            return null;
        }
        if (jobRepository.existsByIncidentIdAndStatusIn(incidentId, ACTIVE_STATUSES)) {
            if (!throwOnError) {
                persistFailedJob(
                        incidentId,
                        plan,
                        trigger,
                        PacketCaptureFailureCode.BUSY,
                        "A capture is already running for this incident"
                );
                return null;
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A capture is already running for this incident"
            );
        }
        if (jobRepository.countByStatusIn(ACTIVE_STATUSES) >= properties.getCapture().getMaxConcurrent()) {
            if (!throwOnError) {
                persistFailedJob(
                        incidentId,
                        plan,
                        trigger,
                        PacketCaptureFailureCode.BUSY,
                        "The capture sensor is already busy"
                );
                return null;
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The capture sensor is already busy"
            );
        }

        int pre = trigger == CaptureTrigger.AUTO_ROLLING ? properties.getRolling().getPreTriggerSeconds() : 0;
        int post = trigger == CaptureTrigger.AUTO_ROLLING
                ? properties.getRolling().getPostTriggerSeconds()
                : plan.durationSeconds();
        PacketCaptureJob job;
        try {
            job = jobRepository.saveAndFlush(new PacketCaptureJob(
                    incidentId,
                    plan.providerId(),
                    plan.sourceIp(),
                    plan.destinationIp(),
                    plan.pointId(),
                    plan.displayName(),
                    plan.interfaceName(),
                    trigger == CaptureTrigger.AUTO_ROLLING ? pre + post : plan.durationSeconds(),
                    trigger,
                    pre,
                    post
            ));
        } catch (DataIntegrityViolationException exception) {
            LOGGER.info("Auto-capture job already exists for incident {} (unique constraint)", incidentId);
            return jobRepository.findFirstByIncidentIdAndTriggerInOrderByCreatedAtDesc(incidentId, AUTOMATIC_TRIGGERS)
                    .map(PacketCaptureJob::getId)
                    .orElse(null);
        }
        auditLogService.log(
                AuditAction.PACKET_CAPTURE_STARTED,
                "PACKET_CAPTURE_JOB",
                jobRef(job),
                (trigger == CaptureTrigger.MANUAL ? "Started capture" : "Automatic packet capture started")
                        + " for incident #" + incidentId + " at " + plan.displayName()
        );
        try {
            String mode = trigger == CaptureTrigger.AUTO_ROLLING ? "ROLLING_SNAPSHOT" : "ON_DEMAND";
            ProviderCaptureHandle handle = captureProvider.startCapture(new ProviderCaptureRequest(
                    post,
                    plan.interfaceName(),
                    plan.sourceIp(),
                    plan.destinationIp(),
                    plan.pointId(),
                    mode,
                    pre,
                    post
            ));
            job.markRunning(handle.providerCaptureId());
            jobRepository.saveAndFlush(job);
            return job.getId();
        } catch (CaptureProviderException exception) {
            job.fail(exception.code(), exception.getMessage());
            jobRepository.saveAndFlush(job);
            auditLogService.log(
                    AuditAction.PACKET_CAPTURE_FAILED,
                    "PACKET_CAPTURE_JOB",
                    jobRef(job),
                    exception.getMessage()
            );
            if (throwOnError) {
                throw mapped(exception);
            }
            return job.getId();
        }
    }

    private PacketCaptureJob persistFailedJob(
            Long incidentId,
            CapturePlan plan,
            CaptureTrigger trigger,
            PacketCaptureFailureCode code,
            String message
    ) {
        PacketCaptureJob job = new PacketCaptureJob(
                incidentId,
                plan.providerId(),
                plan.sourceIp(),
                plan.destinationIp(),
                plan.pointId(),
                plan.displayName(),
                plan.interfaceName(),
                plan.durationSeconds(),
                trigger,
                trigger == CaptureTrigger.AUTO_ROLLING ? properties.getRolling().getPreTriggerSeconds() : 0,
                plan.durationSeconds()
        );
        job.fail(code, message);
        PacketCaptureJob saved = jobRepository.saveAndFlush(job);
        auditLogService.log(
                AuditAction.PACKET_CAPTURE_FAILED,
                "PACKET_CAPTURE_JOB",
                jobRef(saved),
                message
        );
        return saved;
    }

    public void completeCapture(Long jobId) {
        PacketCaptureJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Capture job not found"));
        if (!job.isActive()) {
            return;
        }
        Instant deadline = Instant.now().plusSeconds(job.getDurationSeconds() + 30L);
        try {
            while (Instant.now().isBefore(deadline)) {
                ProviderCaptureStatus status = captureProvider.getCaptureStatus(job.getProviderCaptureId());
                job.updateProgress(status.packetCount(), status.fileSizeBytes());
                jobRepository.save(job);
                if (status.status() == PacketCaptureJobStatus.FAILED) {
                    failJob(job, status.failureCode() == null
                            ? PacketCaptureFailureCode.TCPDUMP_FAILURE
                            : status.failureCode(), status.errorMessage());
                    return;
                }
                if (status.status() == PacketCaptureJobStatus.CANCELLED) {
                    job.cancel(status.errorMessage() == null ? "Capture cancelled" : status.errorMessage());
                    jobRepository.save(job);
                    return;
                }
                if (status.status() == PacketCaptureJobStatus.COMPLETED) {
                    ingestCompletedCapture(job);
                    return;
                }
                Thread.sleep(1000);
            }
            captureProvider.cancelCapture(job.getProviderCaptureId());
            failJob(job, PacketCaptureFailureCode.TIMEOUT, "Capture did not complete within the allowed time");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failJob(job, PacketCaptureFailureCode.TIMEOUT, "Capture was interrupted");
        } catch (CaptureProviderException exception) {
            failJob(job, exception.code(), exception.getMessage());
        } catch (ResponseStatusException exception) {
            failJob(job, PacketCaptureFailureCode.ANALYSIS_FAILURE, exception.getReason());
        } catch (RuntimeException exception) {
            LOGGER.warn("Capture completion failed for job {}: {}", jobId, exception.getMessage());
            failJob(job, PacketCaptureFailureCode.TRANSFER_FAILURE, exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<PacketCaptureJobResponse> list(Long incidentId) {
        loadIncident(incidentId);
        return jobRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PacketCaptureJobResponse get(Long incidentId, Long jobId) {
        loadIncident(incidentId);
        PacketCaptureJob job = jobRepository.findByIdAndIncidentId(jobId, incidentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Capture job not found"));
        return toResponse(job);
    }

    @Transactional
    public PacketCaptureJobResponse cancel(Long incidentId, Long jobId) {
        PacketCaptureJob job = jobRepository.findByIdAndIncidentId(jobId, incidentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Capture job not found"));
        if (!job.isActive()) {
            return toResponse(job);
        }
        if (job.getProviderCaptureId() != null) {
            captureProvider.cancelCapture(job.getProviderCaptureId());
        }
        job.cancel("Capture cancelled by operator");
        jobRepository.save(job);
        return toResponse(job);
    }

    public PacketCaptureProviderHealth providerHealth() {
        try {
            return captureProvider.health();
        } catch (RuntimeException exception) {
            return new PacketCaptureProviderHealth(false, "DOWN", "Capture sensor is unavailable: " + exception.getMessage());
        }
    }

    private void ingestCompletedCapture(PacketCaptureJob job) {
        byte[] bytes = captureProvider.retrieveCapture(job.getProviderCaptureId());
        if (bytes == null || bytes.length == 0) {
            failJob(job, PacketCaptureFailureCode.EMPTY_CAPTURE, "Capture file is empty");
            return;
        }
        if (bytes.length > properties.getCapture().getMaxFileSizeBytes()) {
            failJob(job, PacketCaptureFailureCode.TRANSFER_FAILURE, "Capture file exceeds the configured size limit");
            return;
        }
        try {
            PacketCaptureAnalysisResponse analysis = analysisService.ingest(
                    job.getIncidentId(),
                    "capture-" + job.getId() + ".pcap",
                    MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    bytes,
                    AuditAction.PACKET_CAPTURE_CAPTURED
            );
            job.complete(analysis.totalPackets(), bytes.length, analysis.id());
            jobRepository.save(job);
        } catch (ResponseStatusException exception) {
            failJob(job, PacketCaptureFailureCode.ANALYSIS_FAILURE, exception.getReason());
        }
    }

    private void failJob(PacketCaptureJob job, PacketCaptureFailureCode code, String message) {
        job.fail(code == null ? PacketCaptureFailureCode.TCPDUMP_FAILURE : code, message);
        jobRepository.save(job);
        auditLogService.log(
                AuditAction.PACKET_CAPTURE_FAILED,
                "PACKET_CAPTURE_JOB",
                jobRef(job),
                message
        );
    }

    private CapturePlan planFor(Incident incident, StartPacketCaptureRequest request) {
        return planFor(incident, request, CaptureTrigger.MANUAL);
    }

    private CapturePlan planFor(Incident incident, StartPacketCaptureRequest request, CaptureTrigger trigger) {
        TrafficEndpoints endpoints = incidentComponentBinder.trafficEndpoints(incident);
        String sourceIp = CaptureIp.normalizeOrNull(endpoints.sourceIp());
        String destinationIp = CaptureIp.normalizeOrNull(endpoints.destinationIp());
        if (sourceIp == null && destinationIp == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Could not determine capture endpoints from this incident"
            );
        }
        String pointId = request != null && request.capturePointId() != null && !request.capturePointId().isBlank()
                ? request.capturePointId().trim()
                : properties.getCapture().getDefaultPoint();
        PcapCaptureProperties.Point point = properties.getPoints().get(pointId);
        if (point == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capture point is not configured");
        }
        if (!captureProvider.providerId().equalsIgnoreCase(point.getProvider())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Capture point provider is not available"
            );
        }
        Optional<ResolvedComponentIp> destination = identityResolver.resolveByIp(destinationIp);
        int duration = clampDuration(request == null ? null : request.durationSeconds());
        return new CapturePlan(
                sourceIp,
                destinationIp,
                destination.map(item -> item.component().getName()).orElse(null),
                destination.map(ResolvedComponentIp::matchedInterfaceName).orElse(null),
                destination.map(ResolvedComponentIp::matchedIp).orElse(destinationIp),
                pointId,
                point.getDisplayName(),
                point.getInterfaceName(),
                point.getProvider(),
                duration
        );
    }

    private int clampDuration(Integer requested) {
        PcapCaptureProperties.Capture capture = properties.getCapture();
        int duration = requested == null ? capture.getDefaultDurationSeconds() : requested;
        if (duration > capture.getMaxDurationSeconds()) {
            return capture.getMaxDurationSeconds();
        }
        if (duration < capture.getMinDurationSeconds()) {
            return capture.getMinDurationSeconds();
        }
        return duration;
    }

    private Incident loadIncident(Long incidentId) {
        return incidentRepository.findWithEventsById(incidentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }

    private PacketCaptureJobResponse toResponse(PacketCaptureJob job) {
        Optional<ResolvedComponentIp> destination = identityResolver.resolveByIp(job.getDestinationIp());
        return PacketCaptureJobResponse.from(
                job,
                destination.map(item -> item.component().getName()).orElse(null),
                destination.map(ResolvedComponentIp::matchedIp).orElse(job.getDestinationIp())
        );
    }

    private static String jobRef(PacketCaptureJob job) {
        if (job == null || job.getId() == null) {
            return "pending";
        }
        return job.getId().toString();
    }

    private ResponseStatusException mapped(CaptureProviderException exception) {
        HttpStatus status = switch (exception.code()) {
            case PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case BUSY, INCIDENT_RESOLVED -> HttpStatus.CONFLICT;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new ResponseStatusException(status, exception.getMessage(), exception);
    }

    private record CapturePlan(
            String sourceIp,
            String destinationIp,
            String destinationComponentName,
            String matchedInterfaceName,
            String matchedInterfaceIp,
            String pointId,
            String displayName,
            String interfaceName,
            String providerId,
            int durationSeconds
    ) {
    }
}
