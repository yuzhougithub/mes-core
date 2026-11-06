package com.xihua.mes.common.web;

import java.util.List;
import java.util.UUID;

import com.xihua.mes.auth.CurrentContext;
import com.xihua.mes.common.error.ApiException;
import com.xihua.mes.common.error.ErrorCodes;
import com.xihua.mes.common.error.FieldError;

/** {@code scope} of shared-type resources: {@code SHARED} (plant_id null) or {@code PLANT} (current plant). */
public enum Scope {
    SHARED,
    PLANT;

    public static Scope of(UUID plantId) {
        return plantId == null ? SHARED : PLANT;
    }

    /** Parse a create-request scope; required. */
    public static Scope parseRequired(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.validation(List.of(new FieldError("scope", ErrorCodes.REQUIRED, "scope 必填")));
        }
        return parse(raw, "scope");
    }

    public static Scope parse(String raw, String field) {
        try {
            return Scope.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation(List.of(new FieldError(field, ErrorCodes.INVALID_VALUE,
                    "scope 只能是 SHARED 或 PLANT")));
        }
    }

    /** plant_id to store for this scope. */
    public UUID plantIdForCreate() {
        return this == SHARED ? null : CurrentContext.requirePlantUuid();
    }
}
