package com.brightline.monitor;

import static com.brightline.monitor.domain.HealthStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.brightline.monitor.report.IntegrationReport;
import com.brightline.monitor.report.ReportPeriod;
import com.brightline.monitor.report.ReportService;
import com.brightline.monitor.report.ReportView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Runs against the generated 30-day sample (see tools/generate_logs.py). */
@SpringBootTest
@ActiveProfiles("test")
class ReportServiceTest {
    @Autowired ReportService reports;

    @Test
    void bucketCountsMatchPeriod() {
        assertThat(row(ReportPeriod.DAILY, "Slack Notifications").cells()).hasSize(24);
        assertThat(row(ReportPeriod.WEEKLY, "Slack Notifications").cells()).hasSize(7);
        assertThat(row(ReportPeriod.MONTHLY, "Slack Notifications").cells()).hasSize(30);
    }

    @Test
    void degradingIntegrationsWorsenAsThePeriodShortens() {
        assertThat(row(ReportPeriod.MONTHLY, "ADP Payroll Sync").status()).isEqualTo(AMBER);
        assertThat(row(ReportPeriod.WEEKLY, "ADP Payroll Sync").status()).isEqualTo(RED);
        assertThat(row(ReportPeriod.DAILY, "ADP Payroll Sync").status()).isEqualTo(RED);
        assertThat(row(ReportPeriod.WEEKLY, "Greenhouse ATS Sync").status()).isEqualTo(RED);
    }

    @Test
    void healthyAndThrottledIntegrations() {
        assertThat(row(ReportPeriod.MONTHLY, "Slack Notifications").status()).isEqualTo(GREEN);
        assertThat(row(ReportPeriod.WEEKLY, "DocuSign Envelope API").status()).isEqualTo(AMBER);
    }

    @Test
    void monthlyReportShowsTheAuthIncidentAndTheDataGap() {
        IntegrationReport workday = row(ReportPeriod.MONTHLY, "Workday Directory Sync");
        assertThat(workday.authErrors()).isGreaterThan(0);
        assertThat(workday.amberCells()).isGreaterThanOrEqualTo(2);   // 10 Jul auth outage + 27 Jul gap
        assertThat(workday.coveragePct()).isLessThan(1.0);
    }

    private IntegrationReport row(ReportPeriod period, String name) {
        ReportView view = reports.get(period, true);
        return view.rows().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow();
    }
}
