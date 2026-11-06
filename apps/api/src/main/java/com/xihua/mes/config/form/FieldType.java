package com.xihua.mes.config.form;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Closed set of dynamic-form field types (architecture «动态表单»).
 * JSON wire names are lowercase; no Formily widget names.
 */
public enum FieldType {
    STRING("string"),
    NUMBER("number"),
    DATETIME("datetime"),
    ENUM("enum"),
    MULTI_ENUM("multi_enum"),
    BOOLEAN("boolean"),
    IMAGE("image"),
    FILE("file");

    private final String jsonName;

    FieldType(String jsonName) {
        this.jsonName = jsonName;
    }

    @JsonValue
    public String jsonName() {
        return jsonName;
    }

    /**
     * Resolve a wire type for validation paths. Unknown values become
     * {@link DynamicFormSchemaException} ({@code SCHEMA_INVALID_TYPE}), not a bare IAE.
     */
    public static FieldType from(String value) {
        if (value == null || value.isBlank()) {
            throw new DynamicFormSchemaException(
                    DynamicFormSchemaException.CODE_INVALID_TYPE,
                    "Field type must be a non-blank string");
        }
        for (FieldType type : values()) {
            if (type.jsonName.equals(value)) {
                return type;
            }
        }
        throw new DynamicFormSchemaException(
                DynamicFormSchemaException.CODE_INVALID_TYPE,
                "Unknown field type: " + value);
    }

    @JsonCreator
    public static FieldType fromJson(String value) {
        try {
            return from(value);
        } catch (DynamicFormSchemaException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    public boolean requiresOptions() {
        return this == ENUM || this == MULTI_ENUM;
    }
}
