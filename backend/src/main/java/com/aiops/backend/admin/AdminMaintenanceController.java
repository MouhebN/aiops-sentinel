package com.aiops.backend.admin;

import com.aiops.backend.event.EventService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/maintenance")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMaintenanceController {

    private final AdminMaintenanceService maintenanceService;
    private final EventService eventService;

    public AdminMaintenanceController(AdminMaintenanceService maintenanceService, EventService eventService) {
        this.maintenanceService = maintenanceService;
        this.eventService = eventService;
    }

    @PostMapping("/reset-demo-data")
    public void resetDemoData() {
        maintenanceService.resetDemoData();
    }

    @DeleteMapping("/events/older-than")
    public int purgeOldEvents(@RequestParam int days) {
        return eventService.deleteOlderThan(days);
    }
}
