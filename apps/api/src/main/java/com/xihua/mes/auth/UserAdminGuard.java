package com.xihua.mes.auth;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.xihua.mes.auth.domain.SysUser;
import com.xihua.mes.auth.infra.SysUserMapper;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.org.infra.PersonPlantMapper;

/**
 * F1 stand-in for {@code sys:user:*} until F2 {@code @RequiresPerm}. Super admin, or a caller who
 * holds the {@code ADMIN} role at the current plant, may administer users. Header mode is treated
 * as {@code ADMIN} (existing dev contract).
 */
@Component
public class UserAdminGuard {

    private final String mode;
    private final SysUserMapper users;
    private final PersonPlantMapper assignments;

    public UserAdminGuard(
            @Value("${mes.auth.mode:jwt}") String mode,
            SysUserMapper users,
            PersonPlantMapper assignments) {
        this.mode = mode == null ? PlantContextFilter.MODE_JWT : mode.trim();
        this.users = users;
        this.assignments = assignments;
    }

    public void check(String permCode) {
        if (headerMode() || isSuperAdmin() || hasAdminRole()) {
            return;
        }
        throw ApiException.permissionDenied(permCode);
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

    private boolean hasAdminRole() {
        UUID personId = CurrentContext.personUuid();
        UUID plantId = CurrentContext.requirePlantUuid();
        if (personId == null) {
            return false;
        }
        return assignments.roleCodes(personId, plantId).stream().anyMatch("ADMIN"::equals);
    }
}
