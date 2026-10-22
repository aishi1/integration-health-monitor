package com.brightline.monitor.rules;

import static com.brightline.monitor.domain.HealthStatus.*;

import com.brightline.monitor.config.HealthProperties;
import com.brightline.monitor.domain.Integration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Catches hard outages quickly, even at low volume. Blind to alternating failures (error-rate covers that). */
@Component
@Order(2)
public class ConsecutiveFailureRule implements HealthRule {
    private final HealthProperties p;

    public ConsecutiveFailureRule(HealthProperties p) { this.p = p; }

    @Override
    public RuleResult evaluate(Integration i, WindowStats s) {
        int c = s.consecutiveFailures();
        String text = c + " consecutive failures";
        if (c >= p.consecutiveFailures().red()) return new RuleResult(RED, text);
        if (c >= p.consecutiveFailures().amber()) return new RuleResult(AMBER, text);
        return RuleResult.ok();
    }
}
