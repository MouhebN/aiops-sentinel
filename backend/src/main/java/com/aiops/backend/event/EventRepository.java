package com.aiops.backend.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    List<Event> findTop200ByOccurredAtGreaterThanEqualAndEventSourceOrderByOccurredAtDesc(Instant occurredAt, String eventSource);

    List<Event> findByOccurredAtBefore(Instant occurredAt);
}
