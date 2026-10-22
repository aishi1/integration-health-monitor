package com.brightline.monitor.rules;

import com.brightline.monitor.domain.HealthStatus;

/** Pre-computed numbers for one integration over the evaluation window. */
public record WindowStats(
        int windowMinutes,
        int total,
        int serverErrors,
        int timeouts,
        int throttled,
        int authErrors,
        int clientErrors,
        int consecutiveFailures,        // trailing SERVER/TIMEOUT failures
        Long minutesSinceLastRequest,   // null = never seen
        Double baselineLatencyMs,       // median of successful calls; null = too few successes
        Double recentLatencyMs,         // median of last 3 successful calls
        HealthStatus previousStatus) {  // for hysteresis; null on first evaluation

    public int failures() { return serverErrors + timeouts; }

    public double errorRate() { return total == 0 ? 0 : (double) failures() / total; }
}
