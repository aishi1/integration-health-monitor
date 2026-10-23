package com.brightline.monitor.service;

import com.brightline.monitor.domain.HealthStatus;

public record IncidentMetrics(
        String integrationName,
        HealthStatus status,
        int windowMinutes,
        long totalRequests,
        long successes,
        long serverErrors,
        long timeouts,
        long throttled,
        long authErrors,
        long clientErrors) {
}
