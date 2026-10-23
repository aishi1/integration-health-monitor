package com.brightline.monitor;

import static com.brightline.monitor.domain.HealthStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.brightline.monitor.controller.DashboardController;
import com.brightline.monitor.domain.HealthSnapshot;
import com.brightline.monitor.domain.HealthStatus;
import com.brightline.monitor.domain.Integration;
import com.brightline.monitor.domain.RequestLog;
import com.brightline.monitor.repository.HealthSnapshotRepository;
import com.brightline.monitor.repository.IntegrationRepository;
import com.brightline.monitor.repository.RequestLogRepository;
import com.brightline.monitor.service.HealthEvaluationService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.test.context.ActiveProfiles;

/** Seeds the provided sample logs (clock anchored at 09:55) and asserts the expected triage. */
@SpringBootTest
@ActiveProfiles("test")
class SampleDataAcceptanceTest {
    @Autowired HealthEvaluationService service;
    @Autowired HealthSnapshotRepository snapshots;
    @Autowired IntegrationRepository integrations;
    @Autowired RequestLogRepository logs;
    @Autowired DashboardController dashboard;

    @Test
    void classifiesProvidedSampleData() {
        service.evaluateAll();
        assertThat(statusOf("Slack Notifications")).isEqualTo(GREEN);
        assertThat(statusOf("Workday Directory Sync")).isEqualTo(GREEN);
        assertThat(statusOf("DocuSign Envelope API")).isEqualTo(AMBER);
        assertThat(statusOf("ADP Payroll Sync")).isEqualTo(RED);
        assertThat(statusOf("Greenhouse ATS Sync")).isEqualTo(RED);
        assertThat(reasonOf("DocuSign Envelope API")).contains("rate-limited");
        assertThat(reasonOf("Greenhouse ATS Sync")).contains("5 timeouts");
    }

    @Test
    void flagsSilentAndAuthFailingIntegrations() {
        LocalDateTime latest = logs.findMaxRequestedAt().orElseThrow();
        Integration dormant = integrations.save(new Integration("Dormant Sync", 5));
        logs.save(new RequestLog(dormant, latest.minusMinutes(115), "success", 200, 100));

        Integration expired = integrations.save(new Integration("Expired Token API", 5));
        List<RequestLog> auth = new ArrayList<>();
        for (int m = 25; m >= 0; m -= 5) {
            auth.add(new RequestLog(expired, latest.minusMinutes(m),
                    "error", 401, 90));
        }
        logs.saveAll(auth);

        service.evaluateAll();
        assertThat(statusOf("Dormant Sync")).isEqualTo(RED);
        assertThat(reasonOf("Dormant Sync")).contains("No requests for");
        assertThat(statusOf("Expired Token API")).isEqualTo(RED);
        assertThat(reasonOf("Expired Token API")).contains("auth failure");
    }

    @Test
    void recognizesRecentRequestsWhenEvaluatingSilence() {
        LocalDateTime latest = logs.findMaxRequestedAt().orElseThrow();
        Integration active = integrations.save(new Integration("Recent Requests Sync", 5));
        logs.save(new RequestLog(active, latest.minusMinutes(5), "success", 200, 100));

        service.evaluate(active, latest);

        HealthSnapshot snapshot = snapshots.findById(active.getId()).orElseThrow();
        assertThat(snapshot.getMinutesSinceLastRequest()).isEqualTo(5);
        assertThat(snapshot.getReason()).doesNotContain("No requests ever recorded");
    }

    @Test
    void firstDashboardLoadReevaluatesExistingSnapshots() {
        LocalDateTime latest = logs.findMaxRequestedAt().orElseThrow();
        Integration active = integrations.save(new Integration("Dashboard First Load Sync", 5));
        LocalDateTime requestedAt = latest.minusMinutes(5);
        logs.save(new RequestLog(active, requestedAt, "success", 200, 100));
        snapshots.save(new HealthSnapshot(active.getId(), active.getName(), RED,
                "No requests ever recorded", 0, null, 0, null, 0, latest, LocalDateTime.now()));

        dashboard.dashboard(new ExtendedModelMap());

        HealthSnapshot snapshot = snapshots.findById(active.getId()).orElseThrow();
        assertThat(snapshot.getMinutesSinceLastRequest()).isEqualTo(5);
        assertThat(snapshot.getReason()).doesNotContain("No requests ever recorded");
    }

    private HealthStatus statusOf(String name) { return snapshot(name).getStatus(); }
    private String reasonOf(String name) { return snapshot(name).getReason(); }

    private HealthSnapshot snapshot(String name) {
        Integration i = integrations.findByName(name).orElseThrow();
        return snapshots.findById(i.getId()).orElseThrow();
    }
}
