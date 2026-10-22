package com.brightline.monitor.report;

import java.time.Duration;

/** Reporting windows anchored at "now" (see HealthClock). Each is split into equal buckets ending at now. */
public enum ReportPeriod {
    DAILY("Last 24 hours", Duration.ofHours(24), Duration.ofHours(1), "1 hour"),
    WEEKLY("Last 7 days", Duration.ofDays(7), Duration.ofDays(1), "24 hours"),
    MONTHLY("Last 30 days", Duration.ofDays(30), Duration.ofDays(1), "24 hours");

    private final String label;
    private final Duration span;
    private final Duration unit;
    private final String unitLabel;

    ReportPeriod(String label, Duration span, Duration unit, String unitLabel) {
        this.label = label;
        this.span = span;
        this.unit = unit;
        this.unitLabel = unitLabel;
    }

    public String label() { return label; }
    public Duration span() { return span; }
    public Duration unit() { return unit; }
    public String unitLabel() { return unitLabel; }
    public int buckets() { return (int) (span.toSeconds() / unit.toSeconds()); }

    public static ReportPeriod from(String value) {
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (RuntimeException e) {
            return DAILY;
        }
    }
}
