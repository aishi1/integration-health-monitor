package com.brightline.monitor.config;

import com.brightline.monitor.repository.RequestLogRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * Source of "now" for evaluations. In demo mode (latest-log) time is anchored to the
 * newest log so historical sample data can be evaluated; in production use "system".
 */
@Component
public class HealthClock {
    private final HealthProperties props;
    private final RequestLogRepository logs;

    public HealthClock(HealthProperties props, RequestLogRepository logs) {
        this.props = props;
        this.logs = logs;
    }

    public LocalDateTime now() {
        if ("system".equalsIgnoreCase(props.referenceTime())) return LocalDateTime.now();
        return logs.findMaxRequestedAt().orElseGet(LocalDateTime::now);
    }
}
