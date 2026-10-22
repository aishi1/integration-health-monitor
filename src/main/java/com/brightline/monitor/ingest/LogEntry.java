package com.brightline.monitor.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/** status_code is Integer on purpose: timeouts have "status_code": null. */
public record LogEntry(
        String integration,
        LocalDateTime timestamp,
        String status,
        @JsonProperty("status_code") Integer statusCode,
        @JsonProperty("latency_ms") int latencyMs) {}
