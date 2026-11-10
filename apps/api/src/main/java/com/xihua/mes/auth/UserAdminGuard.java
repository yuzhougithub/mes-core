package com.xihua.mes.auth;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.xihua.mes.auth.domain.SysUser;
import com.xihua.mes.auth.infra.SysUserMapper;

/**
 * Cross-plant and self-grant limits apply to everyone except a super admin.
 * Header mode stays unrestricted, matching the dev contract that it acts as full access.
 * Function permissions themselves are {@link RequiresPerm}, not this guard.
 */
@Component
public class UserAdminGuard {

    private final String mode;
    private final SysUserMapper users;

    public UserAdminGuard(
            @Value("${mes.auth.mode:jwt}") String mode,
            SysUserMapper users) {
        this.mode = mode == null ? PlantContextFilter.MODE_JWT : mode.trim();
        this.users = users;
    }

    public boolean superAdmin() {
        return headerMode() || isSuperAdmin();
    }

    private boolean headerMode() {
        return PlantContextFilter.MODE_HEADER.equalsIgnoreCase(mode);
    }

    private boolean isSuperAdmin() {
        UUID userId = CurrentContext.userUuid();
        if (userId == null) {
            return false;
        }
        SysUser user = users.selectById(userId);
        return user != null && Boolean.TRUE.equals(user.getSuperAdmin());
    }
}
