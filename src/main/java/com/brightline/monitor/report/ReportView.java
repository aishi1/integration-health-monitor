package com.brightline.monitor.report;

import java.time.LocalDateTime;
import java.util.List;

public record ReportView(
        ReportPeriod period,
        LocalDateTime from,
        LocalDateTime to,
        String cellNote,
        int red,
        int amber,
        int green,
        List<IntegrationReport> rows,
        double amberThreshold,
        double redThreshold) {

    public double thresholdY(double threshold) {
        return 96 - Math.max(0, Math.min(1, threshold)) * 92;
    }
}
