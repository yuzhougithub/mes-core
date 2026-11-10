package com.xihua.mes.auth;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.xihua.mes.auth.infra.SysConfigMapper;

/** Reads password / lockout / session limits from {@code sys_config}, then clamps them. */
@Service
public class SecurityPolicyService {

    private final SysConfigMapper configs;
    private final Clock clock;

    public SecurityPolicyService(SysConfigMapper configs, Clock clock) {
        this.configs = configs;
        this.clock = clock;
    }

    public SecurityPolicy current() {
        return new SecurityPolicy(
                parse(configs.findValue("security.password.min-length"), 8),
                parse(configs.findValue("security.password.min-classes"), 3),
                parse(configs.findValue("security.password.max-age-days"), 0),
                parse(configs.findValue("security.lockout.max-failures"), 5),
                parse(configs.findValue("security.lockout.minutes"), 15),
                parse(configs.findValue("security.session.access-minutes"), 30),
                parse(configs.findValue("security.session.refresh-days"), 7));
    }

    public boolean passwordExpired(OffsetDateTime changedAt) {
        SecurityPolicy policy = current();
        if (policy.maxAgeDays() <= 0) {
            return false;
        }
        if (changedAt == null) {
            return true;
        }
        return !changedAt.toInstant().plus(policy.maxAgeDays(), ChronoUnit.DAYS).isAfter(clock.instant());
    }

    private static int parse(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
