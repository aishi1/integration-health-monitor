package com.brightline.monitor.rules;

import static com.brightline.monitor.domain.HealthStatus.*;

import com.brightline.monitor.config.HealthProperties;
import com.brightline.monitor.domain.Integration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** SERVER + TIMEOUT failures over total. 429 and auth errors are judged by their own rules. */
@Component
@Order(1)
public class ErrorRateRule implements HealthRule {
    private final HealthProperties p;

    public ErrorRateRule(HealthProperties p) { this.p = p; }

    @Override
    public RuleResult evaluate(Integration i, WindowStats s) {
        if (s.total() < p.minRequests()) {
            return RuleResult.ok(s.total() == 0 ? "No requests in window"
                    : "Only " + s.total() + " requests in window, too few for an error-rate verdict");
        }
        double rate = s.errorRate();
        // Hysteresis: once flagged, require the rate to fall below a lower bar to step down.
        double red = p.errorRate().red() * (s.previousStatus() == RED ? p.recoveryFactor() : 1);
        double amber = p.errorRate().amber()
                * (s.previousStatus() != null && s.previousStatus() != GREEN ? p.recoveryFactor() : 1);

        String text = Math.round(rate * 100) + "% failure rate (" + breakdown(s) + ") in last "
                + s.windowMinutes() + " min";
        if (rate >= red) return new RuleResult(RED, text);
        if (rate >= amber) return new RuleResult(AMBER, text);
        if (s.failures() > 0) {
            return RuleResult.ok(s.failures() + " failure(s) in " + s.total() + " requests, below threshold");
        }
        return RuleResult.ok("No failures in last " + s.windowMinutes() + " min");
    }

    private static String breakdown(WindowStats s) {
        StringBuilder sb = new StringBuilder();
        if (s.timeouts() > 0) sb.append(s.timeouts()).append(s.timeouts() == 1 ? " timeout" : " timeouts");
        if (s.serverErrors() > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.serverErrors()).append(s.serverErrors() == 1 ? " server error" : " server errors");
        }
        return sb.toString();
    }
}
