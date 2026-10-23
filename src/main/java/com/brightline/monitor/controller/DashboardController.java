package com.brightline.monitor.controller;

import com.brightline.monitor.config.HealthClock;
import com.brightline.monitor.config.HealthProperties;
import com.brightline.monitor.domain.ErrorClass;
import com.brightline.monitor.domain.ErrorClassCount;
import com.brightline.monitor.domain.HealthSnapshot;
import com.brightline.monitor.domain.HealthStatus;
import com.brightline.monitor.domain.Integration;
import com.brightline.monitor.domain.RequestLog;
import com.brightline.monitor.report.ReportCsv;
import com.brightline.monitor.repository.HealthSnapshotRepository;
import com.brightline.monitor.repository.IntegrationRepository;
import com.brightline.monitor.repository.RequestLogRepository;
import com.brightline.monitor.report.ReportPeriod;
import com.brightline.monitor.report.ReportService;
import com.brightline.monitor.service.HealthEvaluationService;
import com.brightline.monitor.service.IncidentMetrics;
import com.brightline.monitor.service.IncidentSummaryService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class DashboardController {
    private final HealthSnapshotRepository snapshots;
    private final IntegrationRepository integrations;
    private final RequestLogRepository logs;
    private final HealthProperties props;
    private final HealthClock clock;
    private final HealthEvaluationService evaluation;
    private final ReportService reports;
    private final IncidentSummaryService incidentSummaries;

    public DashboardController(HealthSnapshotRepository snapshots, IntegrationRepository integrations,
                               RequestLogRepository logs, HealthProperties props, HealthClock clock,
                               HealthEvaluationService evaluation, ReportService reports,
                               IncidentSummaryService incidentSummaries) {
        this.snapshots = snapshots;
        this.integrations = integrations;
        this.logs = logs;
        this.props = props;
        this.clock = clock;
        this.evaluation = evaluation;
        this.reports = reports;
        this.incidentSummaries = incidentSummaries;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        evaluation.evaluateIfStale(Duration.ZERO);
        populate(model);
        return "dashboard";
    }

    /** Re-fetched by the browser every refresh interval; avoids a full page reload. */
    @GetMapping("/dashboard/fragment")
    public String fragment(@RequestParam(required = false) Integer maxAge, Model model) {
        // maxAge (seconds) = the viewer's refresh interval: re-evaluate if the data is older than that.
        // 0 forces an evaluation (manual "Refresh now"); absent = just read the latest snapshots.
        if (maxAge != null) {
            evaluation.evaluateIfStale(Duration.ofSeconds(Math.max(0, maxAge)));
        }
        populate(model);
        return "fragments/lanes :: lanes";
    }

    /** Daily / weekly / monthly report fragment. period = DAILY | WEEKLY | MONTHLY; force bypasses the 30 s cache. */
    @GetMapping("/dashboard/report")
    public String report(@RequestParam(defaultValue = "DAILY") String period,
                         @RequestParam(defaultValue = "false") boolean force, Model model) {
        model.addAttribute("report", reports.get(ReportPeriod.from(period), force));
        return "fragments/report :: report";
    }

    @GetMapping("/api/health")
    @ResponseBody
    public List<HealthSnapshot> api() {
        return snapshots.findAll();
    }

    @GetMapping("/integrations/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Integration integration = integrations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDateTime now = clock.now();
        List<RequestLog> all = logs.findByIntegrationIdAndRequestedAtBetweenOrderByRequestedAtAsc(
                id, now.minusHours(24), now);

        Map<ErrorClass, Long> failures = all.stream().filter(l -> !l.isSuccess())
                .collect(Collectors.groupingBy(RequestLog::getErrorClass,
                        () -> new EnumMap<>(ErrorClass.class), Collectors.counting()));
        List<RequestLog> recent = all.subList(Math.max(0, all.size() - 24), all.size());
        List<RequestLog> table = new ArrayList<>(all);
        Collections.reverse(table);

        model.addAttribute("integration", integration);
        model.addAttribute("snapshot", snapshots.findById(id).orElse(null));
        model.addAttribute("failures", failures);
        model.addAttribute("recent", recent);
        model.addAttribute("rows", table.size() > 100 ? table.subList(0, 100) : table);
        return "integration";
    }

    private void populate(Model model) {
        List<HealthSnapshot> all = snapshots.findAll();
        LocalDateTime metricsTo = clock.now();
        Map<Long, Map<ErrorClass, Long>> counts = new HashMap<>();
        for (ErrorClassCount row : logs.countByIntegrationAndErrorClass(
                metricsTo.minusMinutes(props.windowMinutes()), metricsTo)) {
            counts.computeIfAbsent(row.integrationId(), ignored -> new EnumMap<>(ErrorClass.class))
                    .put(row.errorClass(), row.count());
        }
        Map<Long, String> summaries = new HashMap<>();
        for (HealthSnapshot snapshot : all) {
            Map<ErrorClass, Long> byClass = counts.getOrDefault(snapshot.getIntegrationId(), Map.of());
            long successes = byClass.getOrDefault(ErrorClass.NONE, 0L);
            IncidentMetrics metrics = new IncidentMetrics(snapshot.getIntegrationName(), snapshot.getStatus(),
                    props.windowMinutes(), sum(byClass), successes,
                    byClass.getOrDefault(ErrorClass.SERVER, 0L),
                    byClass.getOrDefault(ErrorClass.TIMEOUT, 0L),
                    byClass.getOrDefault(ErrorClass.THROTTLED, 0L),
                    byClass.getOrDefault(ErrorClass.AUTH, 0L),
                    byClass.getOrDefault(ErrorClass.CLIENT, 0L));
            summaries.put(snapshot.getIntegrationId(), incidentSummaries.summarize(metrics));
        }
        Comparator<HealthSnapshot> order = Comparator.comparingDouble(HealthSnapshot::getErrorRate).reversed()
                .thenComparing(HealthSnapshot::getIntegrationName);
        for (HealthStatus st : HealthStatus.values()) {
            model.addAttribute(st.name().toLowerCase(),
                    all.stream().filter(s -> s.getStatus() == st).sorted(order).toList());
        }
        LocalDateTime lastEvaluated = all.stream().map(HealthSnapshot::getEvaluatedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        LocalDateTime asOf = all.stream().map(HealthSnapshot::getAsOf)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        boolean stale = lastEvaluated == null
                || lastEvaluated.isBefore(LocalDateTime.now().minusSeconds(3L * props.refreshSeconds()));
        model.addAttribute("lastEvaluated", lastEvaluated);
        model.addAttribute("asOf", asOf);
        model.addAttribute("summaries", summaries);
        model.addAttribute("stale", stale);
        model.addAttribute("refreshSeconds", props.refreshSeconds());
    }

    private long sum(Map<ErrorClass, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    @GetMapping("/report/download")
    public ResponseEntity<byte[]> download(@RequestParam(defaultValue = "DAILY") String period) {
        String csv;
        String name;
        if ("LIVE".equalsIgnoreCase(period)) {
            List<HealthSnapshot> all = new ArrayList<>(snapshots.findAll());
            all.sort(Comparator.comparing(HealthSnapshot::getStatus).reversed()
                    .thenComparing(HealthSnapshot::getIntegrationName));
            csv = ReportCsv.renderLive(all);
            name = "integration-health-live-" + clock.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv";
        } else {
            var view = reports.get(ReportPeriod.from(period), true);   // force = skip the 30 s cache
            csv = ReportCsv.render(view);
            name = "integration-report-" + view.period().name().toLowerCase() + "-"
                    + view.to().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

}
