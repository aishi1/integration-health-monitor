package com.brightline.monitor.report;

import com.brightline.monitor.config.HealthClock;
import com.brightline.monitor.config.HealthProperties;
import com.brightline.monitor.domain.ErrorClass;
import com.brightline.monitor.domain.HealthStatus;
import com.brightline.monitor.domain.Integration;
import com.brightline.monitor.domain.LogPoint;
import com.brightline.monitor.repository.IntegrationRepository;
import com.brightline.monitor.repository.RequestLogRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Daily / weekly / monthly reports. Each period is cut into equal buckets ending at "now"
 * (hourly for 24h, 24-hour buckets for 7 and 30 days). Every bucket, and the whole period, is judged
 * with the same thresholds as the live view (error rate, 429 rate, auth errors) plus a data-coverage check.
 * The live-only rules (consecutive failures, latency drift, silence) are not applied to history.
 */
@Service
public class ReportService {
    private static final long CACHE_TTL_MS = 30_000;
    private static final double MIN_COVERAGE = 0.8;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.ENGLISH);

    private final RequestLogRepository logs;
    private final IntegrationRepository integrations;
    private final HealthProperties props;
    private final HealthClock clock;
    private final Map<ReportPeriod, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(ReportView view, long atMillis) {}
    private record Verdict(HealthStatus status, String reason) {}

    public ReportService(RequestLogRepository logs, IntegrationRepository integrations,
                         HealthProperties props, HealthClock clock) {
        this.logs = logs;
        this.integrations = integrations;
        this.props = props;
        this.clock = clock;
    }

    /** Short cache so auto-refreshing browsers do not re-aggregate tens of thousands of rows every tick. */
    public ReportView get(ReportPeriod period, boolean force) {
        long now = System.currentTimeMillis();
        Cached c = cache.get(period);
        if (!force && c != null && now - c.atMillis() < CACHE_TTL_MS) return c.view();
        ReportView view = build(period);
        cache.put(period, new Cached(view, now));
        return view;
    }

    public ReportView build(ReportPeriod period) {
        LocalDateTime to = clock.now();
        LocalDateTime from = to.minus(period.span());
        Map<Long, List<LogPoint>> byIntegration = logs.findPoints(from, to).stream()
                .collect(Collectors.groupingBy(LogPoint::integrationId));
        int n = period.buckets();

        List<IntegrationReport> rows = new ArrayList<>();
        for (Integration i : integrations.findAll()) {
            rows.add(buildRow(i, byIntegration.getOrDefault(i.getId(), List.of()), period, to, n));
        }
        rows.sort(Comparator.comparing((IntegrationReport r) -> r.status()).reversed()
                .thenComparing(Comparator.comparingDouble((IntegrationReport r) -> r.errorPct()).reversed())
                .thenComparing(r -> r.name()));

        int red = (int) rows.stream().filter(r -> r.status() == HealthStatus.RED).count();
        int amber = (int) rows.stream().filter(r -> r.status() == HealthStatus.AMBER).count();
        int green = rows.size() - red - amber;
        String note = "Each cell = " + period.unitLabel() + " (oldest on the left, newest on the right). Hover a cell for details.";
        return new ReportView(period, from, to, note, red, amber, green, rows);
    }

    private IntegrationReport buildRow(Integration i, List<LogPoint> points, ReportPeriod period,
                                       LocalDateTime to, int n) {
        long unitSec = period.unit().toSeconds();
        Agg total = new Agg();
        Agg[] buckets = new Agg[n];
        for (int k = 0; k < n; k++) buckets[k] = new Agg();
        List<Integer> okLatencies = new ArrayList<>();

        for (LogPoint p : points) {
            long age = Duration.between(p.requestedAt(), to).getSeconds();
            int idx = age <= 0 ? 0 : (int) ((age - 1) / unitSec);   // bucket 0 = newest, covers (to-unit, to]
            if (idx >= n) idx = n - 1;
            buckets[idx].add(p);
            total.add(p);
            if (p.errorClass() == ErrorClass.NONE) okLatencies.add(p.latencyMs());
        }

        double expectedBucket = (double) period.unit().toMinutes() / i.getExpectedCadenceMin();
        double expectedTotal = expectedBucket * n;
        Verdict overall = total.total == 0
                ? new Verdict(HealthStatus.RED, "No requests in period")
                : judge(total, expectedTotal);

        List<BucketCell> cells = new ArrayList<>();
        int red = 0, amber = 0, green = 0, noData = 0;
        for (int idx = n - 1; idx >= 0; idx--) {                    // oldest first
            Agg a = buckets[idx];
            LocalDateTime end = to.minusSeconds(unitSec * idx);
            String range = FMT.format(end.minusSeconds(unitSec)) + " to " + FMT.format(end);
            if (a.total == 0) {
                cells.add(new BucketCell(null, range + ": no data"));
                noData++;
                continue;
            }
            Verdict v = judge(a, expectedBucket);
            switch (v.status()) {
                case RED -> red++;
                case AMBER -> amber++;
                case GREEN -> green++;
            }
            cells.add(new BucketCell(v.status(), range + " | " + a.total + " req | " + v.reason()));
        }

        double t = Math.max(1, total.total);
        return new IntegrationReport(i.getId(), i.getName(), overall.status(), overall.reason(), total.total,
                total.ok() / t, total.failures() / t, total.throttled / t, total.auth,
                percentile(okLatencies, 0.50), percentile(okLatencies, 0.95),
                total.total / expectedTotal, red, amber, green, noData, cells);
    }

    private Verdict judge(Agg a, double expected) {
        HealthStatus s = HealthStatus.GREEN;
        List<String> why = new ArrayList<>();

        double coverage = a.total / expected;
        if (coverage < MIN_COVERAGE) {
            s = worse(s, HealthStatus.AMBER);
            why.add(Math.round(coverage * 100) + "% of expected requests received (data gap)");
        }
        if (a.total >= props.minRequests()) {
            double er = (double) a.failures() / a.total;
            if (er >= props.errorRate().red()) { s = worse(s, HealthStatus.RED); why.add(pct(er) + " failure rate"); }
            else if (er >= props.errorRate().amber()) { s = worse(s, HealthStatus.AMBER); why.add(pct(er) + " failure rate"); }

            double tr = (double) a.throttled / a.total;
            if (tr >= props.throttle().red()) { s = worse(s, HealthStatus.RED); why.add(pct(tr) + " rate-limited (429)"); }
            else if (tr >= props.throttle().amber()) { s = worse(s, HealthStatus.AMBER); why.add(pct(tr) + " rate-limited (429)"); }
        }
        if (a.auth > 0) {
            double ar = (double) a.auth / a.total;
            if (ar >= props.authErrors().red()) { s = worse(s, HealthStatus.RED); why.add(a.auth + " auth failures (401/403)"); }
            else if (ar >= props.authErrors().amber()) { s = worse(s, HealthStatus.AMBER); why.add(a.auth + " auth failures (401/403)"); }
        }
        if (why.isEmpty()) {
            why.add(a.failures() > 0 ? a.failures() + " failure(s), below threshold" : "No failures");
        }
        return new Verdict(s, String.join("; ", why));
    }

    private static HealthStatus worse(HealthStatus a, HealthStatus b) { return a.compareTo(b) >= 0 ? a : b; }

    private static String pct(double x) { return Math.round(x * 100) + "%"; }

    private static Double percentile(List<Integer> values, double q) {
        if (values.isEmpty()) return null;
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int idx = Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(q * sorted.size()) - 1));
        return sorted.get(idx).doubleValue();
    }

    private static final class Agg {
        int total, server, timeouts, throttled, auth, client;

        void add(LogPoint p) {
            total++;
            switch (p.errorClass()) {
                case SERVER -> server++;
                case TIMEOUT -> timeouts++;
                case THROTTLED -> throttled++;
                case AUTH -> auth++;
                case CLIENT -> client++;
                case NONE -> { }
            }
        }
        int failures() { return server + timeouts; }
        int ok() { return total - server - timeouts - throttled - auth - client; }
    }
}
