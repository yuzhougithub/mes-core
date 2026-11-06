package com.xihua.mes.config.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.xihua.mes.config.domain.CustomFieldSchema;

import tools.jackson.databind.JsonNode;

public final class CustomFieldSchemaDtos {

    private CustomFieldSchemaDtos() {
    }

    public record SchemaPut(JsonNode schema, Integer lockVersion) {
    }

    public record SchemaView(String entityType, UUID plantId, JsonNode schema, int lockVersion,
            OffsetDateTime updatedAt) {
        public static SchemaView of(CustomFieldSchema row) {
            return new SchemaView(row.getEntityType(), row.getPlantId(), row.getSchema(), row.getLockVersion(),
                    row.getUpdatedAt());
        }
    }
}
