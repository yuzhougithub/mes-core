package com.xihua.mes.system.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.xihua.mes.system.domain.OrgRole;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleCreate(
            String code,
            String name,
            String remark,
            String dataScope,
            Integer sortNo,
            Boolean enabled) {
    }

    public record RoleUpdate(
            String name,
            String remark,
            String dataScope,
            Integer sortNo,
            Boolean enabled,
            Integer lockVersion) {
    }

    public record RoleMenus(List<UUID> menuIds) {
    }

    public record RoleDataScope(String dataScope, List<UUID> deptIds) {
    }

    public record RoleView(
            UUID id,
            String code,
            String name,
            String remark,
            String dataScope,
            int sortNo,
            boolean enabled,
            boolean builtin,
            int lockVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static RoleView of(OrgRole row) {
            return new RoleView(
                    row.getId(),
                    row.getCode(),
                    row.getName(),
                    row.getRemark(),
                    row.getDataScope(),
                    row.getSortNo() == null ? 0 : row.getSortNo(),
                    Boolean.TRUE.equals(row.getEnabled()),
                    Boolean.TRUE.equals(row.getBuiltin()),
                    row.getLockVersion() == null ? 0 : row.getLockVersion(),
                    row.getCreatedAt(),
                    row.getUpdatedAt());
        }
    }
}
