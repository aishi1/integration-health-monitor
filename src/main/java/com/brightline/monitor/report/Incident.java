package com.brightline.monitor.report;

import com.brightline.monitor.domain.HealthStatus;
import java.time.LocalDateTime;

/** Consecutive amber/red buckets for one integration. */
public record Incident(
        LocalDateTime start,
        long durationMinutes,
        String dominantErrorType,
        HealthStatus status,
        boolean active) {

    public String durationLabel() {
        long days = durationMinutes / (24 * 60);
        if (days > 0) return days + (days == 1 ? " day" : " days");
        long hours = durationMinutes / 60;
        if (hours > 0) return hours + (hours == 1 ? " hour" : " hours");
        return durationMinutes + (durationMinutes == 1 ? " minute" : " minutes");
    }
}
