package com.xihua.mes.auth;

import java.util.UUID;

/** Bumps {@code sys_user.perm_version} so the next request rebuilds the permission snapshot. */
public interface PermVersions {

    void bumpRole(UUID roleId);

    void bumpMenu(UUID menuId);
}
