package com.brightline.monitor.service;

import com.brightline.monitor.config.HealthClock;
import com.brightline.monitor.domain.HealthSnapshot;
import com.brightline.monitor.domain.HealthStatus;
import com.brightline.monitor.domain.Integration;
import com.brightline.monitor.repository.HealthSnapshotRepository;
import com.brightline.monitor.repository.IntegrationRepository;
import com.brightline.monitor.rules.HealthRule;
import com.brightline.monitor.rules.RuleResult;
import com.brightline.monitor.rules.WindowStats;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HealthEvaluationService {
    private static final Logger log = LoggerFactory.getLogger(HealthEvaluationService.class);

    private final IntegrationRepository integrations;
    private final HealthSnapshotRepository snapshots;
    private final StatsService stats;
    private final HealthClock clock;
    private final List<HealthRule> rules;   // injected in @Order sequence

    public HealthEvaluationService(IntegrationRepository integrations, HealthSnapshotRepository snapshots,
                                   StatsService stats, HealthClock clock, List<HealthRule> rules) {
        this.integrations = integrations;
        this.snapshots = snapshots;
        this.stats = stats;
        this.clock = clock;
        this.rules = rules;
    }

    public synchronized void evaluateAll() {
        LocalDateTime now = clock.now();
        for (Integration i : integrations.findAll()) {
            try {
                evaluate(i, now);
            } catch (RuntimeException e) {   // one bad integration must not block the others
                log.error("Evaluation failed for {}", i.getName(), e);
            }
        }
    }

    /**
     * Re-evaluates only if the newest snapshot is older than maxAge (zero = always).
     * Lets the browser's chosen refresh interval drive evaluation, not just the fixed scheduler.
     */
    public synchronized boolean evaluateIfStale(Duration maxAge) {
        LocalDateTime newest = snapshots.findAll().stream().map(HealthSnapshot::getEvaluatedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        // 2s slack so a 15s poll does not narrowly miss a 15s threshold and wait a whole extra cycle
        boolean stale = newest == null || maxAge.isZero()
                || Duration.between(newest, LocalDateTime.now()).compareTo(maxAge.minusSeconds(2)) >= 0;
        if (stale) evaluateAll();
        return stale;
    }

    public HealthSnapshot evaluate(Integration i, LocalDateTime now) {
        HealthStatus previous = snapshots.findById(i.getId()).map(HealthSnapshot::getStatus).orElse(null);
        WindowStats s = stats.compute(i, now, previous);

        List<RuleResult> results = rules.stream().map(r -> r.evaluate(i, s)).toList();
        HealthStatus status = results.stream().map(RuleResult::status)
                .max(Comparator.naturalOrder()).orElse(HealthStatus.GREEN);   // worst-of rollup

        String reason = results.stream().filter(r -> r.status() != HealthStatus.GREEN)
                .map(RuleResult::reason).collect(Collectors.joining("; "));
        if (reason.isBlank()) {
            reason = results.stream().map(RuleResult::reason).filter(t -> !t.isBlank()).findFirst()
                    .orElse("All checks passing");
        }

        HealthSnapshot snap = new HealthSnapshot(i.getId(), i.getName(), status, reason, s.errorRate(),
                s.baselineLatencyMs(), s.consecutiveFailures(), s.minutesSinceLastRequest(), s.total(),
                now, LocalDateTime.now());
        return snapshots.save(snap);   // upsert: id is assigned, so save() merges
    }
}
