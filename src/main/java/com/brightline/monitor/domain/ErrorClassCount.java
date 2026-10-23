package com.brightline.monitor.domain;

public record ErrorClassCount(Long integrationId, ErrorClass errorClass, long count) {
}
