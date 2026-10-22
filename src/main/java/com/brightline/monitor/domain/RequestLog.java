package com.brightline.monitor.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "request_log",
       indexes = @Index(name = "idx_log_integration_time", columnList = "integration_id, requested_at"))
public class RequestLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "integration_id")
    private Integration integration;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(nullable = false, length = 16)
    private String status;

    /** Nullable: timeouts carry no HTTP status. */
    private Integer statusCode;

    private int latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ErrorClass errorClass;

    private boolean success;

    protected RequestLog() {}

    public RequestLog(Integration integration, LocalDateTime requestedAt, String status,
                      Integer statusCode, int latencyMs) {
        this.integration = integration;
        this.requestedAt = requestedAt;
        this.status = status;
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.errorClass = ErrorClass.classify(status, statusCode);
        this.success = this.errorClass == ErrorClass.NONE;
    }

    public Long getId() { return id; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public String getStatus() { return status; }
    public Integer getStatusCode() { return statusCode; }
    public int getLatencyMs() { return latencyMs; }
    public ErrorClass getErrorClass() { return errorClass; }
    public boolean isSuccess() { return success; }
}
