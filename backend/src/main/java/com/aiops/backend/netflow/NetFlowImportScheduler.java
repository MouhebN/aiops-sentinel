package com.aiops.backend.netflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NetFlowImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(NetFlowImportScheduler.class);

    private final NetFlowProperties properties;
    private final NetFlowSourceRepository sourceRepository;
    private final NetFlowImportService importService;

    public NetFlowImportScheduler(
            NetFlowProperties properties,
            NetFlowSourceRepository sourceRepository,
            NetFlowImportService importService
    ) {
        this.properties = properties;
        this.sourceRepository = sourceRepository;
        this.importService = importService;
    }

    @Scheduled(fixedDelayString = "${app.netflow.import-interval-ms:15000}")
    public void importEnabledSources() {
        if (!properties.isEnabled() || !properties.isAutoImportEnabled()) {
            return;
        }
        sourceRepository.findAllByEnabledTrue().forEach(source -> {
            try {
                importService.importQuietly(source.getId());
            } catch (RuntimeException exception) {
                log.warn(
                        "Scheduled NetFlow import failed sourceId={} name={} reason={}",
                        source.getId(),
                        source.getName(),
                        exception.getMessage()
                );
            }
        });
    }
}
