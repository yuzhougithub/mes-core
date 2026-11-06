package com.xihua.mes.common.error;

import java.util.ArrayList;
import java.util.List;

/** Small collector for field-level validation of request bodies. */
public final class Errors {

    private final List<FieldError> items = new ArrayList<>();

    public Errors add(String field, String code, String message) {
        items.add(new FieldError(field, code, message));
        return this;
    }

    public Errors addAll(List<FieldError> more) {
        items.addAll(more);
        return this;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<FieldError> items() {
        return List.copyOf(items);
    }

    /** Required non-blank string with max length (code points). Returns trimmed-or-original value. */
    public String text(String field, String value, boolean required, int maxLength) {
        if (value == null || value.isBlank()) {
            if (required) {
                add(field, ErrorCodes.REQUIRED, field + " 必填");
            }
            return value == null || value.isBlank() ? null : value;
        }
        if (value.codePointCount(0, value.length()) > maxLength) {
            add(field, ErrorCodes.TOO_LONG, field + " 长度不能超过 " + maxLength);
        }
        return value;
    }

    public <T> T required(String field, T value) {
        if (value == null) {
            add(field, ErrorCodes.REQUIRED, field + " 必填");
        }
        return value;
    }

    /** @throws ApiException 422 VALIDATION_FAILED when any error was collected */
    public void throwIfAny() {
        if (!items.isEmpty()) {
            throw ApiException.validation(items());
        }
    }
}
