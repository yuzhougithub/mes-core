package com.xihua.mes.config.app;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import com.xihua.mes.config.domain.CustomFieldEntityType;

/**
 * Public read/validate surface of the {@code config} package for other packages
 * (they never use config mappers directly).
 */
public interface ConfigQuery {

    /** @return true if {@code value} is an enabled dictionary item of {@code typeCode} visible in the current plant */
    boolean isDictValue(String typeCode, String value);

    /** Visible defect code (shared or current plant's), or empty. */
    java.util.Optional<com.xihua.mes.common.web.ScopedRef> findDefectCode(java.util.UUID id);

    /** Current plant's schema document for an entity (default {@code {schemaVersion:1, fields:[]}}). */
    JsonNode currentSchema(CustomFieldEntityType entityType);

    /** Validate + merge ext values against the current plant schema (422 VALIDATION_FAILED). */
    ObjectNode validateExtValues(CustomFieldEntityType entityType, JsonNode requested, JsonNode stored,
            String fieldPrefix);

    /** Read projection: only keys in the current schema. */
    ObjectNode projectExtValues(CustomFieldEntityType entityType, JsonNode stored);
}
