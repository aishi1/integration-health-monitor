package com.brightline.monitor.domain;

/** Declaration order matters: GREEN < AMBER < RED, used for worst-of rollup. */
public enum HealthStatus { GREEN, AMBER, RED }
