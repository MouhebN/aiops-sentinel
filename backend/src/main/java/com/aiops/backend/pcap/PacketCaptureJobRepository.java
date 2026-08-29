package com.aiops.backend.pcap;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PacketCaptureJobRepository extends JpaRepository<PacketCaptureJob, Long> {

    List<PacketCaptureJob> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    Optional<PacketCaptureJob> findByIdAndIncidentId(Long id, Long incidentId);

    boolean existsByIncidentIdAndStatusIn(Long incidentId, List<PacketCaptureJobStatus> statuses);

    long countByStatusIn(List<PacketCaptureJobStatus> statuses);

    Optional<PacketCaptureJob> findFirstByIncidentIdAndStatusInOrderByCreatedAtDesc(
            Long incidentId,
            List<PacketCaptureJobStatus> statuses
    );

    boolean existsByIncidentIdAndTriggerIn(Long incidentId, List<CaptureTrigger> triggers);

    Optional<PacketCaptureJob> findFirstByIncidentIdAndTriggerInOrderByCreatedAtDesc(
            Long incidentId,
            List<CaptureTrigger> triggers
    );
}
