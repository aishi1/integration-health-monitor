package com.brightline.monitor.report;

import com.brightline.monitor.domain.HealthStatus;

/** One cell of the heat strip. status == null means no data in that bucket. */
public record BucketCell(HealthStatus status, String detail) {
    public String css() { return status == null ? "nodata" : status.name().toLowerCase(); }
}
