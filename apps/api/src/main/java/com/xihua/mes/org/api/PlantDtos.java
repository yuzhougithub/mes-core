package com.xihua.mes.org.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.xihua.mes.org.domain.Plant;

public final class PlantDtos {

    private PlantDtos() {
    }

    public record PlantCreate(String code, String name, String timezone, Boolean enabled, String remark) {
    }

    public record PlantUpdate(String name, String timezone, Boolean enabled, String remark, Integer lockVersion) {
    }

    public record PlantView(UUID id, String code, String name, String timezone, boolean enabled, String remark,
            int lockVersion, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        public static PlantView of(Plant p) {
            return new PlantView(p.getId(), p.getCode(), p.getName(), p.getTimezone(),
                    Boolean.TRUE.equals(p.getEnabled()), p.getRemark(), p.getLockVersion(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
