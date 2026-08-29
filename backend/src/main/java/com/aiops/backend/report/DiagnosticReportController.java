package com.aiops.backend.report;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class DiagnosticReportController {

    private final DiagnosticReportService reportService;

    public DiagnosticReportController(DiagnosticReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public DiagnosticReportResponse createReport(@Valid @RequestBody SaveDiagnosticReportRequest request) {
        return reportService.save(request);
    }

    @GetMapping
    public List<DiagnosticReportResponse> listReports() {
        return reportService.list();
    }

    @GetMapping("/{id}")
    public DiagnosticReportResponse getReport(@PathVariable Long id) {
        return reportService.get(id);
    }
}
