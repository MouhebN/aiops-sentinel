package com.aiops.backend.netflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NetFlowSourceRepository extends JpaRepository<NetFlowSource, Long> {
    List<NetFlowSource> findAllByEnabledTrue();
}
