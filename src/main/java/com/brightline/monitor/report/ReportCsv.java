package com.brightline.monitor.report;

import com.brightline.monitor.domain.HealthSnapshot;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class ReportCsv {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private ReportCsv() {}

    /** One row per integration for the selected daily / weekly / monthly period. */
    public static String render(ReportView v) {
        StringBuilder sb = new StringBuilder();
        row(sb, "Period", "From", "To", "Integration", "Status", "Reason", "Requests", "Success %",
                "Failure % (5xx+timeouts)", "Throttled % (429)", "Auth errors", "p50 latency ms", "p95 latency ms",
                "Data coverage %", "Red cells", "Amber cells", "Green cells", "No-data cells");
        for (IntegrationReport r : v.rows()) {
            row(sb, v.period().label(), fmt(v.from()), fmt(v.to()), r.name(), r.status().name(), r.reason(),
                    String.valueOf(r.total()), pct(r.successPct()), pct(r.errorPct()), pct(r.throttlePct()),
                    String.valueOf(r.authErrors()), num(r.p50()), num(r.p95()), pct(r.coveragePct()),
                    String.valueOf(r.redCells()), String.valueOf(r.amberCells()),
                    String.valueOf(r.greenCells()), String.valueOf(r.noDataCells()));
        }
        return sb.toString();
    }

    /** Current live snapshot. */
    public static String renderLive(List<HealthSnapshot> snapshots) {
        StringBuilder sb = new StringBuilder();
        row(sb, "Integration", "Status", "Reason", "Requests in window", "Failure % (5xx+timeouts)",
                "Median latency ms", "Consecutive failures", "Minutes since last request", "Data as of", "Evaluated at");
        for (HealthSnapshot s : snapshots) {
            row(sb, s.getIntegrationName(), s.getStatus().name(), s.getReason(), String.valueOf(s.getTotalRequests()),
                    pct(s.getErrorRate()), num(s.getMedianLatencyMs()), String.valueOf(s.getConsecutiveFailures()),
                    s.getMinutesSinceLastRequest() == null ? "" : String.valueOf(s.getMinutesSinceLastRequest()),
                    fmt(s.getAsOf()), fmt(s.getEvaluatedAt()));
        }
        return sb.toString();
    }

    /** Quotes when needed and neutralises spreadsheet formulas (=, +, -, @ at the start). */
    public static String escape(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) { if (i > 0) sb.append(','); sb.append(escape(cells[i])); }
        sb.append("\r\n");
    }
    private static String pct(double f) { return String.format(Locale.ROOT, "%.1f", f * 100); }
    private static String num(Double d) { return d == null ? "" : String.format(Locale.ROOT, "%.0f", d); }
    private static String fmt(LocalDateTime t) { return t == null ? "" : TS.format(t); }
}