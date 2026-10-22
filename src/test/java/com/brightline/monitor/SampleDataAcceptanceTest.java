package com.brightline.monitor;

import static com.brightline.monitor.domain.HealthStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.context.ActiveProfiles;

/** Seeds the provided sample logs (clock anchored at 09:55) and asserts the expected triage. */
@SpringBootTest
@ActiveProfiles("test")
class SampleDataAcceptanceTest {
    @Autowired HealthEvaluationService service;
    @Autowired HealthSnapshotRepository snapshots;
    @Autowired IntegrationRepository integrations;
    @Autowired RequestLogRepository logs;

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
        Integration dormant = integrations.save(new Integration("Dormant Sync", 5));
        logs.save(new RequestLog(dormant, LocalDateTime.parse("2026-08-04T08:00:00"), "success", 200, 100));

        Integration expired = integrations.save(new Integration("Expired Token API", 5));
        List<RequestLog> auth = new ArrayList<>();
        for (int m = 30; m <= 55; m += 5) {
            auth.add(new RequestLog(expired, LocalDateTime.parse("2026-08-04T09:00:00").plusMinutes(m),
                    "error", 401, 90));
        }
        logs.saveAll(auth);

        service.evaluateAll();
        assertThat(statusOf("Dormant Sync")).isEqualTo(RED);
        assertThat(reasonOf("Dormant Sync")).contains("No requests for");
        assertThat(statusOf("Expired Token API")).isEqualTo(RED);
        assertThat(reasonOf("Expired Token API")).contains("auth failure");
    }

    private HealthStatus statusOf(String name) { return snapshot(name).getStatus(); }
    private String reasonOf(String name) { return snapshot(name).getReason(); }

    private HealthSnapshot snapshot(String name) {
        Integration i = integrations.findByName(name).orElseThrow();
        return snapshots.findById(i.getId()).orElseThrow();
    }
}
