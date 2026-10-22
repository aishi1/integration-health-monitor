package com.brightline.monitor.rules;

import static com.brightline.monitor.domain.HealthStatus.*;

import com.brightline.monitor.config.HealthProperties;
import com.brightline.monitor.domain.Integration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 401/403 usually means an expired token or revoked permission: broken until a human fixes it. */
@Component
@Order(4)
public class AuthRule implements HealthRule {
    private final HealthProperties p;

    public AuthRule(HealthProperties p) { this.p = p; }

    @Override
    public RuleResult evaluate(Integration i, WindowStats s) {
        if (s.authErrors() == 0 || s.total() == 0) return RuleResult.ok();
        double rate = (double) s.authErrors() / s.total();
        String text = s.authErrors() + " auth failure(s) (401/403), credentials may be expired or revoked";
        return rate >= p.authErrors().red() ? new RuleResult(RED, text) : new RuleResult(AMBER, text);
    }
}
