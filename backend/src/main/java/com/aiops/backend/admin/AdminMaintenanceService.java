package com.aiops.backend.admin;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import com.aiops.backend.device.DeviceRepository;
import com.aiops.backend.event.EventRepository;
import com.aiops.backend.incident.IncidentRepository;
import com.aiops.backend.pcap.PacketCaptureAnalysisRepository;
import com.aiops.backend.report.DiagnosticReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMaintenanceService {

    private final DiagnosticReportRepository reportRepository;
    private final PacketCaptureAnalysisRepository packetCaptureAnalysisRepository;
    private final IncidentRepository incidentRepository;
    private final EventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final AuditLogService auditLogService;

    public AdminMaintenanceService(
            DiagnosticReportRepository reportRepository,
            PacketCaptureAnalysisRepository packetCaptureAnalysisRepository,
            IncidentRepository incidentRepository,
            EventRepository eventRepository,
            DeviceRepository deviceRepository,
            AuditLogService auditLogService
    ) {
        this.reportRepository = reportRepository;
        this.packetCaptureAnalysisRepository = packetCaptureAnalysisRepository;
        this.incidentRepository = incidentRepository;
        this.eventRepository = eventRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void resetDemoData() {
        reportRepository.deleteAll();
        packetCaptureAnalysisRepository.deleteAll();
        incidentRepository.deleteAll();
        eventRepository.deleteAll();
        deviceRepository.deleteAll();
        auditLogService.log(
                AuditAction.DEMO_DATA_RESET,
                "MAINTENANCE",
                null,
                "Reset demo data: reports, packet captures, incidents, events, and derived devices"
        );
    }
}
