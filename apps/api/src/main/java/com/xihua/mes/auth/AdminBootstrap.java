package com.xihua.mes.auth;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.xihua.mes.auth.domain.SysUser;
import com.xihua.mes.auth.infra.SysUserMapper;
import com.xihua.mes.common.error.FieldError;

/**
 * Replaces the seed admin's unusable password hash from {@code MES_ADMIN_INITIAL_PASSWORD}
 * once. Later startups leave the account alone. Outside the {@code dev} profile a missing
 * password refuses startup while the account is still uninitialized.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    /** Stored by V3. BCrypt rejects it, so the account cannot log in until initialized. */
    static final String UNUSABLE_HASH = "!";
    static final String USERNAME = "admin";
    static final int MIN_LENGTH = 12;

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final SysUserMapper users;
    private final PasswordEncoder encoder;
    private final SecurityPolicyService policies;
    private final Environment environment;
    private final Clock clock;
    private final String initialPassword;

    public AdminBootstrap(
            SysUserMapper users,
            PasswordEncoder encoder,
            SecurityPolicyService policies,
            Environment environment,
            Clock clock,
            @Value("${mes.auth.admin.initial-password:}") String initialPassword) {
        this.users = users;
        this.encoder = encoder;
        this.policies = policies;
        this.environment = environment;
        this.clock = clock;
        this.initialPassword = initialPassword == null ? "" : initialPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        SysUser admin = users.findByUsername(USERNAME);
        if (admin == null || !UNUSABLE_HASH.equals(admin.getPasswordHash())) {
            return;
        }
        boolean dev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        if (initialPassword.isBlank()) {
            if (!dev) {
                throw new IllegalStateException(
                        "MES_ADMIN_INITIAL_PASSWORD is required to initialize the admin account");
            }
            log.warn("admin account is uninitialized and MES_ADMIN_INITIAL_PASSWORD is unset");
            return;
        }
        if (initialPassword.codePointCount(0, initialPassword.length()) < MIN_LENGTH) {
            throw new IllegalStateException("MES_ADMIN_INITIAL_PASSWORD must be at least 12 characters");
        }
        List<FieldError> errors = PasswordPolicy.violations(initialPassword, USERNAME, policies.current());
        if (!errors.isEmpty()) {
            throw new IllegalStateException("MES_ADMIN_INITIAL_PASSWORD does not satisfy the password policy: "
                    + errors.stream().map(FieldError::code).toList());
        }
        int updated = users.changePassword(admin.getId(), encoder.encode(initialPassword), true, OffsetDateTime.now(clock));
        if (updated != 1) {
            throw new IllegalStateException("failed to initialize the admin account");
        }
        log.info("initialized admin account; password change is required on first login");
    }
}
