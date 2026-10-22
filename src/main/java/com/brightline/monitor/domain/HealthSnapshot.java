package com.brightline.monitor.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Read-side projection: one row per integration, upserted every evaluation. */
@Entity
@Table(name = "integration_health_snapshot")
public class HealthSnapshot {
    @Id
    private Long integrationId;
    private String integrationName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private HealthStatus status;

    @Column(length = 500)
    private String reason;

    private double errorRate;
    private Double medianLatencyMs;
    private int consecutiveFailures;
    private Long minutesSinceLastRequest;
    private int totalRequests;

    /** Reference time the evaluation was computed "as of" (log time in demo mode). */
    private LocalDateTime asOf;
    /** Wall-clock time the evaluation ran; used to detect a stalled scheduler. */
    private LocalDateTime evaluatedAt;

    protected HealthSnapshot() {}

    public HealthSnapshot(Long integrationId, String integrationName, HealthStatus status, String reason,
                          double errorRate, Double medianLatencyMs, int consecutiveFailures,
                          Long minutesSinceLastRequest, int totalRequests,
                          LocalDateTime asOf, LocalDateTime evaluatedAt) {
        this.integrationId = integrationId;
        this.integrationName = integrationName;
        this.status = status;
        this.reason = reason;
        this.errorRate = errorRate;
        this.medianLatencyMs = medianLatencyMs;
        this.consecutiveFailures = consecutiveFailures;
        this.minutesSinceLastRequest = minutesSinceLastRequest;
        this.totalRequests = totalRequests;
        this.asOf = asOf;
        this.evaluatedAt = evaluatedAt;
    }

    public Long getIntegrationId() { return integrationId; }
    public String getIntegrationName() { return integrationName; }
    public HealthStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public double getErrorRate() { return errorRate; }
    public Double getMedianLatencyMs() { return medianLatencyMs; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public Long getMinutesSinceLastRequest() { return minutesSinceLastRequest; }
    public int getTotalRequests() { return totalRequests; }
    public LocalDateTime getAsOf() { return asOf; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
}
