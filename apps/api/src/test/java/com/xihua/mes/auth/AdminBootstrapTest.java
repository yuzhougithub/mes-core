package com.xihua.mes.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.xihua.mes.auth.domain.SysUser;
import com.xihua.mes.auth.infra.SysUserMapper;

class AdminBootstrapTest {

    private final SysUserMapper users = mock(SysUserMapper.class);
    private final SecurityPolicyService policies = mock(SecurityPolicyService.class);
    private final Environment environment = mock(Environment.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void missingPasswordOutsideDevRefusesStartup() {
        uninitializedAdmin();
        when(environment.getActiveProfiles()).thenReturn(new String[] {"staging"});
        AdminBootstrap bootstrap = bootstrap("");
        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
        verify(users, never()).changePassword(any(), any(), anyBoolean(), any());
    }

    @Test
    void devWithoutPasswordLeavesTheAccountUnusable() {
        uninitializedAdmin();
        when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
        bootstrap("").run(null);
        verify(users, never()).changePassword(any(), any(), anyBoolean(), any());
    }

    @Test
    void appliesPasswordOnceAndForcesChange() {
        SysUser admin = uninitializedAdmin();
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        when(policies.current()).thenReturn(SecurityPolicy.DEFAULTS);
        when(users.changePassword(eq(admin.getId()), any(), eq(true), any(OffsetDateTime.class))).thenReturn(1);
        bootstrap("Bootstrap-Pass-1").run(null);
        verify(users).changePassword(eq(admin.getId()), any(), eq(true), any(OffsetDateTime.class));

        admin.setPasswordHash(encoder.encode("Bootstrap-Pass-1"));
        bootstrap("Bootstrap-Pass-1").run(null);
        verify(users).changePassword(eq(admin.getId()), any(), eq(true), any(OffsetDateTime.class));
    }

    private SysUser uninitializedAdmin() {
        SysUser admin = new SysUser();
        admin.setId(UUID.randomUUID());
        admin.setPasswordHash(AdminBootstrap.UNUSABLE_HASH);
        when(users.findByUsername(AdminBootstrap.USERNAME)).thenReturn(admin);
        return admin;
    }

    private AdminBootstrap bootstrap(String password) {
        return new AdminBootstrap(users, encoder, policies, environment, Clock.systemUTC(), password);
    }
}
