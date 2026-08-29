package com.aiops.backend.pcap;

import com.aiops.backend.audit.AuditAction;
import com.aiops.backend.audit.AuditLogService;
import com.aiops.backend.incident.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Service
public class PacketCaptureAnalysisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PacketCaptureAnalysisService.class);

    private final PacketCaptureAnalysisRepository repository;
    private final IncidentRepository incidentRepository;
    private final AuditLogService auditLogService;
    private final PcapAnalysisClient analysisClient;

    public PacketCaptureAnalysisService(
            PacketCaptureAnalysisRepository repository,
            IncidentRepository incidentRepository,
            AuditLogService auditLogService,
            PcapAnalysisClient analysisClient
    ) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
        this.auditLogService = auditLogService;
        this.analysisClient = analysisClient;
    }

    @Transactional
    public PacketCaptureAnalysisResponse upload(Long incidentId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Packet capture file is empty");
        }
        String fileName = file.getOriginalFilename();
        if (!isSupportedPcap(fileName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .pcap and .pcapng files are allowed");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read packet capture file");
        }
        return ingest(
                incidentId,
                fileName == null ? "capture.pcap" : fileName,
                contentTypeOf(file),
                bytes,
                AuditAction.PACKET_CAPTURE_UPLOADED
        );
    }

    @Transactional
    public PacketCaptureAnalysisResponse ingest(
            Long incidentId,
            String fileName,
            String contentType,
            byte[] bytes,
            AuditAction sourceAction
    ) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Packet capture file is empty");
        }
        FastApiPacketCaptureAnalysisResponse analysis = analysisClient.analyze(fileName, contentType, bytes);
        PacketCaptureAnalysis saved = repository.save(new PacketCaptureAnalysis(
                incidentId,
                fileName == null ? "capture.pcap" : fileName,
                contentType == null || contentType.isBlank()
                        ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                        : contentType,
                bytes.length,
                analysis.totalPackets(),
                analysis.totalBytes(),
                writeList(analysis.topSourceIps()),
                writeList(analysis.topDestinationIps()),
                writeList(analysis.topProtocols()),
                writeList(analysis.topDestinationPorts()),
                writeList(analysis.suspiciousFindings()),
                analysis.summary()
        ));
        auditLogService.log(
                sourceAction == null ? AuditAction.PACKET_CAPTURE_UPLOADED : sourceAction,
                "PACKET_CAPTURE",
                saved.getId().toString(),
                "Stored packet capture " + saved.getFileName() + " for incident #" + incidentId
        );
        auditLogService.log(
                AuditAction.PACKET_CAPTURE_ANALYZED,
                "PACKET_CAPTURE",
                saved.getId().toString(),
                "Analyzed packet capture " + saved.getFileName() + " for incident #" + incidentId
        );
        return PacketCaptureAnalysisResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PacketCaptureAnalysisResponse> listForIncident(Long incidentId) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        return repository.findByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .map(PacketCaptureAnalysisResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long incidentId, Long analysisId) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found");
        }
        PacketCaptureAnalysis analysis = repository.findByIdAndIncidentId(analysisId, incidentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Packet capture analysis not found"));
        auditLogService.log(
                AuditAction.PCAP_ANALYSIS_DELETE,
                "PACKET_CAPTURE_ANALYSIS",
                analysis.getId().toString(),
                "incidentId=" + incidentId + "; filename=" + analysis.getFileName()
        );
        repository.delete(analysis);
        LOGGER.info(
                "Deleted packet capture analysis: analysisId={}, incidentId={}, filename={}",
                analysisId,
                incidentId,
                analysis.getFileName()
        );
    }

    @Transactional(readOnly = true)
    public PacketCaptureAnalysisResponse get(Long id) {
        return repository.findById(id)
                .map(PacketCaptureAnalysisResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Packet capture analysis not found"));
    }

    @Transactional(readOnly = true)
    public List<PacketCaptureAnalysisResponse> latestForIncident(Long incidentId) {
        return repository.findTop5ByIncidentIdOrderByCreatedAtDesc(incidentId)
                .stream()
                .map(PacketCaptureAnalysisResponse::from)
                .toList();
    }

    private boolean isSupportedPcap(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pcap") || lower.endsWith(".pcapng");
    }

    private String contentTypeOf(MultipartFile file) {
        return file.getContentType() == null || file.getContentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.getContentType();
    }

    private String writeList(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join("\n", values);
    }
}
