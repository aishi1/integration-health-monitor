package com.brightline.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** All tunables live in application.yml under "health". */
@ConfigurationProperties("health")
public record HealthProperties(
        String referenceTime,
        int windowMinutes,
        int minRequests,
        double recoveryFactor,
        int refreshSeconds,
        int defaultCadenceMinutes,
        String sampleLogs,
        Rate errorRate,
        Rate throttle,
        Rate authErrors,
        Count consecutiveFailures,
        Latency latency,
        Silence silence) {

    public record Rate(double amber, double red) {}
    public record Count(int amber, int red) {}
    public record Latency(double amberRatio, double redRatio) {}
    public record Silence(double amberCadences, double redCadences) {}
}
