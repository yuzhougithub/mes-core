package com.xihua.mes.material.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.xihua.mes.common.web.Scope;
import com.xihua.mes.material.domain.Material;

import tools.jackson.databind.JsonNode;

public final class MaterialDtos {

    private MaterialDtos() {
    }

    public record MaterialCreate(String scope, String code, String name, String spec, String materialType,
            UUID categoryId, UUID uomId, String drawingNo, String remark) {
    }

    /** Full replacement of mutable header fields; {@code code} / {@code scope} are immutable (A16). */
    public record MaterialUpdate(String scope, String code, String name, String spec, String materialType,
            UUID categoryId, UUID uomId, String drawingNo, String remark, Integer lockVersion) {
    }

    public record LockRequest(Integer lockVersion) {
    }

    public record PlantExtPut(Boolean enabled, JsonNode values, Integer lockVersion) {
    }

    /** {@code GET /materials/{id}} embeds this; no row yet = {@code {enabled:true, values:{}, lockVersion:0}}. */
    public record PlantExtEmbedded(boolean enabled, JsonNode values, int lockVersion) {
    }

    public record PlantExtView(UUID materialId, UUID plantId, boolean enabled, JsonNode values, int lockVersion) {
    }

    public record MaterialView(UUID id, Scope scope, UUID plantId, String code, String name, String spec,
            String materialType, UUID categoryId, UUID uomId, String drawingNo, String status,
            OffsetDateTime releasedAt, String remark, int lockVersion, PlantExtEmbedded plantExt,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {

        public static MaterialView of(Material m, PlantExtEmbedded ext) {
            return new MaterialView(m.getId(), Scope.of(m.getPlantId()), m.getPlantId(), m.getCode(), m.getName(),
                    m.getSpec(), m.getMaterialType(), m.getCategoryId(), m.getUomId(), m.getDrawingNo(),
                    m.getStatus(), m.getReleasedAt(), m.getRemark(), m.getLockVersion(), ext, m.getCreatedAt(),
                    m.getUpdatedAt());
        }
    }
}
