package com.aiops.backend.alert;

import com.aiops.backend.event.EventResponse;
import com.aiops.backend.event.EventService;
import com.aiops.backend.event.Severity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final EventService eventService;

    public AlertController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventResponse> listAlerts(@RequestParam(defaultValue = "WARNING") Severity minSeverity) {
        return eventService.search(null, null, null, null, null, null)
                .stream()
                .filter(event -> event.getSeverity().ordinal() >= minSeverity.ordinal())
                .map(EventResponse::from)
                .toList();
    }
}
