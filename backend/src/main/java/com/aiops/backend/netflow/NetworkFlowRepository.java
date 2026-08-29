package com.aiops.backend.netflow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkFlowRepository extends JpaRepository<NetworkFlow, Long>, JpaSpecificationExecutor<NetworkFlow> {
    Optional<NetworkFlow> findByRecordHash(String recordHash);

    Page<NetworkFlow> findAll(Specification<NetworkFlow> specification, Pageable pageable);

    List<NetworkFlow> findByIncidentIdOrderByStartTimeDesc(Long incidentId);

    List<NetworkFlow> findByIdIn(Collection<Long> ids);

    List<NetworkFlow> findByStartTimeBetween(Instant from, Instant to);
}
