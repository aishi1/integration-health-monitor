package com.brightline.monitor.rules;

import com.brightline.monitor.domain.Integration;

/** Strategy: each rule inspects the window stats and returns a status plus a human reason. */
public interface HealthRule {
    RuleResult evaluate(Integration integration, WindowStats stats);
}
