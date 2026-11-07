package com.xihua.mes.material.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.xihua.mes.material.domain.Uom;

public final class UomDtos {

    private UomDtos() {
    }

    public record UomCreate(String code, String name, Integer decimals, Boolean enabled) {
    }

    public record UomUpdate(String name, Integer decimals, Boolean enabled, Integer lockVersion) {
    }

    public record UomView(UUID id, String code, String name, int decimals, boolean enabled, int lockVersion,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        public static UomView of(Uom u) {
            return new UomView(u.getId(), u.getCode(), u.getName(), u.getDecimals(),
                    Boolean.TRUE.equals(u.getEnabled()), u.getLockVersion(), u.getCreatedAt(), u.getUpdatedAt());
        }
    }
}
