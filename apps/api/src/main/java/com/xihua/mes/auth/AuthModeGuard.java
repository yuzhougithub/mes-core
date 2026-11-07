package com.xihua.mes.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * A2: {@code mes.auth.mode=header} trusts a client-supplied {@code X-Plant-Id} and may run
 * only when every active profile is {@code dev} or {@code test}. Staging, production, and a
 * process with no profile must not start in header mode.
 * <p>
 * The default mode is {@code jwt}. JWT is not implemented yet: {@link PlantContextFilter} does
 * not read {@code X-Plant-Id}, so calls that need a plant fail closed with {@code PLANT_REQUIRED}.
 */
@Component
public class AuthModeGuard {

    static final String HEADER_NOT_ALLOWED =
            "mes.auth.mode=header is only allowed when the active profiles are dev or test (A2)";

    private final Environment environment;
    private final String mode;

    public AuthModeGuard(Environment environment, @Value("${mes.auth.mode:jwt}") String mode) {
        this.environment = environment;
        this.mode = mode == null || mode.isBlank() ? PlantContextFilter.MODE_JWT : mode.trim();
    }

    @PostConstruct
    void check() {
        if (!PlantContextFilter.MODE_HEADER.equalsIgnoreCase(mode)) {
            return;
        }
        String[] active = environment.getActiveProfiles();
        if (!headerProfilesAllowed(active)) {
            String shown = active.length == 0 ? "(none)" : String.join(",", active);
            throw new IllegalStateException(HEADER_NOT_ALLOWED + "; active profiles: " + shown
                    + ". Header mode trusts X-Plant-Id and must not run in staging, production, or with no profile.");
        }
    }

    /** @return trimmed {@code mes.auth.mode} this process started with */
    String mode() {
        return mode;
    }

    static boolean headerProfilesAllowed(String[] active) {
        if (active == null || active.length == 0) {
            return false;
        }
        for (String profile : active) {
            if (!"dev".equals(profile) && !"test".equals(profile)) {
                return false;
            }
        }
        return true;
    }
}
