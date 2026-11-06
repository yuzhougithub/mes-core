package com.xihua.mes.config.form;

/**
 * Invalid dynamic-form schema (unknown key, illegal type, pattern, etc.).
 * Carries a stable {@code code} and optional JSON pointer for Problem Details {@code errors}.
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
    private final String pointer;

    public DynamicFormSchemaException(String message) {
        this(CODE_INVALID, message, "schema");
    }

    public DynamicFormSchemaException(String code, String message) {
        this(code, message, "schema");
    }

    public DynamicFormSchemaException(String code, String message, String pointer) {
        super(message);
        this.code = code == null || code.isBlank() ? CODE_INVALID : code;
        this.pointer = pointer == null || pointer.isBlank() ? "schema" : pointer;
    }

    public String getCode() {
        return code;
    }

    /** JSON-ish path for Problem Details {@code errors[].field}. */
    public String getPointer() {
        return pointer;
    }
}
