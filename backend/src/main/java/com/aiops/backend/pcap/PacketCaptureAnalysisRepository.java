package com.aiops.backend.pcap;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PacketCaptureAnalysisRepository extends JpaRepository<PacketCaptureAnalysis, Long> {

    List<PacketCaptureAnalysis> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    List<PacketCaptureAnalysis> findTop5ByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    Optional<PacketCaptureAnalysis> findByIdAndIncidentId(Long id, Long incidentId);
}
