package com.brightline.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("incident-summary")
public record IncidentSummaryProperties(
        String apiKey,
        String endpoint,
        String model,
        int connectTimeoutMillis,
        int readTimeoutMillis,
        int cacheTtlSeconds) {
}
