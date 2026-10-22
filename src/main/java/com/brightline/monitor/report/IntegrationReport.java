package com.brightline.monitor.report;

import com.brightline.monitor.domain.HealthStatus;
import java.util.List;
import java.util.Locale;

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
        List<BucketCell> cells,
        List<Incident> incidents) {

    public String css() { return status.name().toLowerCase(); }

    public String trendPath() {
        if (cells.size() < 2) return "";
        StringBuilder path = new StringBuilder();
        boolean connected = false;
        for (int i = 0; i < cells.size(); i++) {
            BucketCell cell = cells.get(i);
            if (cell.total() == 0) {
                connected = false;
                continue;
            }
            double x = 8 + (double) i * 284 / (cells.size() - 1);
            double y = 96 - Math.min(1, cell.failureRate()) * 92;
            path.append(connected ? " L " : " M ")
                    .append(String.format(Locale.ROOT, "%.2f %.2f", x, y));
            connected = true;
        }
        return path.toString().trim();
    }

    public double trendX(int index) {
        return cells.size() < 2 ? 150 : 8 + (double) index * 284 / (cells.size() - 1);
    }

    public double trendY(BucketCell cell) {
        return 96 - Math.min(1, cell.failureRate()) * 92;
    }
}
