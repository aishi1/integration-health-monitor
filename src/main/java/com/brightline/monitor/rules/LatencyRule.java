package com.brightline.monitor.rules;

import static com.brightline.monitor.domain.HealthStatus.*;

import com.brightline.monitor.config.HealthProperties;
import com.brightline.monitor.domain.Integration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Compares the integration against itself (recent median vs window median), using successful
 * calls only so 30s timeouts do not distort the numbers.
 */
@Component
@Order(5)
public class LatencyRule implements HealthRule {
    private final HealthProperties p;

    public LatencyRule(HealthProperties p) { this.p = p; }

    @Override
    public RuleResult evaluate(Integration i, WindowStats s) {
        if (s.baselineLatencyMs() == null || s.recentLatencyMs() == null || s.baselineLatencyMs() <= 0) {
            return RuleResult.ok();
        }
        double ratio = s.recentLatencyMs() / s.baselineLatencyMs();
        String text = String.format("Latency %.1fx baseline (%.0f ms vs %.0f ms)",
                ratio, s.recentLatencyMs(), s.baselineLatencyMs());
        if (ratio >= p.latency().redRatio()) return new RuleResult(RED, text);
        if (ratio >= p.latency().amberRatio()) return new RuleResult(AMBER, text);
        return RuleResult.ok();
    }
}
