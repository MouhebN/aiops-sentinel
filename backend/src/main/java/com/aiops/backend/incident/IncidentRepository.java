package com.aiops.backend.incident;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByCorrelationKeyAndStatus(String correlationKey, IncidentStatus status);

    Optional<Incident> findFirstByCorrelationKeyOrderByLastSeenAtDesc(String correlationKey);

    List<Incident> findByCorrelationKeyStartsWithOrderByLastSeenAtDesc(String correlationKeyPrefix);

    @EntityGraph(attributePaths = "events")
    Optional<Incident> findWithEventsById(Long id);

    List<Incident> findTop5ByCorrelationKeyAndIdNotOrderByLastSeenAtDesc(String correlationKey, Long id);

    List<Incident> findTop5ByDeviceIdAndCategoryAndIdNotOrderByLastSeenAtDesc(String deviceId, String category, Long id);

    @EntityGraph(attributePaths = "events")
    List<Incident> findAll(Sort sort);

    @EntityGraph(attributePaths = "events")
    List<Incident> findAll();

    @EntityGraph(attributePaths = "events")
    List<Incident> findByCategoryAndLastSeenAtGreaterThanEqualOrderByLastSeenAtDesc(String category, Instant lastSeenAt);
}
