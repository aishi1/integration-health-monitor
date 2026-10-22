package com.brightline.monitor.rules;

import static com.brightline.monitor.domain.HealthStatus.*;

import com.brightline.monitor.config.HealthProperties;
import com.brightline.monitor.domain.Integration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** An integration that stopped sending can look 100% healthy on error rate alone. */
@Component
@Order(6)
public class SilenceRule implements HealthRule {
    private final HealthProperties p;

    public SilenceRule(HealthProperties p) { this.p = p; }

    @Override
    public RuleResult evaluate(Integration i, WindowStats s) {
        if (s.minutesSinceLastRequest() == null) return new RuleResult(RED, "No requests ever recorded");
        long mins = s.minutesSinceLastRequest();
        int cadence = i.getExpectedCadenceMin();
        String text = "No requests for " + mins + " min (expected every " + cadence + ")";
        if (mins > p.silence().redCadences() * cadence) return new RuleResult(RED, text);
        if (mins > p.silence().amberCadences() * cadence) return new RuleResult(AMBER, text);
        return RuleResult.ok();
    }
}
