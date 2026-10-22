package com.brightline.monitor.domain;

public enum ErrorClass {
    NONE, SERVER, TIMEOUT, THROTTLED, AUTH, CLIENT;

    /** Derive the error class from a raw log entry. status_code is null for timeouts. */
    public static ErrorClass classify(String status, Integer statusCode) {
        if ("success".equalsIgnoreCase(status)) return NONE;
        if ("timeout".equalsIgnoreCase(status)) return TIMEOUT;
        if (statusCode == null) return SERVER;          // unknown failure: never hide it
        if (statusCode == 429) return THROTTLED;
        if (statusCode == 401 || statusCode == 403) return AUTH;
        return statusCode >= 500 ? SERVER : CLIENT;
    }
}
