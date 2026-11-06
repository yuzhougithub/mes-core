package com.xihua.mes.common.error;

/** Stable top-level / item error codes from docs/v1-api.md «错误体». */
public final class ErrorCodes {

    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String PLANT_REQUIRED = "PLANT_REQUIRED";
    public static final String PLANT_FORBIDDEN = "PLANT_FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String DUPLICATE_CODE = "DUPLICATE_CODE";
    public static final String STALE_VERSION = "STALE_VERSION";
    public static final String STATE_CONFLICT = "STATE_CONFLICT";
    public static final String IN_USE = "IN_USE";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String CROSS_PLANT_REFERENCE = "CROSS_PLANT_REFERENCE";
    public static final String LAYOUT_INVALID = "LAYOUT_INVALID";
    public static final String LAYOUT_TOO_LARGE = "LAYOUT_TOO_LARGE";
    public static final String INTERNAL = "INTERNAL";

    // errors[].code
    public static final String REQUIRED = "REQUIRED";
    public static final String TOO_LONG = "TOO_LONG";
    public static final String OUT_OF_RANGE = "OUT_OF_RANGE";
    public static final String PATTERN_MISMATCH = "PATTERN_MISMATCH";
    public static final String DICT_VALUE_INVALID = "DICT_VALUE_INVALID";
    public static final String EXT_UNKNOWN_KEY = "EXT_UNKNOWN_KEY";
    public static final String EXT_TYPE_MISMATCH = "EXT_TYPE_MISMATCH";
    public static final String EXT_OPTION_INVALID = "EXT_OPTION_INVALID";
    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";
    public static final String INVALID_VALUE = "INVALID_VALUE";
    public static final String IMMUTABLE = "IMMUTABLE";
    public static final String DUPLICATE = "DUPLICATE";

    private ErrorCodes() {
    }
}
