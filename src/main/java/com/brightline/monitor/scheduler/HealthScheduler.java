package com.brightline.monitor.scheduler;

import com.brightline.monitor.service.HealthEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HealthScheduler {
    private static final Logger log = LoggerFactory.getLogger(HealthScheduler.class);
    private final HealthEvaluationService service;

    public HealthScheduler(HealthEvaluationService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${health.evaluation-interval-ms}",
               initialDelayString = "${health.initial-delay-ms}")
    public void run() {
        try {
            service.evaluateAll();
        } catch (RuntimeException e) {
            log.error("Health evaluation run failed", e);
        }
    }
}
