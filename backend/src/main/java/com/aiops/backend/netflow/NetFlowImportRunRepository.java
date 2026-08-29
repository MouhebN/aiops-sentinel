package com.aiops.backend.netflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NetFlowImportRunRepository extends JpaRepository<NetFlowImportRun, Long> {
    List<NetFlowImportRun> findTop20ByOrderByStartedAtDesc();
}
