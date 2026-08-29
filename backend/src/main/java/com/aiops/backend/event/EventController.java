package com.aiops.backend.event;

import com.aiops.backend.device.DeviceType;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public EventResponse ingestEvent(@Valid @RequestBody EventIngestionRequest request) {
        return EventResponse.from(eventService.ingest(request));
    }

    @GetMapping
    public List<EventResponse> listEvents(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) DeviceType deviceType,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return eventService.search(deviceId, deviceType, eventType, severity, from, to)
                .stream()
                .map(EventResponse::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteEvent(@PathVariable Long id) {
        eventService.delete(id);
    }

    @DeleteMapping("/older-than")
    @PreAuthorize("hasRole('ADMIN')")
    public int deleteOlderThan(@RequestParam int days) {
        return eventService.deleteOlderThan(days);
    }
}
