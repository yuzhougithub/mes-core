package com.xihua.mes.config.form;

/**
 * Invalid dynamic-form schema (unknown key, illegal type, pattern, etc.).
 * Carries a stable {@code code} for HTTP 422 bodies.
 */
public class DynamicFormSchemaException extends RuntimeException {

    public static final String CODE_INVALID = "SCHEMA_INVALID";
    public static final String CODE_UNKNOWN_KEY = "SCHEMA_UNKNOWN_KEY";
    public static final String CODE_TOO_MANY_FIELDS = "SCHEMA_TOO_MANY_FIELDS";
    public static final String CODE_UNSUPPORTED_VERSION = "SCHEMA_UNSUPPORTED_VERSION";
    public static final String CODE_INVALID_TYPE = "SCHEMA_INVALID_TYPE";
    public static final String CODE_PATTERN_TOO_LONG = "PATTERN_TOO_LONG";
    public static final String CODE_PATTERN_INVALID = "PATTERN_INVALID";

    private final String code;

    public DynamicFormSchemaException(String message) {
        this(CODE_INVALID, message);
    }

    public DynamicFormSchemaException(String code, String message) {
        super(message);
        this.code = code == null || code.isBlank() ? CODE_INVALID : code;
    }

    public String getCode() {
        return code;
    }
}
