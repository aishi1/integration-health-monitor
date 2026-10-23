package com.brightline.monitor.service;

import com.brightline.monitor.config.HealthProperties;
import com.brightline.monitor.domain.ErrorClass;
import com.brightline.monitor.domain.HealthStatus;
import com.brightline.monitor.domain.Integration;
import com.brightline.monitor.domain.RequestLog;
import com.brightline.monitor.repository.RequestLogRepository;
import com.brightline.monitor.rules.WindowStats;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {
    private static final int MIN_SUCCESSES_FOR_LATENCY = 5;
    private static final int RECENT_SUCCESSES = 3;

    private final RequestLogRepository logs;
    private final HealthProperties props;

    public StatsService(RequestLogRepository logs, HealthProperties props) {
        this.logs = logs;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public WindowStats compute(Integration i, LocalDateTime now, HealthStatus previous) {
        List<RequestLog> window = logs.findByIntegrationIdAndRequestedAtBetweenOrderByRequestedAtAsc(
                i.getId(), now.minusMinutes(props.windowMinutes()), now);

        int server = 0, timeouts = 0, throttled = 0, auth = 0, client = 0;
        for (RequestLog l : window) {
            switch (l.getErrorClass()) {
                case SERVER -> server++;
                case TIMEOUT -> timeouts++;
                case THROTTLED -> throttled++;
                case AUTH -> auth++;
                case CLIENT -> client++;
                case NONE -> { }
            }
        }

        int consecutive = 0;
        for (int k = window.size() - 1; k >= 0; k--) {
            ErrorClass c = window.get(k).getErrorClass();
            if (c == ErrorClass.SERVER || c == ErrorClass.TIMEOUT) consecutive++; else break;
        }

        List<Integer> okLatencies = window.stream().filter(RequestLog::isSuccess)
                .map(RequestLog::getLatencyMs).toList();
        Double baseline = null, recent = null;
        if (okLatencies.size() >= MIN_SUCCESSES_FOR_LATENCY) {
            baseline = median(okLatencies);
            recent = median(okLatencies.subList(okLatencies.size() - RECENT_SUCCESSES, okLatencies.size()));
        }

        Long since = logs.findLatestRequestAt(i.getId(), now)
                .map(requestedAt -> Duration.between(requestedAt, now).toMinutes()).orElse(null);

        return new WindowStats(props.windowMinutes(), window.size(), server, timeouts, throttled, auth,
                client, consecutive, since, baseline, recent, previous);
    }

    static double median(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
