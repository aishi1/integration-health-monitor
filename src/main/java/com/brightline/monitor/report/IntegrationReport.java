package com.brightline.monitor.report;

import com.brightline.monitor.domain.HealthStatus;
import java.util.List;

public record IntegrationReport(
        Long id,
        String name,
        HealthStatus status,
        String reason,
        int total,
        double successPct,
        double errorPct,        // 5xx + timeouts
        double throttlePct,     // 429
        int authErrors,
        Double p50,
        Double p95,
        double coveragePct,     // received / expected requests
        int redCells,
        int amberCells,
        int greenCells,
        int noDataCells,
        List<BucketCell> cells) {

    public String css() { return status.name().toLowerCase(); }
}
