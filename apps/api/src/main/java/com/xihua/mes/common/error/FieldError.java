package com.xihua.mes.common.error;

/**
 * One entry of Problem Details {@code errors[]}: {@code { field, code, message }}.
 * {@code field} is a JSON Pointer for config documents ({@code /fields/0/widget})
 * or a dot path for business bodies ({@code values.surface_finish}).
 */
public record FieldError(String field, String code, String message) {
}
