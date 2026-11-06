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

    @JsonCreator
    public static FieldType fromJson(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Field type must not be null");
        }
        for (FieldType type : values()) {
            if (type.jsonName.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown field type: " + value);
    }

    public boolean requiresOptions() {
        return this == ENUM || this == MULTI_ENUM;
    }
}
