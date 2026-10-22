package com.brightline.monitor.report;

import com.brightline.monitor.domain.HealthStatus;
import java.time.LocalDateTime;

/** One heat-strip bucket with the underlying failure-rate and error-class metrics. */
public record BucketCell(
        HealthStatus status,
        String detail,
        LocalDateTime start,
        LocalDateTime end,
        int total,
        int serverErrors,
        int timeoutErrors,
        int throttledErrors,
        int authErrors,
        int clientErrors) {

    public String css() { return status == null ? "nodata" : status.name().toLowerCase(); }

    /** Matches the report's configured error-rate rule: 5xx plus timeouts over all requests. */
    public double failureRate() {
        return total == 0 ? 0 : (double) (serverErrors + timeoutErrors) / total;
    }

    public double serverPct() { return total == 0 ? 0 : (double) serverErrors / total; }
    public double timeoutPct() { return total == 0 ? 0 : (double) timeoutErrors / total; }
    public double throttledPct() { return total == 0 ? 0 : (double) throttledErrors / total; }
    public double authPct() { return total == 0 ? 0 : (double) authErrors / total; }

    public String errorSummary() {
        return detail + " | 5xx: " + serverErrors + ", timeouts: " + timeoutErrors
                + ", 429: " + throttledErrors + ", auth: " + authErrors;
    }
}
