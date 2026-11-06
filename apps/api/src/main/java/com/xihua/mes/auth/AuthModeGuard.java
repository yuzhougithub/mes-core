package com.xihua.mes.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/** A2: {@code mes.auth.mode=header} must never run under the {@code prod} profile. */
@Component
public class AuthModeGuard {

    private final Environment environment;
    private final String mode;

    public AuthModeGuard(Environment environment, @Value("${mes.auth.mode:header}") String mode) {
        this.environment = environment;
        this.mode = mode;
    }

    @PostConstruct
    void check() {
        if (PlantContextFilter.MODE_HEADER.equalsIgnoreCase(mode.trim())
                && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("mes.auth.mode=header is not allowed with the prod profile (A2)");
        }
    }
}
