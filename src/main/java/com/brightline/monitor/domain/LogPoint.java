package com.brightline.monitor.domain;

import java.time.LocalDateTime;

/** Lightweight projection of a request log row, used for period reports. */
public record LogPoint(Long integrationId, LocalDateTime requestedAt, ErrorClass errorClass, Integer latencyMs) {}
