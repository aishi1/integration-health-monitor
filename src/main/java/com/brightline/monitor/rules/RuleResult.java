package com.brightline.monitor.rules;

import com.brightline.monitor.domain.HealthStatus;

public record RuleResult(HealthStatus status, String reason) {
    public static RuleResult ok() { return new RuleResult(HealthStatus.GREEN, ""); }
    public static RuleResult ok(String reason) { return new RuleResult(HealthStatus.GREEN, reason); }
}
