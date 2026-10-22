package com.brightline.monitor.rules;

import static com.brightline.monitor.domain.HealthStatus.*;

import com.brightline.monitor.config.HealthProperties;
import com.brightline.monitor.domain.Integration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 429s mean "slow down", not "broken": the integration works, our usage is the problem. */
@Component
@Order(3)
public class ThrottleRule implements HealthRule {
    private final HealthProperties p;

    public ThrottleRule(HealthProperties p) { this.p = p; }

    @Override
    public RuleResult evaluate(Integration i, WindowStats s) {
        if (s.total() < p.minRequests() || s.throttled() == 0) return RuleResult.ok();
        double rate = (double) s.throttled() / s.total();
        String text = Math.round(rate * 100) + "% of requests rate-limited (429)"
                + (s.failures() == 0 ? ", no server errors" : "");
        if (rate >= p.throttle().red()) return new RuleResult(RED, text);
        if (rate >= p.throttle().amber()) return new RuleResult(AMBER, text);
        return RuleResult.ok();
    }
}
