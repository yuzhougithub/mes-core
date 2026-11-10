package com.xihua.mes.system.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.xihua.mes.system.domain.SysMenu;

public final class MenuDtos {

    private MenuDtos() {
    }

    public record MenuWrite(
            UUID parentId,
            String type,
            String name,
            String permCode,
            String path,
            String component,
            String icon,
            Integer sortNo,
            Boolean visible,
            Boolean enabled,
            Integer lockVersion) {
    }

    public record MenuNode(
            UUID id,
            UUID parentId,
            String type,
            String name,
            String permCode,
            String path,
            String component,
            String icon,
            int sortNo,
            boolean visible,
            boolean enabled,
            int lockVersion,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<MenuNode> children) {

        public static MenuNode of(SysMenu row, List<MenuNode> children) {
            return new MenuNode(
                    row.getId(),
                    row.getParentId(),
                    row.getType(),
                    row.getName(),
                    row.getPermCode(),
                    row.getPath(),
                    row.getComponent(),
                    row.getIcon(),
                    row.getSortNo() == null ? 0 : row.getSortNo(),
                    Boolean.TRUE.equals(row.getVisible()),
                    Boolean.TRUE.equals(row.getEnabled()),
                    row.getLockVersion() == null ? 0 : row.getLockVersion(),
                    row.getCreatedAt(),
                    row.getUpdatedAt(),
                    children);
        }
    }
}
