package com.aiops.backend.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiagnosticReportRepository extends JpaRepository<DiagnosticReport, Long> {

    List<DiagnosticReport> findAllByOrderByGeneratedAtDesc();

    List<DiagnosticReport> findTop5ByDeviceIdOrderByGeneratedAtDesc(String deviceId);
}
